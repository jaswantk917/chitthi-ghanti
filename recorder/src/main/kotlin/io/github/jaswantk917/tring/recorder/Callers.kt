package io.github.jaswantk917.tring.recorder

import android.content.Context
import android.telephony.TelephonyManager

/**
 * Who a call was with, as Tring knows them. Tring tells this app a stranger's likely label as the
 * call comes in ("Swiggy delivery?") and the one you confirm later ("Swiggy delivery"); each
 * recording and message keeps its number, so a later confirmation renames the older ones too.
 * Numbers from your own country read without the country code.
 */
object Callers {
    private fun prefs(context: Context) = context.getSharedPreferences("callers", Context.MODE_PRIVATE)

    /** Tring: what this stranger is; a label you confirmed isn't replaced by a guess */
    fun describe(context: Context, number: String, label: String, confirmed: Boolean) {
        val key = Screening.last10(number)
        if (key.length < 10 || label.isBlank()) return
        val prefs = prefs(context)
        if (!keeps(prefs.getBoolean("confirmed:$key", false), confirmed)) return
        prefs.edit().putString("label:$key", label).putBoolean("confirmed:$key", confirmed).apply()
    }

    /** whether a new label replaces the one kept: anything replaces a guess, only a confirmation replaces a confirmation */
    internal fun keeps(hadConfirmed: Boolean, confirmed: Boolean) = confirmed || !hadConfirmed

    /** which number a recording or message was with, and whether they're in your contacts */
    fun remember(context: Context, id: Long, number: String, saved: Boolean) {
        if (number.isBlank()) return
        prefs(context).edit().putString("call:$id", (if (saved) "1" else "0") + number).apply()
    }

    /** the name to show for a recording or message saved as [who] */
    fun name(context: Context, id: Long, who: String): String {
        val prefs = prefs(context)
        val call = prefs.getString("call:$id", null)
        val number = call?.drop(1) ?: who.takeIf(::isNumber)
        val label = number?.let { prefs.getString("label:${Screening.last10(it)}", null) }
        return pick(who, saved = call?.startsWith("1") == true, label = label, home = home(context))
    }

    /** the name for a call happening now: the contact's, else what Tring said they are, else their number */
    fun live(context: Context, number: String, contactName: String?): String {
        if (!contactName.isNullOrBlank()) return contactName
        if (number.isBlank()) return "Private number"
        val label = prefs(context).getString("label:${Screening.last10(number)}", null)
        return pick(number, saved = false, label = label, home = home(context))
    }

    /** a saved contact keeps their name; a stranger shows Tring's label, else their number written the local way */
    internal fun pick(who: String, saved: Boolean, label: String?, home: String?): String =
        label?.takeUnless { saved } ?: show(who, home)

    /** "+91 98765 43210" reads "98765 43210" when you're in India; a foreign number keeps its code; a name stays as it is */
    internal fun show(who: String, home: String?): String {
        if (!isNumber(who)) return who
        val digits = who.filter(Char::isDigit)
        val national = when {
            home == null -> null
            who.trimStart().startsWith("+") -> digits.removePrefix(home).takeIf { digits.startsWith(home) && it.length == 10 }
            digits.length == 10 -> digits
            digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
            digits.length == 12 && digits.startsWith(home) -> digits.drop(home.length)
            else -> null
        }
        return national?.let { "${it.take(5)} ${it.drop(5)}" } ?: who
    }

    internal fun isNumber(who: String) = who.isNotBlank() && who.all { it.isDigit() || it in "+ ()-" } && who.count(Char::isDigit) >= 6

    // only India is written the local way for now: that's where Tring is used
    private fun home(context: Context): String? {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val country = telephony?.simCountryIso?.ifBlank { null } ?: telephony?.networkCountryIso
        return if (country.equals("in", ignoreCase = true)) "91" else null
    }
}
