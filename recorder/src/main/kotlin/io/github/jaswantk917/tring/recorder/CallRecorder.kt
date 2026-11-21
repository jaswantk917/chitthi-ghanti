package io.github.jaswantk917.tring.recorder

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Records the microphone while Tring says a call is on. Android gives the microphone to the call
 * alone, except to an Accessibility service; that is the only reason this is one. It reads no
 * screen content. Your voice comes through clearly; the other side only as loud as the earpiece or
 * speaker is near the microphone, since only system apps may record the call line itself.
 */
class CallRecorder : AccessibilityService() {
    private var recorder: Capture? = null
    private var output: ParcelFileDescriptor? = null
    private var saved: Uri? = null
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val callEnded = AudioManager.OnModeChangedListener { mode -> if (mode == AudioManager.MODE_NORMAL) stop() }

    private val commands = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra(EXTRA_ON, false)) start(intent.getStringExtra(EXTRA_WHO).orEmpty()) else stop()
        }
    }

    override fun onServiceConnected() {
        // Tring is another app, so the receiver must be exported; it only records while a call holds the audio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commands, IntentFilter(ACTION_RECORD), RECEIVER_EXPORTED)
        } else {
            registerReceiver(commands, IntentFilter(ACTION_RECORD))
        }
    }

    override fun onDestroy() {
        stop()
        runCatching { unregisterReceiver(commands) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun start(who: String) {
        if (recorder != null) return report(STATE_RECORDING)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return report(STATE_NEEDS_SETUP)
        if (audio.mode != AudioManager.MODE_IN_CALL && audio.mode != AudioManager.MODE_IN_COMMUNICATION) return report(STATE_OFF)
        val stamp = SimpleDateFormat("d MMM, h.mm a", Locale.getDefault()).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${who.ifBlank { "Call" }} · $stamp.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, folder())
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        try {
            val uri = contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return report(STATE_OFF)
            saved = uri
            output = contentResolver.openFileDescriptor(uri, "w")
            val capture = Capture(File(cacheDir, "call.pcm"))
            if (!capture.start()) {
                release(keep = false)
                return report(STATE_OFF)
            }
            recorder = capture
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.addOnModeChangedListener(mainExecutor, callEnded)
            report(STATE_RECORDING)
        } catch (e: Exception) {
            Log.w(TAG, "couldn't start recording", e)
            release(keep = false)
            report(STATE_OFF)
        }
    }

    private fun stop() {
        val capture = recorder ?: return
        recorder = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.removeOnModeChangedListener(callEnded)
        report(STATE_OFF)
        // the mixdown reads the whole call back, seconds of work, so it runs off the main thread
        thread(name = "mixdown") {
            // a call ended before any sound came through leaves nothing worth keeping
            val kept = output?.let { capture.finish(it.fileDescriptor) } == true
            release(keep = kept)
        }
    }

    private fun release(keep: Boolean) {
        recorder = null
        runCatching { output?.close() }
        output = null
        val uri = saved ?: return
        saved = null
        if (keep) {
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            notifySaved(uri)
        } else {
            runCatching { contentResolver.delete(uri, null, null) }
        }
    }

    private fun notifySaved(uri: Uri) {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Saved recordings", NotificationManager.IMPORTANCE_LOW))
        val play = PendingIntent.getActivity(
            this, uri.hashCode(),
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "audio/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            uri.hashCode(),
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Call recorded")
                .setContentText("In ${folder()} · tap to play")
                .setContentIntent(play)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun report(state: String) {
        TRING.forEach { sendBroadcast(Intent(ACTION_STATE).setPackage(it).putExtra(EXTRA_STATE, state)) }
    }

    private fun folder() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Environment.DIRECTORY_RECORDINGS}/Tring" else "${Environment.DIRECTORY_MUSIC}/Tring recordings"

    companion object {
        private const val TAG = "CallRecorder"
        private const val CHANNEL = "saved"
        // the contract with Tring's Ghanti; Recorder.kt there holds the same strings
        const val ACTION_RECORD = "io.github.jaswantk917.tring.recorder.RECORD"
        const val ACTION_STATE = "io.github.jaswantk917.tring.RECORDING"
        const val EXTRA_ON = "on"
        const val EXTRA_WHO = "who"
        const val EXTRA_STATE = "state"
        const val STATE_RECORDING = "recording"
        const val STATE_OFF = "off"
        const val STATE_NEEDS_SETUP = "needs_setup"
        private val TRING = listOf("io.github.jaswantk917.chitthighanti", "io.github.jaswantk917.chitthighanti.debug")
    }
}
