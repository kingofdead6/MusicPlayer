package dev.nk.musicplayer.data.analysis

import android.util.Log
import dev.nk.musicplayer.data.db.SongAnalysis
import dev.nk.musicplayer.data.db.SongAnalysisDao
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.data.llm.ChatMessage
import dev.nk.musicplayer.data.llm.LlmClient

/**
 * Listens to one song and decides what it is.
 *
 * Three steps, each of which can degrade without taking the next one down:
 *  1. [AudioSampler] cuts a 16 kHz mono sample out of the file.
 *  2. [SpeechToTextClient] transcribes it, giving a rough, error-prone text of the vocals.
 *  3. The LLM reads that transcript alongside the file's tags and returns a classification —
 *     sentiment, mood, category, energy, themes.
 *
 * A song with no words (or a file the device cannot decode) still gets classified, from its
 * title, artist and album alone, and the row records which of the two it was so the playlist
 * prompt can weigh a lyric-backed verdict above a guess from a filename.
 *
 * The transcript itself is a means, not an output: a capped excerpt is cached so a later
 * re-analysis is free, the model is asked to describe rather than quote, and nothing in the
 * UI renders it.
 */
class SongAnalyzer(
    private val sampler: AudioSampler,
    private val stt: SpeechToTextClient,
    private val llm: LlmClient,
    private val dao: SongAnalysisDao,
    private val now: () -> Long = System::currentTimeMillis
) {

    sealed interface Outcome {
        data class Success(val analysis: SongAnalysis) : Outcome
        /** Something transient or configuration-level: the batch run should stop and say so. */
        data class Failure(val message: String) : Outcome
    }

    val isConfigured: Boolean get() = llm.isConfigured && stt.isConfigured

    suspend fun analyze(track: Track, force: Boolean = false): Outcome {
        val existing = dao.byTrack(track.id)
        if (!force && existing != null) return Outcome.Success(existing)

        // Re-analysing (a better prompt, a different model) reuses the cached transcript:
        // the classification is worth redoing, paying for transcription twice is not.
        val cached = existing?.transcript?.takeIf { force && it.length >= MIN_TRANSCRIPT_CHARS }
        val transcript = cached ?: when (val heard = listen(track)) {
            is Heard.Failed -> return Outcome.Failure(heard.message)
            is Heard.Text -> heard.text
        }

        val fromLyrics = transcript.length >= MIN_TRANSCRIPT_CHARS
        val excerpt = transcript.take(TRANSCRIPT_PROMPT_LIMIT)

        val messages = listOf(
            ChatMessage("system", SYSTEM_PROMPT),
            ChatMessage("user", userPrompt(track, if (fromLyrics) excerpt else ""))
        )

        val reply = llm.chat(messages).getOrElse { error ->
            return Outcome.Failure(error.message ?: "The classifier could not be reached.")
        }

        val verdict = when (val parsed = SongAnalysisParser.parse(reply)) {
            is AnalysisParseResult.Success -> parsed.verdict
            is AnalysisParseResult.Failure -> {
                Log.w(TAG, "analysis parse failed for ${track.title}: ${parsed.reason}")
                return Outcome.Failure("The classifier's reply could not be read (${parsed.reason}).")
            }
        }

        val analysis = SongAnalysis(
            trackId = track.id,
            analyzedAt = now(),
            source = if (fromLyrics) SongAnalysis.SOURCE_LYRICS else SongAnalysis.SOURCE_METADATA,
            language = if (fromLyrics) verdict.language else "instrumental",
            sentiment = verdict.sentiment,
            mood = verdict.mood,
            category = verdict.category,
            genre = verdict.genre,
            valence = verdict.valence,
            energy = verdict.energy,
            themes = verdict.themes.joinToString(", "),
            summary = verdict.summary,
            explicit = verdict.explicit,
            transcript = transcript.take(TRANSCRIPT_STORE_LIMIT)
        )
        dao.upsert(analysis)
        Log.i(TAG, "analysed ${track.artist} — ${track.title}: ${analysis.mood}/${analysis.category}")
        return Outcome.Success(analysis)
    }

    // ---- step 1 + 2 ------------------------------------------------------------------

    private sealed interface Heard {
        /** Empty text is a legitimate answer: the song has no intelligible words. */
        data class Text(val text: String) : Heard
        data class Failed(val message: String) : Heard
    }

    private suspend fun listen(track: Track): Heard {
        val wav = sampler.sample(track.uri, track.durationMs).getOrElse { error ->
            // An exotic codec or a file the decoder chokes on: classify from tags instead of
            // failing the whole run over one song.
            Log.i(TAG, "no audio sample for ${track.title}: ${error.message}")
            return Heard.Text("")
        }

        return stt.transcribe(wav).fold(
            onSuccess = { Heard.Text(it.trim()) },
            // A network or key problem is not this song's fault and will hit every other song
            // too, so it stops the run rather than filling rows with tag-only guesses.
            onFailure = { Heard.Failed(it.message ?: "Transcription failed.") }
        )
    }

    private fun userPrompt(track: Track, transcript: String): String = buildString {
        append("Title: ").append(track.title).append('\n')
        append("Artist: ").append(track.artist).append('\n')
        if (track.album.isNotBlank()) append("Album: ").append(track.album).append('\n')
        track.year?.let { append("Year: ").append(it).append('\n') }
        append("Duration: ").append(track.durationMs / 1000).append(" seconds\n\n")
        if (transcript.isBlank()) {
            append(
                "No words could be transcribed from the audio — treat it as instrumental or " +
                    "as vocals the recogniser could not make out, and classify it from the " +
                    "metadata and your knowledge of this artist and song."
            )
        } else {
            append("Automatic transcription of a few sampled sections (may contain errors):\n")
            append(transcript)
        }
    }

    companion object {
        private const val TAG = "SongAnalyzer"

        /** Below this the recogniser heard noise, not singing. */
        private const val MIN_TRANSCRIPT_CHARS = 40

        private const val TRANSCRIPT_PROMPT_LIMIT = 3_000
        private const val TRANSCRIPT_STORE_LIMIT = 1_500

        val SYSTEM_PROMPT = """
You classify songs for a personal music library.

You receive a song's metadata and, when the song has words, a rough automatic
transcription of a few sampled sections of its audio. The transcription is
machine-made: it mishears words, drops lines, and repeats itself. Read it for
subject matter and feeling, not as an accurate text, and lean on what you know
about the artist and the song as well.

Describe the song in your own words. Do not quote or reproduce lines from it.

Respond with JSON only, no markdown fences, no commentary:
{"language": "<language of the vocals, or 'instrumental'>",
 "sentiment": "positive | negative | neutral | mixed",
 "mood": "<one word: happy, sad, angry, calm, romantic, melancholic, hopeful,
           energetic, dark, nostalgic, spiritual, playful, defiant, anxious>",
 "category": "<what it is for: party, workout, focus, chill, driving, sleep,
               heartbreak, love, motivation, protest, spiritual, celebration,
               reflection>",
 "genre": "<one or two words>",
 "valence": <0.0 bleak to 1.0 joyful>,
 "energy": <0.0 still to 1.0 frantic>,
 "themes": ["<up to five short subjects, e.g. loss, home, defiance>"],
 "summary": "<one sentence, your own words, on what the song is doing>",
 "explicit": <true if the words are explicit>}
        """.trimIndent()
    }
}
