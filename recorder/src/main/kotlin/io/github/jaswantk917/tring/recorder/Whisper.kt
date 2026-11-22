package io.github.jaswantk917.tring.recorder

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** whisper.cpp's C API (cpp/whisper_jni.cpp). One context at a time, never two calls at once. */
internal object WhisperNative {
    // only arm64 builds carry it
    val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("whisper_jni")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    /** 0 when the model can't be read */
    external fun load(model: String): Long

    external fun free(context: Long)

    /** the [transcribe] running now returns soon, with null */
    external fun stop()

    /** JSON {"words": [{"w", "s", "e", "p", "g"}]} with times in ms of [pcm], or null when stopped or failed */
    external fun transcribe(context: Long, pcm: FloatArray, vadModel: String, threads: Int): String?
}

/**
 * OpenAI's Whisper (large-v3-turbo, 5-bit) through whisper.cpp on the phone's GPU, with its own
 * speech detection skipping the silences. Far clearer than Shruti on real calls, but a 574 MB
 * download ([WhisperModel]) and about twice a call's length to run, so it runs while the phone
 * charges with its screen off.
 */
object Whisper {
    /** one word as heard: where it is in the audio given (ms), how sure Whisper is, and which of its sentences it's in */
    data class Word(val text: String, val startMs: Int, val endMs: Int, val probability: Float, val segment: Int)

    private const val TAG = "Whisper"
    // the GPU does the heavy part; these run the speech detection and what the GPU can't
    private const val THREADS = 4

    @OptIn(ExperimentalCoroutinesApi::class)
    private val engine = Dispatchers.IO.limitedParallelism(1)

    val supported get() = WhisperNative.isAvailable

    fun ready(context: Context) = supported && WhisperModel.ready(context)

    /** the model loaded for [block], let go after: it holds the best part of a gigabyte */
    suspend fun <T> session(context: Context, block: suspend Session.() -> T): T = withContext(engine) {
        val handle = WhisperNative.load(WhisperModel.file(context).path)
        check(handle != 0L) { "Whisper's model couldn't be read" }
        try {
            Session(handle, WhisperModel.vad(context).path).block()
        } finally {
            WhisperNative.free(handle)
        }
    }

    class Session internal constructor(private val handle: Long, private val vad: String) {
        /** the words in 16 kHz mono [pcm] in [-1, 1] */
        suspend fun transcribe(pcm: FloatArray): List<Word> = coroutineScope {
            val started = System.currentTimeMillis()
            // whisper.cpp can't be interrupted, only asked to stop between steps: cancelling asks it
            val watch = launch(Dispatchers.Default) {
                try {
                    awaitCancellation()
                } finally {
                    WhisperNative.stop()
                }
            }
            val json = try {
                WhisperNative.transcribe(handle, pcm, vad, THREADS)
            } finally {
                watch.cancel()
            }
            ensureActive()
            val words = parse(json ?: error("Whisper failed"))
            Log.i(TAG, "${pcm.size / 16} ms of audio in ${System.currentTimeMillis() - started} ms, ${words.size} words")
            words
        }
    }

    fun parse(json: String): List<Word> {
        val words = JSONObject(json).optJSONArray("words") ?: return emptyList()
        return (0 until words.length()).map { i ->
            val word = words.getJSONObject(i)
            Word(word.getString("w"), word.getInt("s"), word.getInt("e"), word.getDouble("p").toFloat(), word.getInt("g"))
        }
    }
}
