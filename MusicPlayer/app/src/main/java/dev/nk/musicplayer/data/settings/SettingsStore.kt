package dev.nk.musicplayer.data.settings

import android.content.Context
import androidx.core.content.edit
import dev.nk.musicplayer.ui.nowplaying.SeekBarStyle
import dev.nk.musicplayer.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Appearance preferences. Same reasoning as QueueStore: a couple of values written when the
 * user taps a theme, so SharedPreferences is the right size of tool.
 *
 * The values are held in StateFlows as well as on disk because the theme has to recompose the
 * whole tree the instant it changes — reading prefs on every composition would not do that.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(readTheme())
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _glowEnabled = MutableStateFlow(prefs.getBoolean(KEY_GLOW, true))
    val glowEnabled: StateFlow<Boolean> = _glowEnabled.asStateFlow()

    private val _pulseWithPlayback = MutableStateFlow(prefs.getBoolean(KEY_PULSE, true))
    val pulseWithPlayback: StateFlow<Boolean> = _pulseWithPlayback.asStateFlow()

    private val _seekBarStyle = MutableStateFlow(readSeekBarStyle())
    val seekBarStyle: StateFlow<SeekBarStyle> = _seekBarStyle.asStateFlow()

    /**
     * Hugging Face access token for AI playlists. Entered by the user in Settings rather than
     * baked in at build time, so a plain install can turn the feature on without a rebuild.
     * Empty means "not configured" and the AI screen says so.
     */
    private val _hfApiKey = MutableStateFlow(prefs.getString(KEY_HF_KEY, null).orEmpty())
    val hfApiKey: StateFlow<String> = _hfApiKey.asStateFlow()

    /** Unknown or removed theme names fall back to the default rather than crashing. */
    private fun readTheme(): AppTheme {
        val stored = prefs.getString(KEY_THEME, null) ?: return AppTheme.NeonCyan
        return AppTheme.entries.firstOrNull { it.name == stored } ?: AppTheme.NeonCyan
    }

    private fun readSeekBarStyle(): SeekBarStyle {
        val stored = prefs.getString(KEY_SEEK, null) ?: return SeekBarStyle.NeonTube
        return SeekBarStyle.entries.firstOrNull { it.name == stored } ?: SeekBarStyle.NeonTube
    }

    fun setSeekBarStyle(style: SeekBarStyle) {
        _seekBarStyle.value = style
        prefs.edit { putString(KEY_SEEK, style.name) }
    }

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
        prefs.edit { putString(KEY_THEME, theme.name) }
    }

    fun setGlowEnabled(enabled: Boolean) {
        _glowEnabled.value = enabled
        prefs.edit { putBoolean(KEY_GLOW, enabled) }
    }

    fun setPulseWithPlayback(enabled: Boolean) {
        _pulseWithPlayback.value = enabled
        prefs.edit { putBoolean(KEY_PULSE, enabled) }
    }

    /** Stray whitespace from a paste would break the Authorization header, so trim it here. */
    fun setHfApiKey(key: String) {
        val cleaned = key.trim()
        _hfApiKey.value = cleaned
        prefs.edit { putString(KEY_HF_KEY, cleaned) }
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_GLOW = "glow_enabled"
        const val KEY_PULSE = "pulse_with_playback"
        const val KEY_SEEK = "seek_bar_style"
        const val KEY_HF_KEY = "hf_api_key"
    }
}
