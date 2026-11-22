package io.github.jaswantk917.tring.recorder

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** whisper.cpp's C API (cpp/whisper_jni.cpp), used only in [WhisperService]'s process. One context at a time, never two calls at once. */
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

    /** JSON {"words": [{"w", "s", "e", "p", "g"}]} with times in ms of [pcm], or null when stopped or failed */
    external fun transcribe(context: Long, pcm: FloatArray, vadModel: String, threads: Int): String?
}

/**
 * OpenAI's Whisper (large-v3-turbo, 5-bit) through whisper.cpp on the phone's GPU, in its own
 * process ([WhisperService]), with its own speech detection skipping the silences. Far clearer than Shruti on real calls, but a 574 MB
 * download ([WhisperModel]) and about twice a call's length to run, so it runs while the phone
 * charges with its screen off.
 */
object Whisper {
    /** one word as heard: where it is in the audio given (ms), how sure Whisper is, and which of its sentences it's in */
    data class Word(val text: String, val startMs: Int, val endMs: Int, val probability: Float, val segment: Int)

    private const val TAG = "Whisper"

    val supported get() = WhisperNative.isAvailable

    fun ready(context: Context) = supported && WhisperModel.ready(context)

    /** [WhisperService] started for [block] and ended after: the model holds the best part of a gigabyte */
    suspend fun <T> session(context: Context, block: suspend Session.() -> T): T {
        val session = Session(context.applicationContext)
        return try {
            session.connect()
            session.block()
        } finally {
            session.end()
        }
    }

    /** cancelling a [transcribe] ends Whisper's process there and then, wherever whisper.cpp has got to */
    class Session internal constructor(private val context: Context) {
        private val bound = AtomicBoolean(false)
        private var service: Messenger? = null
        private var connecting: CancellableContinuation<Unit>? = null
        private var waiting: CancellableContinuation<String?>? = null

        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = Messenger(binder)
                connecting?.takeIf { it.isActive }?.resume(Unit)
                connecting = null
            }

            override fun onServiceDisconnected(name: ComponentName) = died()

            override fun onBindingDied(name: ComponentName) = died()
        }

        private val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.what == WhisperService.HEARD) {
                waiting?.takeIf { it.isActive }?.resume(message.data.getString(WhisperService.JSON))
                waiting = null
            }
            true
        })

        // it crashed or was killed by the system: the piece fails, the app carries on
        private fun died() {
            service = null
            waiting?.takeIf { it.isActive }?.resumeWithException(IllegalStateException("Whisper stopped"))
            waiting = null
        }

        internal suspend fun connect() = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                connecting = continuation
                bound.set(true)
                context.bindService(Intent(context, WhisperService::class.java), connection, Context.BIND_AUTO_CREATE)
                continuation.invokeOnCancellation { end() }
            }
        }

        /** the words in [fromMs] to [toMs] of the decoded call in [pcm] (16 kHz mono, 16-bit) */
        suspend fun transcribe(pcm: File, fromMs: Int, toMs: Int): List<Word> {
            val started = System.currentTimeMillis()
            val json = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    waiting = continuation
                    val ask = Message.obtain(null, WhisperService.LISTEN).apply {
                        data = Bundle().apply {
                            putString(WhisperService.PCM, pcm.path)
                            putInt(WhisperService.FROM, fromMs)
                            putInt(WhisperService.TO, toMs)
                        }
                        replyTo = replies
                    }
                    try {
                        service!!.send(ask)
                    } catch (e: Exception) {
                        continuation.resumeWithException(IllegalStateException("Whisper stopped"))
                    }
                    continuation.invokeOnCancellation { end() }
                }
            }
            val words = parse(json ?: error("Whisper failed"))
            Log.i(TAG, "${toMs - fromMs} ms of audio in ${System.currentTimeMillis() - started} ms, ${words.size} words")
            return words
        }

        /** unbound first, so the system doesn't bring the service back, then its process ended */
        internal fun end() {
            if (!bound.getAndSet(false)) return
            runCatching { context.unbindService(connection) }
            context.getSystemService(ActivityManager::class.java).runningAppProcesses
                ?.filter { it.processName == context.packageName + WhisperService.PROCESS }
                ?.forEach { Process.killProcess(it.pid) }
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
