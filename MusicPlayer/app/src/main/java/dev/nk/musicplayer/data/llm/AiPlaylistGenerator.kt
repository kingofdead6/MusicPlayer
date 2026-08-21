package dev.nk.musicplayer.data.llm

import android.util.Log
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.data.library.LibraryRepository
import dev.nk.musicplayer.util.formatDuration

/**
 * Turns "make me a 3-hour traveling playlist, calm" into tracks I own.
 *
 * The model only ever sees a numbered list and only ever answers with numbers. Titles it
 * returns are ignored entirely — see [PlaylistResponseParser.sanitize].
 */
class AiPlaylistGenerator(
    private val library: LibraryRepository,
    private val client: LlmClient
) {

    sealed interface Result {
        data class Success(
            val name: String,
            val reasoning: String,
            val tracks: List<Track>
        ) : Result

        data class Failure(val message: String) : Result
    }

    val isConfigured: Boolean get() = client.isConfigured

    suspend fun generate(request: String, targetMinutes: Int?): Result {
        val tracks = library.allTracks()
        if (tracks.isEmpty()) {
            return Result.Failure("There is no music in the library yet. Scan first.")
        }

        val digest = buildDigest(tracks)
        val indices = if (digest.length <= DIGEST_CHAR_LIMIT) {
            Log.i(TAG, "single-pass: ${tracks.size} tracks, ${digest.length} chars")
            selectSinglePass(request, targetMinutes, digest, tracks.indices)
        } else {
            Log.i(TAG, "chunked: ${tracks.size} tracks, ${digest.length} chars exceeds $DIGEST_CHAR_LIMIT")
            selectChunked(request, targetMinutes, tracks)
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
                    Result.Success(indices.name, indices.reasoning, chosen)
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
        tracks: List<Track>
    ): Selection {
        val chunks = chunkTracks(tracks)
        Log.i(TAG, "chunked: ${chunks.size} chunks")

        val candidates = LinkedHashSet<Int>()
        var anyChunkSucceeded = false

        chunks.forEach { chunk ->
            val chunkDigest = buildDigest(chunk.map { tracks[it] })
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
        val finalDigest = buildDigest(shortlist.map { tracks[it] })
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

    /** `0. Artist — Title (3:42)`, one per line, in stable id order. */
    internal fun buildDigest(tracks: List<Track>): String = buildString {
        tracks.forEachIndexed { index, track ->
            append(index)
            append(". ")
            append(track.artist)
            append(" — ")
            append(track.title)
            append(" (")
            append(formatDuration(track.durationMs))
            append(")\n")
        }
    }

    /** Splits library positions into groups whose digest stays under [CHUNK_CHAR_LIMIT]. */
    private fun chunkTracks(tracks: List<Track>): List<List<Int>> {
        val chunks = ArrayList<List<Int>>()
        var current = ArrayList<Int>()
        var size = 0
        tracks.forEachIndexed { index, track ->
            // "9999. " + artist + " — " + title + " (0:00)\n"
            val lineLength = track.artist.length + track.title.length + 20
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

Select songs from the list that fit the request. Use your knowledge of these
artists and songs to judge mood, energy, and style — the list contains no
genre or tempo data.

Rules:
- Only return index numbers that appear in the provided list.
- Order the indices deliberately: build a listening arc that fits the request
  (ease in, build, sustain, wind down) rather than a random ordering.
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
