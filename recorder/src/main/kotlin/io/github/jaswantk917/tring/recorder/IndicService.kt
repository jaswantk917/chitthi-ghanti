package io.github.jaswantk917.tring.recorder

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import android.os.Process
import android.util.Log
import java.io.File
import kotlin.math.abs

/**
 * The clearer transcript of one call, written by [IndicEngine] in a process of its own (":indic")
 * that runs as a foreground service, so it gets the phone's fast cores and says so in a note with
 * a Stop. Stopping is ending the process, which nothing in the model can hold up. It reads the
 * call and its turns itself, sends each line back as it's heard, and saves the transcript.
 */
class IndicService : Service() {
    private val worker = HandlerThread("indic").apply { start() }
    private val handler = Handler(worker.looper) { message ->
        if (message.what == START) write(message.data, message.replyTo)
        true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            client?.let { tell(it, STOPPED) }
            Process.killProcess(Process.myPid())
            return START_NOT_STICKY
        }
        note(intent?.getLongExtra(EXTRA_ID, -1) ?: -1, null)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder = Messenger(handler).binder

    // the model and ONNX Runtime's memory go back in one go
    override fun onDestroy() = Process.killProcess(Process.myPid())

    @Volatile private var client: Messenger? = null

    private fun write(ask: Bundle, reply: Messenger) {
        client = reply
        val id = ask.getLong(EXTRA_ID)
        val notes = Notes(this)
        val pcm = File(cacheDir, "indic-decoded.pcm")
        try {
            Transcriber.decode(this, Uri.parse(ask.getString(EXTRA_URI)), pcm) ?: error("This recording can't be read")
            val callMs = (pcm.length() / Transcriber.BYTES_PER_MS).toInt()
            // the whole call, cut where it's quiet: a wrong turn would cut a sentence in two
            val pieces = IndicPieces.of(null, callMs) { from, to -> quietest(pcm, from, to) }
            val lines = ArrayList<Line>()
            val power = getSystemService(PowerManager::class.java)
            IndicEngine(IndicModel.dir(this)).use { engine ->
                pieces.forEachIndexed { i, piece ->
                    if (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) error("the phone got too warm, let it cool and try again")
                    note(id, Transcriber.Progress(i, pieces.size))
                    tell(reply, PROGRESS, Bundle().apply { putInt(DONE_COUNT, i); putInt(OF, pieces.size) })
                    val heard = engine.hear(Transcriber.read(pcm, piece.startMs, piece.endMs))
                    if (heard.text.isNotEmpty()) {
                        val line = Line(piece.them, piece.startMs, heard.text, heard.confidence)
                        lines += line
                        tell(reply, LINE, line.bundle())
                    }
                }
            }
            notes.saveTranscript(id, lines, clearer = true)
            tell(reply, FINISHED)
        } catch (e: Exception) {
            Log.w(TAG, "couldn't write the clearer transcript", e)
            tell(reply, FAILED, Bundle().apply { putString(MESSAGE, e.message ?: e.javaClass.simpleName) })
        } finally {
            pcm.delete()
        }
    }

    // the middle of the quietest 300 ms between two times, so a cut doesn't split a word
    private fun quietest(pcm: File, fromMs: Int, toMs: Int): Int {
        val samples = Transcriber.read(pcm, fromMs, toMs)
        val window = 16 * 300
        if (samples.size <= window) return (fromMs + toMs) / 2
        var sum = 0.0
        for (i in 0 until window) sum += abs(samples[i])
        var best = sum
        var at = 0
        for (i in window until samples.size) {
            sum += abs(samples[i]) - abs(samples[i - window])
            if (sum < best) {
                best = sum
                at = i - window + 1
            }
        }
        return fromMs + (at + window / 2) / 16
    }

    private fun tell(to: Messenger, what: Int, data: Bundle? = null) {
        runCatching { to.send(Message.obtain(null, what).apply { if (data != null) this.data = data }) }
    }

    private fun note(id: Long, progress: Transcriber.Progress?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Clearer transcripts", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, IndicService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, RecordingsActivity::class.java).putExtra(RecordingsActivity.EXTRA_OPEN, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val note = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Writing a clearer transcript")
            .setContentText(if (progress == null || progress.of == 0) "Getting the call ready…" else "${progress.done} of ${progress.of}")
            .setProgress(progress?.of ?: 0, progress?.done ?: 0, progress == null || progress.of == 0)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null as Icon?, "Stop", stop).build())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> startForeground(NOTE, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> startForeground(NOTE, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> startForeground(NOTE, note)
        }
    }

    /**
     * The app's side: starts [IndicService] for one call and hears back from it. [stop] ends its
     * process there and then; so does its own Stop, after which it says [onStopped].
     */
    class Run(
        private val context: Context,
        private val recording: Recording,
        private val onProgress: (Transcriber.Progress) -> Unit,
        private val onLine: (Line) -> Unit,
        private val onFinished: () -> Unit,
        private val onFailed: (String) -> Unit,
        private val onStopped: () -> Unit,
    ) {
        private var over = false
        private val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                PROGRESS -> onProgress(Transcriber.Progress(message.data.getInt(DONE_COUNT), message.data.getInt(OF)))
                LINE -> onLine(line(message.data))
                FINISHED -> end { onFinished() }
                FAILED -> end { onFailed(message.data.getString(MESSAGE).orEmpty()) }
                STOPPED -> end { onStopped() }
            }
            true
        })

        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val ask = Message.obtain(null, START).apply {
                    data = Bundle().apply {
                        putLong(EXTRA_ID, recording.id)
                        putString(EXTRA_URI, recording.uri.toString())
                    }
                    replyTo = replies
                }
                runCatching { Messenger(binder).send(ask) }.onFailure { end { onFailed("it didn't start") } }
            }

            // its own Stop says so first; going without a word is Android ending it
            override fun onServiceDisconnected(name: ComponentName) = end { onFailed(ENDED) }

            override fun onBindingDied(name: ComponentName) = end { onFailed(ENDED) }
        }

        fun start() {
            val intent = Intent(context, IndicService::class.java).putExtra(EXTRA_ID, recording.id)
            context.startForegroundService(intent)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        fun stop() = end { onStopped() }

        // unbound first, so the system doesn't bring the service back, then its process ended
        private fun end(then: () -> Unit) {
            if (over) return
            over = true
            runCatching { context.unbindService(connection) }
            context.getSystemService(ActivityManager::class.java).runningAppProcesses
                ?.filter { it.processName == context.packageName + PROCESS }
                ?.forEach { Process.killProcess(it.pid) }
            then()
        }
    }

    companion object {
        private const val TAG = "IndicService"
        private const val CHANNEL = "clearer"
        private const val NOTE = 5
        private const val PROCESS = ":indic"
        private const val ACTION_STOP = "io.github.jaswantk917.tring.recorder.STOP_CLEARER"
        private const val EXTRA_ID = "id"
        private const val EXTRA_URI = "uri"
        private const val START = 1
        private const val PROGRESS = 2
        private const val LINE = 3
        private const val FINISHED = 4
        private const val FAILED = 5
        private const val STOPPED = 6
        private const val DONE_COUNT = "done"
        private const val OF = "of"
        private const val MESSAGE = "message"
        private const val ENDED = "Android ended it, most likely short on memory. Close some apps and try again"

        private fun Line.bundle() = Bundle().apply {
            putInt("them", when (them) { true -> 1; false -> 0; null -> -1 })
            putInt("at", atMs)
            putString("text", text)
            putFloat("confidence", confidence)
        }

        private fun line(data: Bundle) = Line(
            when (data.getInt("them")) { 1 -> true; 0 -> false; else -> null },
            data.getInt("at"), data.getString("text").orEmpty(), data.getFloat("confidence"),
        )
    }
}
