package io.github.jaswantk917.ghanti.signals

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.jaswantk917.chitthi.R
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.ghanti.MainActivity
import io.github.jaswantk917.ghanti.data.Prefs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * "Expecting a new number": for a while, strangers ring out loud like everyone else. Once a new
 * number has rung, a note after the call asks whether to stop now; when the time is up, a note says
 * strangers ring silently again.
 */
object StrangersRing {
    const val CHANNEL = "strangers_ring"
    /** the lengths offered, in hours, and how they read */
    val choices = listOf(1 to "1 hr", 4 to "4 hr", 8 to "8 hr", 24 to "1 day", 48 to "2 days")
    private const val HOUR_MS = 60 * 60_000L
    private const val WORK = "strangers-ring"
    private const val ID_ASK = 5
    private const val ID_ENDED = 6
    private const val ACTION_STOP = "io.github.jaswantk917.ghanti.STRANGERS_STOP"
    private const val ACTION_KEEP = "io.github.jaswantk917.ghanti.STRANGERS_KEEP"

    // the last call that rang only because of this: its last 10 digits ("" for a private number)
    @Volatile private var rang: String? = null

    fun start(context: Context, hours: Int, now: Long = System.currentTimeMillis()) {
        GhantiApp.instance.prefs.setStrangersRing(Prefs.StrangersRing(now + hours * HOUR_MS, hours))
        val ends = OneTimeWorkRequestBuilder<Ended>().setInitialDelay(hours * HOUR_MS, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, ends)
        NotificationManagerCompat.from(context).apply { cancel(ID_ASK); cancel(ID_ENDED) }
    }

    fun stop(context: Context) {
        GhantiApp.instance.prefs.setStrangersRing(Prefs.StrangersRing())
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        NotificationManagerCompat.from(context).cancel(ID_ASK)
    }

    /** the screener let a stranger ring because of this */
    fun rang(number: String) {
        rang = DeliveryRules.last10(number)
    }

    /** whether a call that just ended is the one that rang because of this, while it's still on */
    internal fun shouldAsk(rang: String?, number: String, window: Prefs.StrangersRing, now: Long) =
        rang != null && rang == DeliveryRules.last10(number) && window.on(now)

    fun callEnded(context: Context, number: String) {
        val window = GhantiApp.instance.prefs.strangersRing.value
        val now = System.currentTimeMillis()
        if (!shouldAsk(rang, number, window, now)) return
        rang = null
        notify(
            context, ID_ASK,
            NotificationCompat.Builder(context, CHANNEL)
                .setContentTitle("A new number called")
                .setContentText("Strangers ring out loud ${until(window.until, now)}. Silence them again?")
                .addAction(0, "Silence strangers", receiver(context, ACTION_STOP))
                .addAction(0, "Keep ringing", receiver(context, ACTION_KEEP)),
        )
    }

    /** "until 6:30 pm", "until tomorrow 9:10 am", "until Sat 9:10 am" */
    fun until(until: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val end = Instant.ofEpochMilli(until).atZone(zone)
        val time = end.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)).lowercase()
        return when (ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), end.toLocalDate())) {
            0L -> "until $time"
            1L -> "until tomorrow $time"
            else -> "until ${end.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))} $time"
        }
    }

    /** the header's one line, short enough for 360 dp: "Strangers ring till 6:30 pm", "… till tomorrow", "… till Sat" */
    fun status(until: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val end = Instant.ofEpochMilli(until).atZone(zone)
        return "Strangers ring till " + when (ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), end.toLocalDate())) {
            0L -> end.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)).lowercase()
            1L -> "tomorrow"
            else -> end.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
        }
    }

    private fun receiver(context: Context, action: String) = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, Action::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            id, builder.setSmallIcon(R.drawable.ghanti_notification).setContentIntent(open).setAutoCancel(true).build(),
        )
    }

    /** the two buttons on the note after a new number called */
    class Action : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_STOP -> stop(context)
                ACTION_KEEP -> NotificationManagerCompat.from(context).cancel(ID_ASK)
            }
        }
    }

    /** the time is up: strangers go back to ringing silently, and a note says so */
    class Ended(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val prefs = GhantiApp.instance.prefs
            val window = prefs.strangersRing.value
            // already stopped by hand, or started again for longer (that run has its own end)
            if (window.until == 0L || window.on(System.currentTimeMillis() + 60_000L)) return Result.success()
            prefs.setStrangersRing(Prefs.StrangersRing())
            NotificationManagerCompat.from(applicationContext).cancel(ID_ASK)
            notify(
                applicationContext, ID_ENDED,
                NotificationCompat.Builder(applicationContext, CHANNEL)
                    .setContentTitle("Strangers ring silently again")
                    .setContentText("The time you set for a new number is up"),
            )
            return Result.success()
        }
    }
}
