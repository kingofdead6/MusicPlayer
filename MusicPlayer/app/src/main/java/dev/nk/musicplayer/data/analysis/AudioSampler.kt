package dev.nk.musicplayer.data.analysis

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Cuts a small, speech-recognisable sample out of a song.
 *
 * Speech-to-text models want 16 kHz mono and charge by audio length, and a four-minute MP3 is
 * both too long and in the wrong shape. So the file is decoded with [MediaCodec] — which
 * handles every codec the device can play, not just the two a [android.media.MediaMuxer]
 * could re-wrap — downmixed to mono, resampled to 16 kHz and written as a WAV.
 *
 * Three short windows spread across the song are used rather than one block from the start:
 * an intro is often instrumental, and a verse plus a chorus says far more about what a song
 * is about than ninety seconds of its first minute.
 */
class AudioSampler(private val context: Context) {

    /**
     * @return WAV bytes (16 kHz, mono, 16-bit), or a failure describing why this file could
     *         not be sampled. A failure here is not fatal to analysis: the caller falls back
     *         to classifying from metadata alone.
     */
    suspend fun sample(uri: String, trackDurationMs: Long): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            var extractor: MediaExtractor? = null
            var codec: MediaCodec? = null
            try {
                extractor = MediaExtractor().apply { setDataSource(context, Uri.parse(uri), null) }

                val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                        ?.startsWith("audio/") == true
                } ?: return@withContext Result.failure(
                    SamplingException("The file has no audio track.")
                )

                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                    ?: return@withContext Result.failure(SamplingException("Unknown audio format."))

                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else {
                    trackDurationMs * 1000
                }

                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val decoded = decodeWindows(extractor, codec, durationUs)
                if (decoded.samples.isEmpty()) {
                    return@withContext Result.failure(
                        SamplingException("Nothing could be decoded from the file.")
                    )
                }

                val mono16k = resample(decoded.samples, decoded.sampleRate, TARGET_SAMPLE_RATE)
                Log.i(
                    TAG,
                    "sampled $uri: ${decoded.samples.size} src frames @ ${decoded.sampleRate}Hz " +
                        "-> ${mono16k.size} frames @ ${TARGET_SAMPLE_RATE}Hz"
                )
                Result.success(toWav(mono16k))
            } catch (e: Exception) {
                Log.w(TAG, "sampling failed for $uri", e)
                Result.failure(SamplingException("Could not read the audio (${e.message}).", e))
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { extractor?.release() }
            }
        }

    // ---- decoding --------------------------------------------------------------------

    private class Decoded(val samples: ShortArray, val sampleRate: Int)

    /**
     * Decodes a handful of windows spread over the song into one mono buffer, at whatever
     * rate the decoder emits.
     */
    private suspend fun decodeWindows(
        extractor: MediaExtractor,
        codec: MediaCodec,
        durationUs: Long
    ): Decoded {
        val out = ShortBucket()
        // The output format is announced once, before the first buffer. A later window's
        // flush() does not necessarily announce it again, so it is held across windows
        // rather than rediscovered per window.
        val state = DecodeState()

        val starts = windowStarts(durationUs)
        for (startUs in starts) {
            currentCoroutineContext().ensureActive()
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            codec.flush()
            val reachedEnd = decodeOneWindow(extractor, codec, state, out)
            if (reachedEnd) break
        }

        Log.i(
            TAG,
            "decoded ${out.size} mono frames from ${starts.size} window(s), " +
                "${state.channels} ch @ ${state.sampleRate}Hz"
        )
        return Decoded(out.toShortArray(), state.sampleRate)
    }

    /** What the decoder said about its output, once it says it. */
    private class DecodeState {
        var sampleRate = TARGET_SAMPLE_RATE
        var channels = 0
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        fun readFrom(format: MediaFormat) {
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            }
        }
    }

    /** @return true when the decoder ran off the end of the file. */
    private suspend fun decodeOneWindow(
        extractor: MediaExtractor,
        codec: MediaCodec,
        state: DecodeState,
        out: ShortBucket
    ): Boolean {
        val info = MediaCodec.BufferInfo()
        var framesCollected = 0L
        var inputDone = false
        var endOfStream = false
        val deadline = System.nanoTime() + WINDOW_TIMEOUT_NS

        while (true) {
            currentCoroutineContext().ensureActive()
            if (System.nanoTime() > deadline) {
                Log.w(TAG, "window decode timed out")
                break
            }

            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex)
                    val read = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                    if (read < 0) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                        endOfStream = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, read, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> state.readFrom(codec.outputFormat)

                outputIndex >= 0 -> {
                    if (info.size > 0 && state.channels > 0) {
                        val buffer = codec.getOutputBuffer(outputIndex)
                        if (buffer != null) {
                            framesCollected += appendMono(buffer, info, state, out)
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        endOfStream = true
                        break
                    }
                }
            }

            // Measured in frames, which needs the real rate — hence after the format arrives.
            if (state.channels > 0 &&
                framesCollected >= state.sampleRate.toLong() * WINDOW_MS / 1000
            ) {
                break
            }
        }

        return endOfStream
    }

    /**
     * Folds one decoded buffer down to mono and appends it. Decoders may hand back 16-bit
     * integer PCM or float PCM depending on the device and codec, so both are handled.
     *
     * @return how many mono frames were appended.
     */
    private fun appendMono(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        state: DecodeState,
        out: ShortBucket
    ): Int {
        val channels = state.channels
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val ordered = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)

        return if (state.pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val floats = ordered.asFloatBuffer()
            val frames = floats.remaining() / channels
            repeat(frames) {
                var sum = 0f
                repeat(channels) { sum += floats.get() }
                val value = (sum / channels * Short.MAX_VALUE)
                    .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
                out.add(value.toInt().toShort())
            }
            frames
        } else {
            val shorts = ordered.asShortBuffer()
            val frames = shorts.remaining() / channels
            repeat(frames) {
                var sum = 0
                repeat(channels) { sum += shorts.get().toInt() }
                out.add((sum / channels).toShort())
            }
            frames
        }
    }

    /**
     * Where to cut. Short songs get one window from the top; anything long enough is sampled
     * at a few points so a verse and a chorus both stand a chance of being heard.
     */
    private fun windowStarts(durationUs: Long): List<Long> {
        val windowUs = WINDOW_MS * 1000L
        if (durationUs <= windowUs * 2) return listOf(0L)
        return WINDOW_FRACTIONS.map { fraction ->
            ((durationUs * fraction).toLong()).coerceAtMost(durationUs - windowUs)
                .coerceAtLeast(0L)
        }
    }

    // ---- PCM helpers -----------------------------------------------------------------

    /** Linear-interpolating resampler. Speech recognition does not need a better one. */
    private fun resample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate <= 0 || fromRate == toRate || input.isEmpty()) return input
        val outLength = (input.size.toLong() * toRate / fromRate).toInt().coerceAtLeast(1)
        val output = ShortArray(outLength)
        val step = fromRate.toDouble() / toRate
        for (i in 0 until outLength) {
            val position = i * step
            val left = position.toInt()
            val right = (left + 1).coerceAtMost(input.size - 1)
            val fraction = position - left
            val value = input[left] + (input[right] - input[left]) * fraction
            output[i] = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return output
    }

    /** Minimal 16-bit PCM WAV container: a 44-byte header and the samples. */
    private fun toWav(samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)                                   // PCM header size
        header.putShort(1)                                  // PCM format
        header.putShort(1)                                  // mono
        header.putInt(TARGET_SAMPLE_RATE)
        header.putInt(TARGET_SAMPLE_RATE * 2)               // byte rate
        header.putShort(2)                                  // block align
        header.putShort(16)                                 // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        out.write(header.array())

        val body = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { body.putShort(it) }
        out.write(body.array())
        return out.toByteArray()
    }

    /** Growable ShortArray; an ArrayList<Short> would box a million samples. */
    private class ShortBucket {
        private var data = ShortArray(INITIAL_CAPACITY)
        var size = 0
            private set

        fun add(value: Short) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }

        fun toShortArray(): ShortArray = data.copyOf(size)

        private companion object {
            const val INITIAL_CAPACITY = 1 shl 16
        }
    }

    private companion object {
        const val TAG = "AudioSampler"

        /** Whisper-family models are trained at 16 kHz; anything higher is wasted bytes. */
        const val TARGET_SAMPLE_RATE = 16_000

        const val WINDOW_MS = 30_000L
        val WINDOW_FRACTIONS = listOf(0.12, 0.42, 0.70)

        const val DEQUEUE_TIMEOUT_US = 10_000L

        /** A decoder that stops producing output must not hang the whole run. */
        const val WINDOW_TIMEOUT_NS = 20_000_000_000L
    }
}

class SamplingException(override val message: String, cause: Throwable? = null) :
    Exception(message, cause)
