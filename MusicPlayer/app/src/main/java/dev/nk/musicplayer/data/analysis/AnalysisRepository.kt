package dev.nk.musicplayer.data.analysis

import android.util.Log
import dev.nk.musicplayer.data.db.SongAnalysisDao
import dev.nk.musicplayer.data.db.TrackDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs [SongAnalyzer] over the library, one song at a time, and reports progress.
 *
 * Sequential on purpose. Each song costs a transcription call and a classification call
 * against a free-tier endpoint; firing ten at once earns a rate limit, not a speed-up. The
 * work is resumable — already-analysed songs are skipped — so a run that is stopped, rate
 * limited or killed with the app simply picks up where it left off next time.
 */
class AnalysisRepository(
    private val analysisDao: SongAnalysisDao,
    private val trackDao: TrackDao,
    private val analyzer: SongAnalyzer,
    private val scope: CoroutineScope
) {

    data class Progress(
        val running: Boolean = false,
        val done: Int = 0,
        val failed: Int = 0,
        val total: Int = 0,
        val currentTitle: String? = null,
        val message: String? = null
    ) {
        val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
    }

    /** Analysed vs. analysable, for the "X of Y songs understood" line. */
    data class Coverage(val analyzed: Int, val total: Int) {
        val complete: Boolean get() = total > 0 && analyzed >= total
        val fraction: Float get() = if (total <= 0) 0f else analyzed.toFloat() / total
    }

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private var job: Job? = null

    fun observeCoverage(): Flow<Coverage> =
        combine(analysisDao.observeAnalyzedCount(), trackDao.observeTrackCount()) { analyzed, total ->
            Coverage(analyzed = analyzed, total = total)
        }

    /**
     * Starts (or resumes) a run over songs that have no analysis yet. A second call while a
     * run is active is ignored rather than starting a competing one.
     */
    fun start(limit: Int = DEFAULT_BATCH) {
        if (job?.isActive == true) return
        job = scope.launch {
            val pending = analysisDao.tracksWithoutAnalysis(limit)
            if (pending.isEmpty()) {
                _progress.value = Progress(message = "Every song has already been analysed.")
                return@launch
            }

            _progress.value = Progress(running = true, total = pending.size)
            var done = 0
            var failed = 0
            var consecutiveFailures = 0
            var stopMessage: String? = null

            for (track in pending) {
                if (!isActive) break
                _progress.update { it.copy(currentTitle = "${track.artist} — ${track.title}") }

                val outcome = try {
                    analyzer.analyze(track)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "analysis threw for ${track.title}", e)
                    SongAnalyzer.Outcome.Failure(e.message ?: "Unexpected failure.")
                }

                when (outcome) {
                    is SongAnalyzer.Outcome.Success -> {
                        done++
                        consecutiveFailures = 0
                    }

                    is SongAnalyzer.Outcome.Failure -> {
                        failed++
                        consecutiveFailures++
                        Log.w(TAG, "analysis failed for ${track.title}: ${outcome.message}")
                        // A key, a quota or a dead connection fails every song the same way;
                        // three in a row means stop and show why rather than burn the library.
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            stopMessage = "Stopped after $consecutiveFailures failures in a row: " +
                                outcome.message
                            break
                        }
                    }
                }
                _progress.update { it.copy(done = done, failed = failed) }
            }

            _progress.update {
                it.copy(
                    running = false,
                    currentTitle = null,
                    done = done,
                    failed = failed,
                    message = stopMessage ?: summary(done, failed)
                )
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _progress.update { it.copy(running = false, currentTitle = null, message = "Stopped.") }
    }

    fun dismissMessage() = _progress.update { it.copy(message = null) }

    private fun summary(done: Int, failed: Int): String = when {
        done == 0 && failed == 0 -> "Nothing to analyse."
        failed == 0 -> "Analysed $done ${plural(done)}."
        else -> "Analysed $done ${plural(done)}, $failed failed."
    }

    private fun plural(count: Int) = if (count == 1) "song" else "songs"

    private companion object {
        const val TAG = "AnalysisRepository"

        /** One tap's worth of work: long enough to be useful, short enough to be stoppable. */
        const val DEFAULT_BATCH = 200

        const val MAX_CONSECUTIVE_FAILURES = 3
    }
}
