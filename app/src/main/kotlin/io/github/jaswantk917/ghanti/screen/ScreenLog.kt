package io.github.jaswantk917.ghanti.screen

import android.content.Context
import io.github.jaswantk917.ghanti.signals.DeliveryRules
import io.github.jaswantk917.ghanti.signals.Guess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What Ghanti decided for a stranger's call, so recents and notifications can say why. */
data class Outcome(val number: String, val at: Long, val rang: Boolean, val guess: Guess?, val repeat: Boolean = false)

class ScreenLog(context: Context) {
    private val prefs = context.getSharedPreferences("ghanti_screen_log", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val outcomes: StateFlow<List<Outcome>> = state

    fun add(outcome: Outcome) {
        state.update { (it + outcome).sortedBy { o -> o.at }.takeLast(MAX) }
        prefs.edit().putStringSet(KEY, state.value.map(::encode).toSet()).apply()
    }

    fun lastSilenced(number: String): Long? {
        val key = DeliveryRules.last10(number)
        return state.value.lastOrNull { !it.rang && it.number == key }?.at
    }

    /** the decision for a call that started at [callAt], matched loosely since the call log's clock is a little later */
    fun forCall(number: String, callAt: Long): Outcome? {
        val key = DeliveryRules.last10(number)
        return state.value.lastOrNull { it.number == key && kotlin.math.abs(it.at - callAt) < MATCH_MS }
    }

    private fun encode(o: Outcome) = listOf(
        o.number, o.at, o.rang, o.repeat, o.guess?.title.orEmpty(), o.guess?.detail.orEmpty(), o.guess?.confident ?: false, o.guess?.ride ?: false,
        o.guess?.brands.orEmpty().joinToString(","),
    ).joinToString(SEP)

    private fun load(): List<Outcome> = prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { line ->
        val p = line.split(SEP)
        // older entries have no brands field
        if (p.size !in 8..9) return@mapNotNull null
        val brands = p.getOrNull(8)?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
        val guess = p[4].takeIf { it.isNotEmpty() }?.let { Guess(it, p[5].ifEmpty { null }, p[6].toBoolean(), p[7].toBoolean(), brands) }
        Outcome(p[0], p[1].toLongOrNull() ?: return@mapNotNull null, p[2].toBoolean(), guess, p[3].toBoolean())
    }.sortedBy { it.at }

    private companion object {
        const val KEY = "outcomes"
        const val SEP = "\u0001"
        const val MAX = 300
        const val MATCH_MS = 2 * 60_000L
    }
}
