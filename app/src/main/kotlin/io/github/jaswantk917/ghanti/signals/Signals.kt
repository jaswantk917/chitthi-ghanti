package io.github.jaswantk917.ghanti.signals

import android.content.Context
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Delivery hints seen lately. Notifications are kept as they arrive (they may be dismissed before
 * the call comes); SMS are read fresh from the inbox whenever asked.
 */
class Signals(private val context: Context) {
    private val prefs = context.getSharedPreferences("ghanti_signals", Context.MODE_PRIVATE)
    private val seen = MutableStateFlow(load())
    /** ticks on every change, for screens showing what's on its way */
    val changes: StateFlow<List<Signal>> = seen

    fun record(signal: Signal) {
        seen.update { list ->
            // a progress update of the same order replaces the last one, keeping any number it named
            val same = list.lastOrNull { it.brand == signal.brand && it.strength == signal.strength }
            val merged = if (same != null) signal.copy(numbers = same.numbers + signal.numbers) else signal
            (list - setOfNotNull(same) + merged).filter { merged.at - it.at < KEEP_MS }
        }
        save(seen.value)
    }

    /** everything that could explain a call right now */
    fun current(now: Long = System.currentTimeMillis()): List<Signal> = seen.value + fromSms(now)

    private fun fromSms(now: Long): List<Signal> = try {
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?", arrayOf((now - KEEP_MS).toString()), null,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    DeliveryRules.fromSms(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getLong(2))?.let(::add)
                }
            }
        }.orEmpty()
    } catch (e: SecurityException) {
        Log.w(TAG, "no SMS access", e)
        emptyList()
    }

    private fun load(): List<Signal> = prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { line ->
        val parts = line.split(SEP)
        if (parts.size != 4) return@mapNotNull null
        Signal(
            brand = parts[0],
            strength = Strength.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null,
            at = parts[2].toLongOrNull() ?: return@mapNotNull null,
            numbers = parts[3].split(',').filter { it.isNotEmpty() }.toSet(),
        )
    }.sortedBy { it.at }

    private fun save(list: List<Signal>) {
        prefs.edit().putStringSet(KEY, list.map { listOf(it.brand, it.strength.name, it.at, it.numbers.joinToString(",")).joinToString(SEP) }.toSet()).apply()
    }

    private companion object {
        const val TAG = "Signals"
        const val KEY = "seen"
        const val SEP = "\u0001"
        const val KEEP_MS = 24 * 3_600_000L
    }
}
