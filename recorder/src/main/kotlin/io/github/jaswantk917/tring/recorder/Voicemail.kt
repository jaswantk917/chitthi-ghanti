package io.github.jaswantk917.tring.recorder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a caller said, screened or let ring, kept like a recording, with a note saying who and what. */
object Voicemail {
    fun save(context: Context, who: String, pcm: ByteArray, words: String) {
        val resolver = context.contentResolver
        val stamp = SimpleDateFormat("d MMM, h.mm a", Locale.getDefault()).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$who · $stamp.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Recordings.folder())
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return
        val wrote = try {
            resolver.openFileDescriptor(uri, "w")?.use { file ->
                val encoder = Capture.Encoder(file.fileDescriptor)
                val samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val frame = ShortArray(Mixdown.FRAME)
                while (samples.hasRemaining()) {
                    frame.fill(0)
                    samples.get(frame, 0, minOf(frame.size, samples.remaining()))
                    encoder.write(frame)
                }
                encoder.finish()
            } == true
        } catch (e: Exception) {
            android.util.Log.w("Voicemail", "couldn't encode the message", e)
            false
        }
        if (!wrote) {
            resolver.delete(uri, null, null)
            return
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        val id = ContentUris.parseId(uri)
        Notes(context).apply {
            // no words: the transcriber has another go at the sound when the app opens
            if (words.isNotBlank()) saveTranscript(id, listOf(Line(null, 0, words)))
            markMessage(id)
        }
        RecordingsProvider.changed(context)
        notify(context, id, who, words)
    }

    private fun notify(context: Context, id: Long, who: String, words: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Messages from callers", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, RecordingsActivity::class.java).putExtra(RecordingsActivity.EXTRA_OPEN, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            id.hashCode(),
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Message from $who")
                .setContentText(words.ifBlank { "Tap to listen" })
                .setStyle(Notification.BigTextStyle().bigText(words.ifBlank { "Tap to listen" }))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private const val CHANNEL = "messages"
}
