package dev.nk.musicplayer.data.analysis

import dev.nk.musicplayer.data.llm.LlmJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** The classification the model is asked for, after normalisation. */
data class SongVerdict(
    val language: String,
    val sentiment: String,
    val mood: String,
    val category: String,
    val genre: String,
    val valence: Float,
    val energy: Float,
    val themes: List<String>,
    val summary: String,
    val explicit: Boolean
)

sealed interface AnalysisParseResult {
    data class Success(val verdict: SongVerdict) : AnalysisParseResult
    data class Failure(val reason: String) : AnalysisParseResult
}

/**
 * Turns the classifier's reply into a row the rest of the app can group by.
 *
 * The normalisation is the point. A model asked for a mood answers "melancholy" one call and
 * "Melancholic vibes." the next, and two spellings of one feeling make the playlist prompt and
 * any future filter worse, not better. So free text is folded onto a small vocabulary,
 * numbers are clamped, and anything unrecognised survives as a plain lowercase word rather
 * than being thrown away — the model may well have heard something the vocabulary lacks.
 *
 * Plain Kotlin, no Android: this is the piece worth unit-testing.
 */
object SongAnalysisParser {

    /** Feelings. Deliberately small: a big vocabulary is a vocabulary nothing groups by. */
    val MOODS = listOf(
        "happy", "sad", "angry", "calm", "romantic", "melancholic", "hopeful",
        "energetic", "dark", "nostalgic", "spiritual", "playful", "defiant", "anxious"
    )

    /** What a song is *for*. This is what a request like "something for the gym" matches. */
    val CATEGORIES = listOf(
        "party", "workout", "focus", "chill", "driving", "sleep", "heartbreak",
        "love", "motivation", "protest", "spiritual", "celebration", "reflection"
    )

    val SENTIMENTS = listOf("positive", "negative", "neutral", "mixed")

    private val MOOD_SYNONYMS = mapOf(
        "melancholy" to "melancholic", "melancholia" to "melancholic",
        "somber" to "melancholic", "sombre" to "melancholic", "wistful" to "melancholic",
        "bittersweet" to "melancholic", "mournful" to "sad", "sorrowful" to "sad",
        "heartbroken" to "sad", "depressing" to "sad", "grief" to "sad",
        "joyful" to "happy", "joyous" to "happy", "cheerful" to "happy",
        "upbeat" to "happy", "euphoric" to "happy", "uplifting" to "hopeful",
        "optimistic" to "hopeful", "inspiring" to "hopeful", "triumphant" to "hopeful",
        "furious" to "angry", "aggressive" to "angry", "rage" to "angry",
        "bitter" to "angry", "peaceful" to "calm", "serene" to "calm",
        "relaxed" to "calm", "mellow" to "calm", "soothing" to "calm",
        "tender" to "romantic", "loving" to "romantic", "sensual" to "romantic",
        "intense" to "energetic", "hype" to "energetic", "driving" to "energetic",
        "excited" to "energetic", "brooding" to "dark", "menacing" to "dark",
        "haunting" to "dark", "moody" to "dark", "gloomy" to "dark",
        "wistfulness" to "nostalgic", "reminiscent" to "nostalgic",
        "devotional" to "spiritual", "worshipful" to "spiritual", "reverent" to "spiritual",
        "fun" to "playful", "cheeky" to "playful", "lighthearted" to "playful",
        "rebellious" to "defiant", "confident" to "defiant", "empowering" to "defiant",
        "nervous" to "anxious", "restless" to "anxious", "tense" to "anxious"
    )

    private val CATEGORY_SYNONYMS = mapOf(
        "dance" to "party", "club" to "party", "night out" to "party", "rave" to "party",
        "gym" to "workout", "running" to "workout", "exercise" to "workout",
        "training" to "workout", "cardio" to "workout",
        "study" to "focus", "studying" to "focus", "work" to "focus",
        "concentration" to "focus", "deep work" to "focus",
        "relax" to "chill", "relaxing" to "chill", "lounge" to "chill",
        "background" to "chill", "sunday morning" to "chill",
        "road trip" to "driving", "travel" to "driving", "traveling" to "driving",
        "travelling" to "driving", "commute" to "driving", "highway" to "driving",
        "bedtime" to "sleep", "night" to "sleep", "lullaby" to "sleep",
        "breakup" to "heartbreak", "break-up" to "heartbreak", "loss" to "heartbreak",
        "longing" to "heartbreak", "romance" to "love", "wedding" to "love",
        "motivational" to "motivation", "pump up" to "motivation", "hustle" to "motivation",
        "political" to "protest", "resistance" to "protest", "activism" to "protest",
        "religious" to "spiritual", "faith" to "spiritual", "gospel" to "spiritual",
        "worship" to "spiritual", "celebratory" to "celebration", "victory" to "celebration",
        "introspective" to "reflection", "contemplative" to "reflection",
        "thoughtful" to "reflection", "solitude" to "reflection"
    )

    private val SENTIMENT_SYNONYMS = mapOf(
        "pos" to "positive", "hopeful" to "positive", "uplifting" to "positive",
        "neg" to "negative", "sad" to "negative", "dark" to "negative",
        "ambivalent" to "mixed", "bittersweet" to "mixed", "conflicted" to "mixed",
        "neutral/mixed" to "mixed", "none" to "neutral", "unknown" to "neutral"
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): AnalysisParseResult {
        val body = LlmJson.extractObject(raw)
            ?: return AnalysisParseResult.Failure("No JSON object found in the response")

        val root: JsonObject = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return AnalysisParseResult.Failure("Response was not valid JSON (${e.message})")
        }

        val mood = canonical(root.string("mood"), MOODS, MOOD_SYNONYMS)
        val category = canonical(root.string("category"), CATEGORIES, CATEGORY_SYNONYMS)
        val sentiment = canonical(root.string("sentiment"), SENTIMENTS, SENTIMENT_SYNONYMS)

        // A reply with none of the three classifications is a reply about something else.
        if (mood.isEmpty() && category.isEmpty() && sentiment.isEmpty()) {
            return AnalysisParseResult.Failure("Response had no mood, category or sentiment")
        }

        val valence = root.float("valence") ?: valenceFromSentiment(sentiment)
        val energy = root.float("energy") ?: energyFromMood(mood)

        return AnalysisParseResult.Success(
            SongVerdict(
                language = root.string("language").lowercase().ifBlank { "unknown" }.take(24),
                sentiment = sentiment.ifEmpty { "neutral" },
                mood = mood.ifEmpty { "unknown" },
                category = category.ifEmpty { "chill" },
                genre = root.string("genre").lowercase().take(32).ifBlank { "unknown" },
                valence = valence,
                energy = energy,
                themes = themes(root),
                summary = root.string("summary").take(SUMMARY_LIMIT),
                explicit = root.bool("explicit")
            )
        )
    }

    /**
     * Folds one free-text label onto [vocabulary]. Exact match wins, then a known synonym,
     * then a vocabulary word contained in the answer ("quietly hopeful" -> hopeful). Failing
     * all three the first word is kept as-is, so a mood nobody listed still groups with itself.
     */
    internal fun canonical(
        value: String,
        vocabulary: List<String>,
        synonyms: Map<String, String>
    ): String {
        val cleaned = value.lowercase().trim().trim('.', '!', ',', ';', ':', '"', '\'')
        if (cleaned.isEmpty()) return ""
        if (cleaned in vocabulary) return cleaned
        synonyms[cleaned]?.let { return it }

        val words = cleaned.split(' ', '/', '-', ',', '&').map { it.trim() }.filter { it.isNotEmpty() }
        words.firstOrNull { it in vocabulary }?.let { return it }
        words.firstNotNullOfOrNull { synonyms[it] }?.let { return it }
        vocabulary.firstOrNull { cleaned.contains(it) }?.let { return it }

        return words.firstOrNull()?.take(24).orEmpty()
    }

    /** Themes are the free-text part: kept lowercase, de-duplicated and capped. */
    private fun themes(root: JsonObject): List<String> {
        val element = root["themes"]
        val raw = when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> element.content.split(',')
            else -> emptyList()
        }
        return raw
            .map { it.lowercase().trim().trim('.', '"', '\'').take(THEME_LENGTH_LIMIT) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_THEMES)
    }

    /** A model that skipped the number still gave a sentiment; derive something usable. */
    private fun valenceFromSentiment(sentiment: String): Float = when (sentiment) {
        "positive" -> 0.8f
        "negative" -> 0.2f
        "mixed" -> 0.5f
        else -> 0.5f
    }

    private fun energyFromMood(mood: String): Float = when (mood) {
        "energetic", "angry", "defiant" -> 0.85f
        "happy", "playful" -> 0.7f
        "hopeful", "romantic", "spiritual", "anxious" -> 0.5f
        "calm", "sad", "melancholic", "nostalgic" -> 0.3f
        else -> 0.5f
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.content?.trim().orEmpty()

    /** Accepts 0.7, "0.7", and the 0-100 scale models sometimes use anyway. */
    private fun JsonObject.float(key: String): Float? {
        val value = (this[key] as? JsonPrimitive)?.content?.trim()?.toFloatOrNull() ?: return null
        val scaled = if (value > 1f) value / 100f else value
        return scaled.coerceIn(0f, 1f)
    }

    private fun JsonObject.bool(key: String): Boolean {
        val value = (this[key] as? JsonPrimitive)?.content?.trim()?.lowercase() ?: return false
        return value == "true" || value == "yes" || value == "1"
    }

    private const val MAX_THEMES = 5
    private const val THEME_LENGTH_LIMIT = 28
    private const val SUMMARY_LIMIT = 180
}
