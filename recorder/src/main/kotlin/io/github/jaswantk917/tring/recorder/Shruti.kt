package io.github.jaswantk917.tring.recorder

import android.content.Context
import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** The needle engine's C API (cpp/shruti_jni.cpp). One model per process, and never two calls at once. */
internal object Needle {
    // only arm64 builds carry the engine
    val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("shruti")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    /** [model] is a direct buffer that must never be freed. Negative on failure. */
    external fun load(model: ByteBuffer): Int

    external fun lastError(): String

    /** JSON {"text": ..., "language": ..., "words": [{"word", "start", "end", "probability"}]} for the whole clip, or null on failure. */
    external fun transcribe(pcm: FloatArray, samples: Int, language: String?, keywords: String?): String?
}

/**
 * Udgam Labs' Shruti, the speech model Hey re uses: made for Hindi, English and Hinglish, bundled,
 * fully offline. Every call goes through one thread, since needle is neither re-entrant nor thread-safe.
 */
object Shruti {
    private const val TAG = "Shruti"
    private const val MODEL = "shruti/shruti.cact"
    // left to guess, Hinglish opening with an English word is taken for English; Hindi still writes English words in English (Hey re)
    private const val LANGUAGE = "hi"

    @OptIn(ExperimentalCoroutinesApi::class)
    private val engine = Dispatchers.IO.limitedParallelism(1)

    // mapped straight from the APK (stored uncompressed) and kept for as long as the app runs, since needle keeps reading it
    @Volatile
    private var model: ByteBuffer? = null

    val available get() = Needle.isAvailable

    /** what was heard, and how sure Shruti is of it: the mean of its words' probabilities, 1 when it gave none */
    data class Heard(val text: String, val confidence: Float)

    /** the words in 16 kHz mono [pcm] in [-1, 1]; [names] are spelt as given when heard */
    suspend fun transcribe(context: Context, pcm: FloatArray, names: List<String> = emptyList()): Heard = withContext(engine) {
        load(context)
        val started = System.currentTimeMillis()
        val json = Needle.transcribe(pcm, pcm.size, LANGUAGE, names.joinToString("\n").ifEmpty { null })
            ?: error(Needle.lastError())
        val heard = parse(json)
        Log.i(TAG, "${pcm.size / 16} ms of audio in ${System.currentTimeMillis() - started} ms, confidence ${"%.2f".format(heard.confidence)}")
        heard
    }

    fun parse(json: String): Heard {
        val result = JSONObject(json)
        val words = result.optJSONArray("words")
        val probabilities = (0 until (words?.length() ?: 0)).mapNotNull { words!!.optJSONObject(it)?.optDouble("probability")?.takeIf { p -> !p.isNaN() } }
        return Heard(result.optString("text").trim(), if (probabilities.isEmpty()) 1f else probabilities.average().toFloat())
    }

    private fun load(context: Context) {
        if (model != null) return
        check(Needle.isAvailable) { "Shruti needs a 64-bit ARM phone" }
        val buffer = context.assets.openFd(MODEL).use { file ->
            FileInputStream(file.fileDescriptor).channel.use { it.map(FileChannel.MapMode.READ_ONLY, file.startOffset, file.declaredLength) }
        }
        check(Needle.load(buffer) >= 0) { Needle.lastError() }
        model = buffer
    }
}
