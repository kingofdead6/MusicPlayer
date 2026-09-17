package dev.nk.musicplayer.data.llm

/**
 * The one thing every model reply has in common: the JSON is in there somewhere, wrapped in
 * markdown fences or prefaced with "Here you go". Shared by the playlist parser and the song
 * analysis parser so both tolerate exactly the same chatter.
 */
internal object LlmJson {

    fun extractObject(raw: String): String? {
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
}
