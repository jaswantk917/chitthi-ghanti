package io.github.jaswantk917.tring.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class Release(val version: String, val notes: String, val apkUrl: String?, val pageUrl: String)

/**
 * The recorder isn't on Play, so nothing updates it: once a day it asks GitHub for the newest
 * release, and says so once when there is one. This is the app's only use of the internet.
 */
object Updates {
    const val REPO = "https://github.com/jaswantk917/tring-recorder"
    private const val LATEST = "https://api.github.com/repos/jaswantk917/tring-recorder/releases/latest"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION = 2

    fun parse(json: String): Release? = runCatching {
        val release = JSONObject(json)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
        val assets = release.optJSONArray("assets")
        val apk = assets?.let { list ->
            (0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url")
        }
        Release(
            version = release.getString("tag_name").removePrefix("v"),
            notes = release.optString("body").trim(),
            apkUrl = apk?.takeIf { it.isNotEmpty() },
            pageUrl = release.optString("html_url").ifEmpty { "$REPO/releases/latest" },
        )
    }.getOrNull()

    /** "1.10" is newer than "1.9"; anything that isn't a version is never newer */
    fun newer(version: String, than: String): Boolean {
        val a = version.split('.').map { it.toIntOrNull() ?: return false }
        val b = than.split('.').map { it.toIntOrNull() ?: return false }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun installed(context: Context): String = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** a newer version found by an earlier check, or null */
    fun available(context: Context): Release? {
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val version = prefs.getString("version", null) ?: return null
        if (!newer(version, installed(context))) return null
        return Release(version, prefs.getString("notes", "").orEmpty(), prefs.getString("apk", null), prefs.getString("page", null) ?: "$REPO/releases/latest")
    }

    suspend fun check(context: Context): Release? = withContext(Dispatchers.IO) {
        val connection = URL(LATEST).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "TringRecorder")
            if (connection.responseCode != 200) return@withContext null
            val release = parse(connection.inputStream.bufferedReader().readText()) ?: return@withContext null
            context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit()
                .putString("version", release.version).putString("notes", release.notes)
                .putString("apk", release.apkUrl).putString("page", release.pageUrl)
                .apply()
            release.takeIf { newer(it.version, installed(context)) }
        } finally {
            connection.disconnect()
        }
    }

    /** where a tap on Update goes: the APK itself, so the browser downloads it straight away */
    fun download(release: Release): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl ?: release.pageUrl))

    fun notifyOnce(context: Context, release: Release) {
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        if (prefs.getString("told", null) == release.version) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "New versions", NotificationManager.IMPORTANCE_DEFAULT))
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, RecordingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val get = PendingIntent.getActivity(context, 1, download(release).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(
            NOTIFICATION,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Tring Recorder ${release.version} is out")
                .setContentText(release.notes.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.removePrefix("- ") ?: "Tap to see what's new")
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(null, "Download", get).build())
                .setAutoCancel(true)
                .build(),
        )
        prefs.edit().putString("told", release.version).apply()
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            Updates.check(applicationContext)?.let { Updates.notifyOnce(applicationContext, it) }
        } catch (e: Exception) {
            Log.w("Updates", "update check failed", e)
        }
        return Result.success()
    }
}
