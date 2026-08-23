package dev.nk.musicplayer.data.llm

import android.util.Log
import dev.nk.musicplayer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Endpoint settings. The API key is entered by the user in Settings; the base URL and model
 * still come from `local.properties` via BuildConfig when a build wants to point somewhere
 * else, otherwise they fall back to the Hugging Face router. Nothing about the provider is
 * baked into the code beyond the OpenAI-compatible wire format.
 */
data class LlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /** `<base>/chat/completions`, tolerating a trailing slash in local.properties. */
    val chatCompletionsUrl: String
        get() = baseUrl.trimEnd('/') + "/chat/completions"

    companion object {
        /** Hugging Face's OpenAI-compatible inference router. */
        const val HF_BASE_URL = "https://router.huggingface.co/v1"
        const val HF_DEFAULT_MODEL = "meta-llama/Llama-3.3-70B-Instruct"

        /**
         * The user's key wins over anything baked into the build, so entering one in Settings
         * always takes effect. Base URL and model keep their BuildConfig values when set.
         */
        fun forUserKey(userKey: String) = LlmConfig(
            baseUrl = BuildConfig.LLM_BASE_URL.ifBlank { HF_BASE_URL },
            apiKey = userKey.ifBlank { BuildConfig.LLM_API_KEY },
            model = BuildConfig.LLM_MODEL.ifBlank { HF_DEFAULT_MODEL }
        )
    }
}

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean = false
)

@Serializable
private data class ChatResponse(val choices: List<Choice> = emptyList())

@Serializable
private data class Choice(val message: ChatMessage? = null)

/**
 * [configProvider] is re-read on every call rather than captured once: the user can paste a
 * key into Settings while the app is running and the very next generation must use it.
 */
class LlmClient(private val configProvider: () -> LlmConfig) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Curating a whole library is slow; 90 s is the budget before we give up.
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = configProvider().isConfigured

    suspend fun chat(messages: List<ChatMessage>): Result<String> = withContext(Dispatchers.IO) {
        val config = configProvider()
        if (!config.isConfigured) {
            return@withContext Result.failure(
                LlmFailureException(
                    "No Hugging Face API key set. Add one in Settings to turn on AI playlists."
                )
            )
        }

        val payload = json.encodeToString(
            ChatRequest(
                model = config.model,
                messages = messages,
                temperature = 0.8,
                maxTokens = 4096
            )
        )

        val request = Request.Builder()
            .url(config.chatCompletionsUrl)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "LLM HTTP ${response.code}: ${body.take(500)}")
                    // 401/403 is almost always a bad or expired token, so point at Settings.
                    val message = if (response.code == 401 || response.code == 403) {
                        "Hugging Face rejected the API key (HTTP ${response.code}). " +
                            "Check it in Settings."
                    } else {
                        "The model returned HTTP ${response.code}."
                    }
                    return@withContext Result.failure(LlmFailureException(message))
                }
                val content = json.decodeFromString<ChatResponse>(body)
                    .choices.firstOrNull()?.message?.content
                if (content.isNullOrBlank()) {
                    return@withContext Result.failure(
                        LlmFailureException("The model returned an empty response.")
                    )
                }
                Result.success(content)
            }
        } catch (e: IOException) {
            // No network, DNS failure, timeout: playback is untouched, the UI just says so.
            Log.w(TAG, "LLM call failed", e)
            Result.failure(
                LlmFailureException("Couldn't reach the model. Check your connection and try again.", e)
            )
        } catch (e: Exception) {
            Log.w(TAG, "LLM response could not be read", e)
            Result.failure(LlmFailureException("The model's reply could not be read (${e.message}).", e))
        }
    }

    private companion object {
        const val TAG = "LlmClient"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class LlmFailureException(override val message: String, cause: Throwable? = null) :
    Exception(message, cause)
