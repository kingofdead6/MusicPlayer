package dev.nk.musicplayer.data.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** What the model is asked to return: a name, one line of reasoning, and index numbers. */
data class LlmPlaylist(
    val name: String,
    val reasoning: String,
    val indices: List<Int>
)

sealed interface ParseResult {
    data class Success(val playlist: LlmPlaylist) : ParseResult
    data class Failure(val reason: String) : ParseResult
}

/**
 * Turns whatever the model actually sent into indices, or an explanation of why it could not.
 *
 * Deliberately free of Android and of [dev.nk.musicplayer.data.db.Track]: this is the one
 * piece with enough edge cases to be worth unit-testing, so it stays plain Kotlin.
 *
 * The hard rule of the whole feature lives in [sanitize]: only index numbers ever reach a
 * playlist, and anything outside the library's range is dropped without comment. A title the
 * model invented has no way through.
 */
object PlaylistResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String): ParseResult {
        val body = extractJsonObject(raw)
            ?: return ParseResult.Failure("No JSON object found in the response")

        val root: JsonObject = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return ParseResult.Failure("Response was not valid JSON (${e.message})")
        }

        val indicesElement = root["indices"]
            ?: return ParseResult.Failure("Response had no \"indices\" field")
        if (indicesElement !is JsonArray) {
            return ParseResult.Failure("\"indices\" was not a list")
        }

        // Some models emit ["12", "47"] rather than [12, 47]; both are accepted, anything
        // that is not a whole number is simply dropped.
        val indices = indicesElement.mapNotNull { element ->
            (element as? JsonPrimitive)?.content?.trim()?.toIntOrNull()
        }
        if (indices.isEmpty()) {
            return ParseResult.Failure("\"indices\" contained no usable numbers")
        }

        return ParseResult.Success(
            LlmPlaylist(
                name = root.stringOrNull("name")?.takeIf { it.isNotBlank() } ?: "AI playlist",
                reasoning = root.stringOrNull("reasoning").orEmpty(),
                indices = indices
            )
        )
    }

    /**
     * Drops indices outside [validRange] and repeats, preserving the model's ordering.
     * This is what makes it impossible for the model to add a song I do not own.
     */
    fun sanitize(indices: List<Int>, validRange: IntRange): List<Int> {
        val seen = LinkedHashSet<Int>()
        for (index in indices) {
            if (index in validRange) seen.add(index)
        }
        return seen.toList()
    }

    /**
     * Strips markdown fences and any chatter around the object. Models routinely wrap JSON in
     * ```json fences or preface it with "Here is your playlist:".
     */
    internal fun extractJsonObject(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        // Remove fenced blocks by taking what is inside the first fence pair, if present.
        val fence = Regex("```(?:[A-Za-z0-9_-]*)?\\s*\\n?([\\s\\S]*?)```")
        fence.find(text)?.let { match ->
            val inner = match.groupValues[1].trim()
            if (inner.isNotEmpty()) text = inner
        }
        // A stray unterminated fence can survive the pass above.
        text = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.content
}
