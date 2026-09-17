package dev.nk.musicplayer.data.llm

import android.util.Log
import dev.nk.musicplayer.data.db.SongAnalysis
import dev.nk.musicplayer.data.db.SongAnalysisDao
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.data.library.LibraryRepository
import dev.nk.musicplayer.util.formatDuration

/**
 * Turns "make me a 3-hour traveling playlist, calm" into tracks I own.
 *
 * The model only ever sees a numbered list and only ever answers with numbers. Titles it
 * returns are ignored entirely — see [PlaylistResponseParser.sanitize].
 *
 * Every song that has been through
 * [dev.nk.musicplayer.data.analysis.SongAnalyzer] carries its verdict into the digest, so the
 * curator is choosing on what the songs are about — mood, sentiment, energy, subject matter —
 * instead of inferring everything from an artist name it may never have heard of. Songs that
 * have not been analysed still appear, just bare, and the prompt says which is which.
 */
class AiPlaylistGenerator(
    private val library: LibraryRepository,
    private val client: LlmClient,
    private val analysisDao: SongAnalysisDao
) {

    sealed interface Result {
        data class Success(
            val name: String,
            val reasoning: String,
            val tracks: List<Track>,
            /** What the app knows about the chosen songs, for the preview's mood chips. */
            val analyses: Map<Long, SongAnalysis> = emptyMap()
        ) : Result

        data class Failure(val message: String) : Result
    }

    val isConfigured: Boolean get() = client.isConfigured

    suspend fun generate(request: String, targetMinutes: Int?): Result {
        val tracks = library.allTracks()
        if (tracks.isEmpty()) {
            return Result.Failure("There is no music in the library yet. Scan first.")
        }

        val analyses = analysisDao.all().associateBy { it.trackId }
        Log.i(TAG, "${analyses.size} of ${tracks.size} tracks have an analysis")

        val digest = buildDigest(tracks, analyses)
        val indices = if (digest.length <= DIGEST_CHAR_LIMIT) {
            Log.i(TAG, "single-pass: ${tracks.size} tracks, ${digest.length} chars")
            selectSinglePass(request, targetMinutes, digest, tracks.indices)
        } else {
            Log.i(TAG, "chunked: ${tracks.size} tracks, ${digest.length} chars exceeds $DIGEST_CHAR_LIMIT")
            selectChunked(request, targetMinutes, tracks, analyses)
        }

        return when (indices) {
            is Selection.Failed -> Result.Failure(indices.message)
            is Selection.Picked -> {
                val chosen = indices.indices.map { tracks[it] }
                if (chosen.size < MIN_TRACKS) {
                    Result.Failure(
                        "Only ${chosen.size} of the model's picks matched a real track in your " +
                            "library. Try a broader request."
                    )
                } else {
                    Result.Success(
                        name = indices.name,
                        reasoning = indices.reasoning,
                        tracks = chosen,
                        analyses = analyses.filterKeys { id -> chosen.any { it.id == id } }
                    )
                }
            }
        }
    }

    // ---- passes ----------------------------------------------------------------------

    private sealed interface Selection {
        data class Picked(val name: String, val reasoning: String, val indices: List<Int>) : Selection
        data class Failed(val message: String) : Selection
    }

    private suspend fun selectSinglePass(
        request: String,
        targetMinutes: Int?,
        digest: String,
        validRange: IntRange
    ): Selection {
        val messages = listOf(
            ChatMessage("system", SYSTEM_PROMPT),
            ChatMessage("user", userPrompt(request, targetMinutes, digest))
        )
        return callAndParse(messages, validRange)
    }

    /**
     * Libraries too big for one request are asked chunk by chunk for candidates, and the union
     * of those candidates then goes through the normal single pass. Indices inside a chunk are
     * chunk-local, so they are mapped back to library positions before the union is built.
     */
    private suspend fun selectChunked(
        request: String,
        targetMinutes: Int?,
        tracks: List<Track>,
        analyses: Map<Long, SongAnalysis>
    ): Selection {
        val chunks = chunkTracks(tracks, analyses)
        Log.i(TAG, "chunked: ${chunks.size} chunks")

        val candidates = LinkedHashSet<Int>()
        var anyChunkSucceeded = false

        chunks.forEach { chunk ->
            val chunkDigest = buildDigest(chunk.map { tracks[it] }, analyses)
            val messages = listOf(
                ChatMessage("system", CHUNK_SYSTEM_PROMPT),
                ChatMessage("user", userPrompt(request, targetMinutes, chunkDigest))
            )
            when (val result = callAndParse(messages, chunk.indices)) {
                is Selection.Picked -> {
                    anyChunkSucceeded = true
                    // chunk-local index -> library index
                    result.indices.forEach { local -> candidates.add(chunk[local]) }
                }
                is Selection.Failed -> Log.w(TAG, "chunk pass failed: ${result.message}")
            }
        }

        if (!anyChunkSucceeded) {
            return Selection.Failed("The model could not shortlist anything from your library.")
        }
        if (candidates.size < MIN_TRACKS) {
            return Selection.Failed("The model shortlisted too few tracks to build a playlist.")
        }

        val shortlist = candidates.toList()
        val finalDigest = buildDigest(shortlist.map { tracks[it] }, analyses)
        Log.i(TAG, "chunked: final pass over ${shortlist.size} candidates, ${finalDigest.length} chars")

        val messages = listOf(
            ChatMessage("system", SYSTEM_PROMPT),
            ChatMessage("user", userPrompt(request, targetMinutes, finalDigest))
        )
        return when (val result = callAndParse(messages, shortlist.indices)) {
            is Selection.Failed -> result
            // Final-pass indices point into the shortlist; translate once more.
            is Selection.Picked -> result.copy(indices = result.indices.map { shortlist[it] })
        }
    }

    /**
     * One call, and on a parse failure exactly one corrective retry before giving up.
     */
    private suspend fun callAndParse(
        messages: List<ChatMessage>,
        validRange: IntRange
    ): Selection {
        val first = client.chat(messages)
        val firstBody = first.getOrElse { error ->
            return Selection.Failed(error.message ?: "The request to the model failed.")
        }

        when (val parsed = PlaylistResponseParser.parse(firstBody)) {
            is ParseResult.Success -> return picked(parsed.playlist, validRange)
            is ParseResult.Failure -> Log.w(TAG, "first parse failed: ${parsed.reason}")
        }

        val retryMessages = messages + listOf(
            ChatMessage("assistant", firstBody.take(2000)),
            ChatMessage("user", CORRECTIVE_MESSAGE)
        )
        val second = client.chat(retryMessages)
        val secondBody = second.getOrElse { error ->
            return Selection.Failed(error.message ?: "The retry to the model failed.")
        }

        return when (val parsed = PlaylistResponseParser.parse(secondBody)) {
            is ParseResult.Success -> picked(parsed.playlist, validRange)
            is ParseResult.Failure ->
                Selection.Failed("The model did not return usable JSON (${parsed.reason}).")
        }
    }

    private fun picked(playlist: LlmPlaylist, validRange: IntRange): Selection {
        val clean = PlaylistResponseParser.sanitize(playlist.indices, validRange)
        val dropped = playlist.indices.size - clean.size
        if (dropped > 0) Log.i(TAG, "dropped $dropped out-of-range or repeated indices")
        return Selection.Picked(playlist.name, playlist.reasoning, clean)
    }

    // ---- prompt construction ---------------------------------------------------------

    /**
     * One line per song, in stable id order:
     *
     * ```
     * 0. Artist — Title (3:42)
     * 1. Artist — Title (4:15) | hopeful, positive | for: driving | energy .7 valence .8 | indie rock | about: road, freedom
     * ```
     *
     * The tags after the first pipe are only present for songs that have been analysed.
     */
    internal fun buildDigest(
        tracks: List<Track>,
        analyses: Map<Long, SongAnalysis> = emptyMap()
    ): String = buildString {
        tracks.forEachIndexed { index, track ->
            append(digestLine(index, track, analyses[track.id]))
            append('\n')
        }
    }

    private fun digestLine(index: Int, track: Track, analysis: SongAnalysis?): String = buildString {
        append(index)
        append(". ")
        append(track.artist)
        append(" — ")
        append(track.title)
        append(" (")
        append(formatDuration(track.durationMs))
        append(")")
        if (analysis == null) return@buildString

        append(" | ")
        append(analysis.mood)
        append(", ")
        append(analysis.sentiment)
        append(" | for: ")
        append(analysis.category)
        append(" | energy ")
        append(oneDecimal(analysis.energy))
        append(" valence ")
        append(oneDecimal(analysis.valence))
        if (analysis.genre.isNotBlank() && analysis.genre != "unknown") {
            append(" | ")
            append(analysis.genre)
        }
        if (analysis.themes.isNotBlank()) {
            append(" | about: ")
            append(analysis.themes)
        }
        // "heard" vs "guessed": a verdict from a transcript is worth more than one from a
        // filename, and the curator is told to weigh them that way.
        if (!analysis.fromLyrics) append(" | tags only")
    }

    /** `.7`, not `0.7000000001`: the digest is charged for by the character. */
    private fun oneDecimal(value: Float): String {
        val rounded = (value * 10).toInt().coerceIn(0, 10)
        return if (rounded == 10) "1.0" else ".$rounded"
    }

    /** Splits library positions into groups whose digest stays under [CHUNK_CHAR_LIMIT]. */
    private fun chunkTracks(
        tracks: List<Track>,
        analyses: Map<Long, SongAnalysis>
    ): List<List<Int>> {
        val chunks = ArrayList<List<Int>>()
        var current = ArrayList<Int>()
        var size = 0
        tracks.forEachIndexed { index, track ->
            // Measured rather than estimated: an analysed line is several times longer than
            // a bare one, and guessing here is what overflows a request.
            val lineLength = digestLine(9999, track, analyses[track.id]).length + 1
            if (size + lineLength > CHUNK_CHAR_LIMIT && current.isNotEmpty()) {
                chunks.add(current)
                current = ArrayList()
                size = 0
            }
            current.add(index)
            size += lineLength
        }
        if (current.isNotEmpty()) chunks.add(current)
        return chunks
    }

    private fun userPrompt(request: String, targetMinutes: Int?, digest: String): String =
        buildString {
            append("Request: ")
            append(request.trim())
            append('\n')
            if (targetMinutes != null && targetMinutes > 0) {
                append("Target total duration: about $targetMinutes minutes.\n")
            }
            append("\nLibrary:\n")
            append(digest)
        }

    companion object {
        private const val TAG = "AiPlaylistGenerator"

        /** Above this the digest is split; roughly the "~120k characters" budget. */
        private const val DIGEST_CHAR_LIMIT = 120_000

        /** Chunks stay comfortably under the single-pass limit. */
        private const val CHUNK_CHAR_LIMIT = 100_000

        /** Fewer surviving indices than this and we show an error rather than a stub playlist. */
        private const val MIN_TRACKS = 5

        val SYSTEM_PROMPT = """
You are a music curator. You will receive a numbered list of songs from the
user's personal library, and a request describing the playlist they want.

Most songs carry tags the app worked out by transcribing the song's own vocals
and reading what it heard:

  12. Artist — Title (3:42) | hopeful, positive | for: driving |
      energy .7 valence .8 | indie rock | about: road, freedom

  mood, sentiment  what the song feels like and whether it lands positive,
                   negative, neutral or mixed
  for:             the situation it suits — party, workout, focus, chill,
                   driving, sleep, heartbreak, love, motivation, protest,
                   spiritual, celebration, reflection
  energy           .0 still to 1.0 frantic
  valence          .0 bleak to 1.0 joyful
  about:           what the words are actually about
  tags only        no words could be transcribed, so those tags are a guess
                   from the title and artist — trust them less

Trust the tags over your own impression of a title: they come from the song
itself. A line with no tags after the duration has not been analysed yet —
judge it from the artist and title as best you can, and prefer a tagged song
when the two are otherwise equal.

Match the request against the tags first: a request for a mood, a feeling or a
situation is answered by mood, sentiment, for: and about:, not by genre.

Rules:
- Only return index numbers that appear in the provided list.
- Order the indices deliberately: build a listening arc that fits the request,
  using energy and valence to shape it (ease in, build, sustain, wind down)
  rather than a random ordering.
- Respect the requested total duration within about 10%.
- Do not repeat an index.
- Avoid stacking more than two songs by the same artist consecutively.

Respond with JSON only, no markdown fences, no commentary:
{"name": "<short playlist name, max 4 words>",
 "reasoning": "<one sentence on the vibe you built>",
 "indices": [12, 47, 3, ...]}
        """.trimIndent()

        /**
         * Used only for the per-chunk shortlisting pass of a very large library; the final
         * pass always uses [SYSTEM_PROMPT].
         */
        private val CHUNK_SYSTEM_PROMPT = """
You are a music curator shortlisting candidates. You will receive part of a
numbered list of songs from the user's personal library, and a request
describing the playlist they want.

Songs may carry tags the app worked out from the song's own words —
`| mood, sentiment | for: situation | energy .N valence .N | genre |
about: subjects |` — and `tags only` marks a song whose words could not be
transcribed, so its tags are a weaker guess. Use the tags first; fall back on
what you know about the artist for untagged lines.

Return the index numbers of every song in this part that could plausibly fit
the request. Be generous: this is a shortlist that will be narrowed down
later, so aim for two to three times more songs than the request needs.

Rules:
- Only return index numbers that appear in the provided list.
- Do not repeat an index.
- If nothing in this part fits, return an empty list.

Respond with JSON only, no markdown fences, no commentary:
{"name": "shortlist", "reasoning": "<one sentence>", "indices": [1, 4, 9, ...]}
        """.trimIndent()

        private val CORRECTIVE_MESSAGE = """
That was not valid JSON. Reply again with the JSON object only — no markdown
fences, no explanation, no text before or after it:
{"name": "...", "reasoning": "...", "indices": [1, 2, 3]}
        """.trimIndent()
    }
}
