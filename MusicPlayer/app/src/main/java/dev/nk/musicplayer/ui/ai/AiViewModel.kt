package dev.nk.musicplayer.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.data.llm.AiPlaylistGenerator
import dev.nk.musicplayer.data.playlist.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Nothing here is written to Room until [save] is called. */
data class AiPreview(
    val name: String,
    val reasoning: String,
    val tracks: List<Track>,
    val prompt: String,
    val savedPlaylistId: Long? = null
) {
    val totalDurationMs: Long get() = tracks.sumOf { it.durationMs }
}

data class AiUiState(
    val prompt: String = "",
    /** null means "use whatever is typed in the custom field". */
    val presetMinutes: Int? = 60,
    val customSelected: Boolean = false,
    val customMinutes: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val preview: AiPreview? = null,
    val configured: Boolean = true
) {
    val targetMinutes: Int?
        get() = if (customSelected) customMinutes.toIntOrNull()?.takeIf { it > 0 } else presetMinutes

    val canGenerate: Boolean
        get() = prompt.isNotBlank() && !loading && configured
}

class AiViewModel(
    private val generator: AiPlaylistGenerator,
    private val playlists: PlaylistRepository,
    apiKey: Flow<String>
) : ViewModel() {

    private val _state = MutableStateFlow(AiUiState(configured = generator.isConfigured))
    val state = _state.asStateFlow()

    init {
        // The key is edited in Settings while this ViewModel is alive, so the banner and the
        // Generate button have to follow it rather than a value read once at construction.
        viewModelScope.launch {
            apiKey.collect { _state.update { s -> s.copy(configured = generator.isConfigured) } }
        }
    }

    fun setPrompt(value: String) = _state.update { it.copy(prompt = value) }

    fun selectPreset(minutes: Int) =
        _state.update { it.copy(presetMinutes = minutes, customSelected = false) }

    fun selectCustom() = _state.update { it.copy(customSelected = true) }

    fun setCustomMinutes(value: String) =
        _state.update { it.copy(customMinutes = value.filter { c -> c.isDigit() }.take(4)) }

    fun generate() {
        val current = _state.value
        if (!current.canGenerate) return

        _state.update { it.copy(loading = true, error = null, preview = null) }
        viewModelScope.launch {
            when (val result = generator.generate(current.prompt, current.targetMinutes)) {
                is AiPlaylistGenerator.Result.Success -> _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        preview = AiPreview(
                            name = result.name,
                            reasoning = result.reasoning,
                            tracks = result.tracks,
                            prompt = current.prompt
                        )
                    )
                }

                is AiPlaylistGenerator.Result.Failure -> _state.update {
                    it.copy(loading = false, error = result.message, preview = null)
                }
            }
        }
    }

    /** Writes the preview to Room. Only now does a playlist actually exist. */
    fun save() {
        val preview = _state.value.preview ?: return
        if (preview.savedPlaylistId != null) return
        viewModelScope.launch {
            val id = playlists.createGenerated(
                name = preview.name,
                prompt = preview.prompt,
                trackIds = preview.tracks.map { it.id }
            )
            _state.update { it.copy(preview = it.preview?.copy(savedPlaylistId = id)) }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    companion object {
        fun factory(
            generator: AiPlaylistGenerator,
            playlists: PlaylistRepository,
            apiKey: Flow<String>
        ) = viewModelFactory { initializer { AiViewModel(generator, playlists, apiKey) } }
    }
}
