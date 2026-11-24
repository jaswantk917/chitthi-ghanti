package io.github.jaswantk917.tring.recorder

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs as the adb shell through Shizuku, in its own process: the only part allowed to answer a
 * call silently and use the call's audio. It also guards the call: if the app dies or never
 * finishes, it hands the call back to ring.
 */
class ShellService : IScreenShell.Stub() {
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private var guard: Guard? = null
    private var line: CallLine? = null

    override fun destroy() {
        System.exit(0)
    }

    /** whether the call's audio can be opened at all, checked before any call is taken; records nothing */
    override fun audioReady(): Boolean = runCatching {
        val record = openRecord()
        val speaker = openSpeaker()
        val works = record.state == AudioRecord.STATE_INITIALIZED && speaker.state == AudioTrack.STATE_INITIALIZED
        record.release()
        speaker.release()
        works
    }.onFailure { Log.w(TAG, "call audio won't open", it) }.getOrDefault(false)

    override fun allowBeside(packageName: String) = runCatching {
        Runtime.getRuntime().exec(arrayOf("appops", "set", packageName, "MANAGE_ONGOING_CALLS", "allow")).waitFor() == 0
    }.getOrDefault(false)

    @Synchronized
    override fun enter(adapter: IBinder, callId: String, app: IBinder): Boolean {
        guard?.release()
        return try {
            val telecom = telecom(adapter)
            telecom.javaClass.getMethod("enterBackgroundAudioProcessing", String::class.java, Int::class.javaPrimitiveType)
                .invoke(telecom, callId, USE_CASE_SCREENING)
            guard = Guard(adapter, callId, app)
            true
        } catch (e: Exception) {
            Log.w(TAG, "couldn't answer silently", e)
            false
        }
    }

    @Synchronized
    override fun settled() {
        guard?.release()
        guard = null
    }

    @Synchronized
    override fun listen(greeting: ParcelFileDescriptor): ParcelFileDescriptor {
        line?.stop()
        val (read, write) = ParcelFileDescriptor.createPipe()
        line = CallLine(greeting, write)
        return read
    }

    @Synchronized
    override fun speak(pcm: ParcelFileDescriptor) {
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(pcm).use { it.readBytes() }
        line?.say(bytes)
    }

    @Synchronized
    override fun stopAudio() {
        line?.stop()
        line = null
    }

    private fun telecom(adapter: IBinder): Any =
        Class.forName("com.android.internal.telecom.IInCallAdapter\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, adapter)!!

    /** while a call is held silently: the app's death or the time limit puts it back to ringing */
    private inner class Guard(private val adapter: IBinder, private val callId: String, private val app: IBinder) : IBinder.DeathRecipient {
        private val limit: ScheduledFuture<*> = timer.schedule(Runnable { giveBack() }, LONGEST_MS, TimeUnit.MILLISECONDS)
        private var done = false

        init {
            runCatching { app.linkToDeath(this, 0) }.onFailure { giveBack() }
        }

        override fun binderDied() = giveBack()

        private fun giveBack() {
            synchronized(this@ShellService) {
                if (done) return
                release()
                guard = null
                line?.stop()
                line = null
                runCatching {
                    val telecom = telecom(adapter)
                    telecom.javaClass.getMethod("exitBackgroundAudioProcessing", String::class.java, Boolean::class.javaPrimitiveType)
                        .invoke(telecom, callId, true)
                }.onFailure { Log.w(TAG, "couldn't hand the call back", it) }
            }
        }

        fun release() {
            done = true
            limit.cancel(false)
            runCatching { app.unlinkToDeath(this, 0) }
        }
    }

    /** plays the greeting into the call, then sends what the caller says down [out] */
    private class CallLine(greeting: ParcelFileDescriptor, private val out: ParcelFileDescriptor) {
        @Volatile private var running = true
        @Volatile private var track: AudioTrack? = null

        init {
            thread(name = "call-line") {
                val voice = ParcelFileDescriptor.AutoCloseInputStream(greeting).use { it.readBytes() }
                try {
                    run(voice)
                } catch (e: Exception) {
                    Log.w(TAG, "call audio failed", e)
                } finally {
                    runCatching { out.close() }
                }
            }
        }

        fun stop() {
            running = false
        }

        fun say(pcm: ByteArray) {
            val speaker = track ?: return
            thread(name = "call-say") { runCatching { speaker.write(pcm, 0, pcm.size) } }
        }

        private fun run(voice: ByteArray) {
            val deadline = System.currentTimeMillis() + WAIT_MS
            while (mode() != MODE_CALL_SCREENING) {
                if (!running || System.currentTimeMillis() > deadline) return
                Thread.sleep(40)
            }
            Thread.sleep(SETTLE_MS)
            val record = openRecord()
            val speaker = openSpeaker()
            try {
                if (record.state != AudioRecord.STATE_INITIALIZED || speaker.state != AudioTrack.STATE_INITIALIZED) error("call audio didn't open")
                track = speaker
                record.startRecording()
                speaker.play()
                thread(name = "call-greet") { runCatching { speaker.write(voice, 0, voice.size) } }
                var skip = voice.size.toLong()
                val buffer = ByteArray(1_280)
                ParcelFileDescriptor.AutoCloseOutputStream(out).use { sink ->
                    var reads = 0
                    while (running && (reads++ % MODE_EVERY != 0 || mode() == MODE_CALL_SCREENING)) {
                        val n = record.read(buffer, 0, buffer.size)
                        if (n <= 0) break
                        if (skip >= n) {
                            skip -= n
                            continue
                        }
                        sink.write(buffer, skip.toInt(), n - skip.toInt())
                        sink.flush()
                        skip = 0
                    }
                }
            } finally {
                track = null
                runCatching { record.stop() }
                record.release()
                runCatching { speaker.stop() }
                speaker.release()
            }
        }

        private fun mode(): Int {
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "audio")
            val audio = Class.forName("android.media.IAudioService\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            return Class.forName("android.media.IAudioService").getMethod("getMode").invoke(audio) as Int
        }
    }

    private companion object {
        const val TAG = "ShellService"
        const val USE_CASE_SCREENING = 2
        const val MODE_CALL_SCREENING = 4
        const val USAGE_CALL_ASSISTANT = 17
        const val PRESET_VOICE_DOWNLINK = 3
        const val REDIRECT_PSTN = 1
        const val LONGEST_MS = 90_000L
        const val WAIT_MS = 6_000L
        const val SETTLE_MS = 300L
        const val MODE_EVERY = 12
        const val SHELL_PACKAGE = "com.android.shell"

        // Shizuku starts this process as "android" under the shell's uid, and audioserver refuses that pair
        val shellIdentity: Context by lazy {
            val thread = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null)!!
            val system = thread.javaClass.getMethod("getSystemContext").invoke(thread) as Context
            val shell = AttributionSource.Builder(Process.myUid()).setPid(Process.myPid()).setPackageName(SHELL_PACKAGE).build()
            object : ContextWrapper(system) {
                override fun getAttributionSource() = shell
            }
        }

        fun openSpeaker(): AudioTrack {
            val attributes = AudioAttributes.Builder()
            AudioAttributes.Builder::class.java.getMethod("setSystemUsage", Int::class.javaPrimitiveType).invoke(attributes, USAGE_CALL_ASSISTANT)
            attributes.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            val builder = AudioTrack.Builder().setContext(shellIdentity).setAudioAttributes(attributes.build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Pcm.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
            AudioTrack.Builder::class.java.getMethod("setCallRedirectionMode", Int::class.javaPrimitiveType).invoke(builder, REDIRECT_PSTN)
            return builder.build()
        }

        fun openRecord(): AudioRecord {
            val attributes = AudioAttributes.Builder()
            AudioAttributes.Builder::class.java.getMethod("setInternalCapturePreset", Int::class.javaPrimitiveType).invoke(attributes, PRESET_VOICE_DOWNLINK)
            val builder = AudioRecord.Builder().setContext(shellIdentity)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Pcm.RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            AudioRecord.Builder::class.java.getMethod("setAudioAttributes", AudioAttributes::class.java).invoke(builder, attributes.build())
            AudioRecord.Builder::class.java.getMethod("setCallRedirectionMode", Int::class.javaPrimitiveType).invoke(builder, REDIRECT_PSTN)
            return builder.build()
        }
    }
}
