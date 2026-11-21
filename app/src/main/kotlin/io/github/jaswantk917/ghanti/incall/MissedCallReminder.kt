package io.github.jaswantk917.ghanti.incall

import android.Manifest
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog.Calls
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.jaswantk917.chitthi.R
import io.github.jaswantk917.ghanti.MainActivity
import io.github.jaswantk917.ghanti.signals.DeliveryRules
import java.util.concurrent.TimeUnit

/**
 * A missed call swiped away comes back once, an hour later, unless Ghanti was opened or the person
 * was called back or talked to in between. In its own channel, so it can be turned off by itself.
 */
object MissedCallReminder {
    const val CHANNEL = "missed_reminder"
    const val DELAY_MINUTES = 60L
    private const val ID = 3
    private const val WORK = "missed-reminder"
    private const val ACTION_DISMISSED = "io.github.jaswantk917.ghanti.MISSED_DISMISSED"
    private const val EXTRA_CLEAR = "clear"
    private const val KEY_NUMBER = "number"
    private const val KEY_WHO = "who"
    private const val KEY_AT = "at"
    private const val KEY_COUNT = "count"

    /** a call after the miss; [reached] when it was outgoing (answered or not) or an answered incoming one */
    data class LaterCall(val at: Long, val number: String, val reached: Boolean)

    /** remind unless Ghanti was opened after the miss, or the person was called back or talked to since */
    fun due(missedAt: Long, number: String, seenAt: Long, later: List<LaterCall>): Boolean {
        if (seenAt >= missedAt) return false
        val key = DeliveryRules.last10(number)
        if (key.isEmpty()) return true
        return later.none { it.at > missedAt && it.reached && DeliveryRules.last10(it.number) == key }
    }

    /** the missed-call note's delete intent: Telecom's own clear, then the reminder */
    fun onDismiss(context: Context, number: String, who: String, count: Int, clear: PendingIntent?): PendingIntent = PendingIntent.getBroadcast(
        context, ID,
        Intent(context, Dismissed::class.java).setAction(ACTION_DISMISSED)
            .putExtra(KEY_NUMBER, number).putExtra(KEY_WHO, who).putExtra(KEY_COUNT, count)
            .putExtra(KEY_AT, System.currentTimeMillis()).putExtra(EXTRA_CLEAR, clear),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Ghanti was opened: whatever was missed has been seen */
    fun seen(context: Context) {
        prefs(context).edit().putLong(KEY_AT, System.currentTimeMillis()).apply()
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        NotificationManagerCompat.from(context).cancel(ID)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("ghanti_missed_reminder", Context.MODE_PRIVATE)

    class Dismissed : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION") // the typed getter is 13+ only
            intent.getParcelableExtra<PendingIntent>(EXTRA_CLEAR)?.let { runCatching { it.send() } }
            val request = OneTimeWorkRequestBuilder<Worker>()
                .setInitialDelay(DELAY_MINUTES, TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        KEY_NUMBER to intent.getStringExtra(KEY_NUMBER).orEmpty(),
                        KEY_WHO to intent.getStringExtra(KEY_WHO).orEmpty(),
                        KEY_COUNT to intent.getIntExtra(KEY_COUNT, 1),
                        KEY_AT to intent.getLongExtra(KEY_AT, System.currentTimeMillis()),
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val context = applicationContext
            val number = inputData.getString(KEY_NUMBER).orEmpty()
            val who = inputData.getString(KEY_WHO).orEmpty().ifBlank { number.ifBlank { "Private number" } }
            val count = inputData.getInt(KEY_COUNT, 1)
            val at = inputData.getLong(KEY_AT, 0L)
            // Play: the call log is read only while this is the phone app
            val isPhoneApp = context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)
            if (!isPhoneApp) return Result.success()
            if (!due(at, if (count > 1) "" else number, prefs(context).getLong(KEY_AT, 0L), laterCalls(context, at))) return Result.success()
            notify(context, number.takeIf { count == 1 }, if (count > 1) "$count missed calls" else who, at)
            return Result.success()
        }

        private fun laterCalls(context: Context, since: Long): List<LaterCall> {
            if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return emptyList()
            val calls = ArrayList<LaterCall>()
            context.contentResolver.query(
                Calls.CONTENT_URI, arrayOf(Calls.DATE, Calls.NUMBER, Calls.TYPE, Calls.DURATION),
                "${Calls.DATE} > ?", arrayOf(since.toString()), null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getInt(2)
                    val reached = type == Calls.OUTGOING_TYPE || (type == Calls.INCOMING_TYPE && cursor.getLong(3) > 0)
                    calls += LaterCall(cursor.getLong(0), cursor.getString(1).orEmpty(), reached)
                }
            }
            return calls
        }

        private fun notify(context: Context, number: String?, who: String, at: Long) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val open = PendingIntent.getActivity(
                context, ID, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ghanti_missed_notification)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentTitle(if (number == null) "$who to return" else "Call $who back?")
                .setContentText("Missed at ${DateFormat.getTimeFormat(context).format(at)}")
                .setContentIntent(open)
                .setAutoCancel(true)
            if (!number.isNullOrBlank()) {
                val tel = Uri.fromParts("tel", number, null)
                builder.addAction(0, "Call back", PendingIntent.getActivity(context, ID + 1, Intent(Intent.ACTION_CALL, tel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
                builder.addAction(0, "Message", PendingIntent.getActivity(context, ID + 2, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
            }
            NotificationManagerCompat.from(context).notify(ID, builder.build())
        }
    }
}
