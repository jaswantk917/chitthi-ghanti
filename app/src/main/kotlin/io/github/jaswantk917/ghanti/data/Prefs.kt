package io.github.jaswantk917.ghanti.data

import android.content.Context
import android.os.Build
import io.github.jaswantk917.ghanti.signals.DeliveryRules
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
    /** which calls Tring Recorder starts on by itself once they connect */
    val autoRecord: AutoRecord = AutoRecord.Off,
)

enum class AutoRecord {
    Off, Chosen, Strangers, Everyone;

    /** [chosen]: the last 10 digits of the numbers you switched on, for [Chosen] */
    fun wants(number: String, isContact: Boolean, chosen: Set<String>) = when (this) {
        Off -> false
        Chosen -> number.isNotBlank() && DeliveryRules.last10(number) in chosen
        Strangers -> !isContact
        Everyone -> true
    }
}

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("ghanti_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<Settings> = state

    /** contact id to the number a favourite's tap calls, when they have more than one */
    private val usualPrefs = context.getSharedPreferences("ghanti_usual_numbers", Context.MODE_PRIVATE)
    private val usualState = MutableStateFlow(usualPrefs.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> (v as? String)?.let { id to it } } }.toMap())
    val usual: StateFlow<Map<Long, String>> = usualState

    /** the last 10 digits of each number whose calls record by themselves, when [AutoRecord.Chosen] */
    private val recordPrefs = context.getSharedPreferences("ghanti_record_numbers", Context.MODE_PRIVATE)
    private val recordState = MutableStateFlow(recordPrefs.getStringSet("numbers", null).orEmpty().toSet())
    val recordNumbers: StateFlow<Set<String>> = recordState

    fun setRecord(numbers: List<String>, on: Boolean) {
        val keys = numbers.map(DeliveryRules::last10).filter { it.isNotEmpty() }
        val next = if (on) recordState.value + keys else recordState.value - keys.toSet()
        recordPrefs.edit().putStringSet("numbers", next).apply()
        recordState.value = next
    }

    /** the last 10 digits of each number you said not to suggest above Recents */
    private val hiddenFacePrefs = context.getSharedPreferences("ghanti_hidden_faces", Context.MODE_PRIVATE)
    private val hiddenFaceState = MutableStateFlow(hiddenFacePrefs.getStringSet("numbers", null).orEmpty().toSet())
    val hiddenFaces: StateFlow<Set<String>> = hiddenFaceState

    fun setFaceHidden(number: String, hidden: Boolean) {
        val key = DeliveryRules.last10(number)
        val next = if (hidden) hiddenFaceState.value + key else hiddenFaceState.value - key
        hiddenFacePrefs.edit().putStringSet("numbers", next).apply()
        hiddenFaceState.value = next
    }

    /** "expecting a new number": strangers ring out loud until [until]; [hours] is the length you picked */
    data class StrangersRing(val until: Long = 0L, val hours: Int = 0) {
        fun on(now: Long) = until > now
    }

    private val strangersState = MutableStateFlow(StrangersRing(prefs.getLong("strangers_ring_until", 0L), prefs.getInt("strangers_ring_hours", 0)))
    val strangersRing: StateFlow<StrangersRing> = strangersState

    fun setStrangersRing(next: StrangersRing) {
        prefs.edit().putLong("strangers_ring_until", next.until).putInt("strangers_ring_hours", next.hours).apply()
        strangersState.value = next
    }

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
        autoRecord = AutoRecord.entries.firstOrNull { it.name == prefs.getString("auto_record", null) } ?: AutoRecord.Off,
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
            .putString("auto_record", next.autoRecord.name)
            .apply()
        state.value = next
    }
}
