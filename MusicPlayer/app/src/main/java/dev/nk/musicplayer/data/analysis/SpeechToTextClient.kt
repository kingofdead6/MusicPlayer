package dev.nk.musicplayer.data.analysis

import android.util.Log
import dev.nk.musicplayer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Where transcription happens. Same token as the playlist model — a Hugging Face inference
 * endpoint that takes raw audio bytes and answers with `{"text": "..."}`. A build can point
 * this anywhere with `STT_URL` in `local.properties`; the wire format is the plain
 * audio-in/JSON-out shape every hosted Whisper deployment speaks.
 */
data class SttConfig(val url: String, val apiKey: String) {
    val isConfigured: Boolean get() = url.isNotBlank() && apiKey.isNotBlank()

    companion object {
        const val HF_WHISPER_URL =
            "https://router.huggingface.co/hf-inference/models/openai/whisper-large-v3"

        fun forUserKey(userKey: String) = SttConfig(
            url = BuildConfig.STT_URL.ifBlank { HF_WHISPER_URL },
            apiKey = userKey.ifBlank { BuildConfig.LLM_API_KEY }
        )
    }
}

@Serializable
private data class SttResponse(val text: String = "")

class SpeechToTextClient(private val configProvider: () -> SttConfig) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Transcribing 90 s of audio on a cold endpoint is the slowest call the app makes.
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = configProvider().isConfigured

    /**
     * @param wav 16 kHz mono WAV, as produced by [AudioSampler].
     * @return the recognised text, which is empty for an instrumental — that is a result, not
     *         an error, and the caller treats it as "this song has no words".
     */
    suspend fun transcribe(wav: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        val config = configProvider()
        if (!config.isConfigured) {
            return@withContext Result.failure(
                SttException("No API key set. Add one in Settings to analyse songs.")
            )
        }

        for (attempt in 1..MAX_ATTEMPTS) {
            val request = Request.Builder()
                .url(config.url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "audio/wav")
                // Serverless endpoints park a cold model behind a 503; ask them to hold the
                // request instead of bouncing it back.
                .addHeader("X-Wait-For-Model", "true")
                .post(wav.toRequestBody(WAV_MEDIA_TYPE))
                .build()

            val outcome = runCatching {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> Outcome.Text(readText(body))
                        // Model still loading: worth exactly one wait-and-retry.
                        response.code == 503 && attempt < MAX_ATTEMPTS -> Outcome.Retry
                        response.code == 401 || response.code == 403 -> Outcome.Fatal(
                            "The API key was rejected by the transcription model " +
                                "(HTTP ${response.code}). Check it in Settings."
                        )
                        response.code == 413 -> Outcome.Fatal(
                            "The audio sample was too large for the transcription model."
                        )
                        response.code == 429 -> Outcome.Fatal(
                            "Rate limited by the transcription model. Try again in a minute."
                        )
                        else -> {
                            Log.w(TAG, "STT HTTP ${response.code}: ${body.take(300)}")
                            Outcome.Fatal("The transcription model returned HTTP ${response.code}.")
                        }
                    }
                }
            }.getOrElse { error ->
                if (error is IOException) {
                    Log.w(TAG, "STT call failed", error)
                    Outcome.Fatal("Couldn't reach the transcription model. Check your connection.")
                } else {
                    Log.w(TAG, "STT response could not be read", error)
                    Outcome.Fatal("The transcription reply could not be read (${error.message}).")
                }
            }

            when (outcome) {
                is Outcome.Text -> return@withContext Result.success(outcome.value)
                is Outcome.Fatal -> return@withContext Result.failure(SttException(outcome.message))
                Outcome.Retry -> delay(RETRY_DELAY_MS)
            }
        }
        Result.failure(SttException("The transcription model stayed unavailable."))
    }

    /**
     * Hosted Whisper deployments answer with `{"text": "..."}`; a couple wrap that in a list.
     * Anything else is treated as "no words heard" rather than an error.
     */
    private fun readText(body: String): String {
        val trimmed = body.trim()
        val payload = if (trimmed.startsWith("[")) {
            trimmed.removePrefix("[").removeSuffix("]").trim()
        } else {
            trimmed
        }
        return runCatching { json.decodeFromString<SttResponse>(payload).text }
            .getOrElse {
                Log.w(TAG, "unrecognised STT payload: ${body.take(200)}")
                ""
            }
            .trim()
    }

    private sealed interface Outcome {
        data class Text(val value: String) : Outcome
        data class Fatal(val message: String) : Outcome
        data object Retry : Outcome
    }

    private companion object {
        const val TAG = "SpeechToTextClient"
        const val MAX_ATTEMPTS = 2
        const val RETRY_DELAY_MS = 8_000L
        val WAV_MEDIA_TYPE = "audio/wav".toMediaType()
    }
}

class SttException(override val message: String, cause: Throwable? = null) :
    Exception(message, cause)
