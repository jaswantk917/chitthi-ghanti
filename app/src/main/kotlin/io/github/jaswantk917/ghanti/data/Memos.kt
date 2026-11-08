package io.github.jaswantk917.ghanti.data

import android.content.Context
import io.github.jaswantk917.ghanti.signals.DeliveryRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/** What you added to one call: who it turned out to be, and a note. */
data class Memo(val number: String, val at: Long, val label: String?, val note: String)

/**
 * Pure matching. Telecom dates the call log entry by the call's creation time, the same clock the
 * call screen keys on, so a tight window keeps a rider's two quick calls apart.
 */
object MemoList {
    const val MATCH_MS = 10_000L

    fun match(memos: List<Memo>, number: String, at: Long): Memo? {
        val key = DeliveryRules.last10(number)
        return memos.filter { it.number == key && abs(it.at - at) < MATCH_MS }.minByOrNull { abs(it.at - at) }
    }

    /** who this number turned out to be on an earlier call */
    fun previousLabel(memos: List<Memo>, number: String, at: Long): String? {
        val key = DeliveryRules.last10(number)
        return memos.filter { it.number == key && it.label != null && it.at < at - MATCH_MS }.maxByOrNull { it.at }?.label
    }

    /** replaces the memo for that call, or drops it once both fields are empty */
    fun save(memos: List<Memo>, number: String, at: Long, label: String?, note: String): List<Memo> {
        val existing = match(memos, number, at)
        val rest = memos - listOfNotNull(existing).toSet()
        val cleanLabel = label?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanLabel == null && note.isBlank()) return rest
        return rest + Memo(DeliveryRules.last10(number), existing?.at ?: at, cleanLabel, note)
    }
}

class Memos(context: Context) {
    private val prefs = context.getSharedPreferences("ghanti_memos", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val all: StateFlow<List<Memo>> = state

    fun save(number: String, at: Long, label: String?, note: String) {
        state.update { MemoList.save(it, number, at, label, note).sortedBy { m -> m.at }.takeLast(MAX) }
        prefs.edit().putStringSet(KEY, state.value.map { listOf(it.number, it.at, it.label.orEmpty(), it.note).joinToString(SEP) }.toSet()).apply()
    }

    private fun load(): List<Memo> = prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { line ->
        val p = line.split(SEP)
        if (p.size != 4) return@mapNotNull null
        Memo(p[0], p[1].toLongOrNull() ?: return@mapNotNull null, p[2].ifEmpty { null }, p[3])
    }.sortedBy { it.at }

    private companion object {
        const val KEY = "memos"
        const val SEP = "\u0001"
        const val MAX = 1000
    }
}
