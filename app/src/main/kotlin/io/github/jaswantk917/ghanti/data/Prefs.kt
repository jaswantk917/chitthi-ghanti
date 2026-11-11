package io.github.jaswantk917.ghanti.data

import android.content.Context
import android.os.Build
import io.github.jaswantk917.ghanti.signals.ScreenSettings
import io.github.jaswantk917.ghanti.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val screen: ScreenSettings = ScreenSettings(),
    /** progressive blur under the bars; heavy for Mali GPUs, so on by default only on Pixels */
    val blur: Boolean = Build.MANUFACTURER.equals("Google", ignoreCase = true),
    val palette: Palette = Palette.Ghanti,
    val amoled: Boolean = false,
    /** a firm pulse when the other side picks up, a soft one when the call ends */
    val callBuzz: Boolean = true,
)

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("ghanti_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<Settings> = state

    /** contact id to the number a favourite's tap calls, when they have more than one */
    private val usualPrefs = context.getSharedPreferences("ghanti_usual_numbers", Context.MODE_PRIVATE)
    private val usualState = MutableStateFlow(usualPrefs.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> (v as? String)?.let { id to it } } }.toMap())
    val usual: StateFlow<Map<Long, String>> = usualState

    fun setUsual(contactId: Long, number: String?) {
        usualPrefs.edit().apply { if (number == null) remove(contactId.toString()) else putString(contactId.toString(), number) }.apply()
        usualState.value = if (number == null) usualState.value - contactId else usualState.value + (contactId to number)
    }

    private fun read() = Settings(
        screen = ScreenSettings(
            silenceUnknown = prefs.getBoolean("silence_unknown", true),
            letDeliveries = prefs.getBoolean("let_deliveries", true),
            repeatCallers = prefs.getBoolean("repeat_callers", true),
        ),
        blur = prefs.getBoolean("blur", Settings().blur),
        palette = Palette.entries.firstOrNull { it.name == prefs.getString("palette", null) } ?: Palette.Ghanti,
        amoled = prefs.getBoolean("amoled", false),
        callBuzz = prefs.getBoolean("call_buzz", true),
    )

    fun update(change: (Settings) -> Settings) {
        val next = change(state.value)
        prefs.edit()
            .putBoolean("silence_unknown", next.screen.silenceUnknown)
            .putBoolean("let_deliveries", next.screen.letDeliveries)
            .putBoolean("repeat_callers", next.screen.repeatCallers)
            .putBoolean("blur", next.blur)
            .putString("palette", next.palette.name)
            .putBoolean("amoled", next.amoled)
            .putBoolean("call_buzz", next.callBuzz)
            .apply()
        state.value = next
    }
}
