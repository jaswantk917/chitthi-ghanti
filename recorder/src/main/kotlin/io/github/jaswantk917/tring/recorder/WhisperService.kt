package io.github.jaswantk917.tring.recorder

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import java.io.File

/**
 * Whisper in a process of its own (":whisper"), one piece at a time: a decoded call's path and the
 * piece's bounds in, the words heard out ([WhisperNative.transcribe]'s JSON). The app stops it by
 * ending the process, which no step of whisper.cpp can hold up.
 */
class WhisperService : Service() {
    private val worker = HandlerThread("whisper").apply { start() }
    private var model = 0L

    private val handler = Handler(worker.looper) { message ->
        val ask = message.data
        val json = runCatching {
            if (model == 0L && WhisperNative.isAvailable) model = WhisperNative.load(WhisperModel.file(this).path)
            if (model == 0L) null
            else WhisperNative.transcribe(
                model,
                Transcriber.read(File(ask.getString(PCM)!!), ask.getInt(FROM), ask.getInt(TO)),
                WhisperModel.vad(this).path,
                THREADS,
            )
        }.getOrNull()
        message.replyTo.send(Message.obtain(null, HEARD).apply { data = Bundle().apply { putString(JSON, json) } })
        true
    }

    override fun onBind(intent: Intent): IBinder = Messenger(handler).binder

    // the model and the GPU driver's memory go back in one go
    override fun onDestroy() = Process.killProcess(Process.myPid())

    companion object {
        const val LISTEN = 1
        const val HEARD = 2
        const val PCM = "pcm"
        const val FROM = "from"
        const val TO = "to"
        const val JSON = "json"
        const val PROCESS = ":whisper"
        // the GPU does the heavy part; these run the speech detection and what the GPU can't
        private const val THREADS = 4
    }
}
