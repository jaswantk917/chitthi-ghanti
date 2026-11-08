package io.github.jaswantk917.ghanti.signals

import java.time.Instant
import java.time.ZoneId

/** Live: someone is on the way right now. Today: something arrives today. Done: it arrived or was cancelled. */
enum class Strength { Live, Today, Done }

/** A hint, from a notification or an SMS, that a stranger may call about an order or a ride. */
data class Signal(
    val brand: String,
    val strength: Strength,
    val at: Long,
    /** phone numbers named in the text, as their last 10 digits */
    val numbers: Set<String> = emptySet(),
)

data class Guess(
    /** "Probably Bistro delivery", "Uber driver" */
    val title: String,
    /** the other candidates when it can't tell which, "Swiggy or Zepto" */
    val detail: String? = null,
    /** the caller's number was in the message itself */
    val confident: Boolean = false,
    val ride: Boolean = false,
    /** the companies it could be, for "it was Ownly" */
    val brands: List<String> = emptyList(),
)

data class ScreenSettings(
    val silenceUnknown: Boolean = true,
    val letDeliveries: Boolean = true,
    val repeatCallers: Boolean = true,
)

data class Decision(val ring: Boolean, val guess: Guess? = null, val repeat: Boolean = false)

object DeliveryRules {
    const val LIVE_WINDOW_MS = 90 * 60_000L
    const val REPEAT_WINDOW_MS = 3 * 60_000L

    private val rideBrands = setOf("Uber", "Ola", "Rapido", "BluSmart", "Namma Yatri", "inDrive")

    /** checked in order, so a more specific name wins over one it contains */
    private val brands = listOf(
        "Bistro", "Swish", "Ownly", "Zomato", "Instamart", "Swiggy", "Blinkit", "Zepto", "bigbasket", "BBnow",
        "Dunzo", "Uber", "Rapido", "Ola", "BluSmart", "Namma Yatri", "inDrive", "Porter",
        "Amazon", "Flipkart", "Ekart", "Myntra", "Meesho", "Ajio", "Nykaa", "JioMart", "Tata 1mg", "PharmEasy",
        "Apollo", "Licious", "FreshToHome", "Domino's", "Pizza Hut", "KFC", "McDonald's", "EatSure", "magicpin",
        "Delhivery", "Blue Dart", "Shadowfax", "XpressBees", "DTDC", "Ecom Express", "India Post", "Shiprocket",
        "Lenskart", "Croma", "Urban Company",
    )

    /** what DLT SMS headers call them; the part after "AX-" */
    private val headers = mapOf(
        "AMAZON" to "Amazon", "AMAZNI" to "Amazon", "FLPKRT" to "Flipkart", "FKRTIN" to "Flipkart", "EKARTL" to "Ekart",
        "DLHVRY" to "Delhivery", "BLUDRT" to "Blue Dart", "BLUEDT" to "Blue Dart", "SHDWFX" to "Shadowfax",
        "XPRESB" to "XpressBees", "DTDCIN" to "DTDC", "ECOMEX" to "Ecom Express", "SWIGGY" to "Swiggy",
        "ZOMATO" to "Zomato", "BLINKT" to "Blinkit", "ZEPTON" to "Zepto", "MYNTRA" to "Myntra", "MEESHO" to "Meesho",
        "BGBSKT" to "bigbasket", "UBERIN" to "Uber", "RAPIDO" to "Rapido", "OLACAB" to "Ola",
    )

    /** chat apps: the brand comes from what the message says, not who sent it */
    private val messengers = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "com.google.android.apps.messaging",
        "io.github.jaswantk917.chitthi", "io.github.jaswantk917.chitthi.debug",
    )

    private val done = listOf(
        "has been delivered", "was delivered", "is delivered", "delivered successfully", "successfully delivered",
        "order delivered", "enjoy your meal", "trip ended", "trip completed", "ride completed", "hope you enjoyed",
        "order cancelled", "order canceled", "has been cancelled", "was cancelled",
    )
    private val live = listOf(
        "out for delivery", "on the way", "on his way", "on her way", "is arriving", "arriving in", "arriving soon",
        "will arrive", "reaching in", "reaching you", "about to reach", "is nearby", "is near you", "has reached",
        "reached your", "at your door", "at your location", "delivery partner", "delivery executive", "delivery agent",
        "delivery boy", "rider", "valet", "picked up your order", "order is picked", "has picked up", "driver is",
        "captain is", "your ride", "delivery otp", "otp for delivery", "delivery code", "professional is on",
    )
    private val today = listOf(
        "delivered today", "delivery today", "arriving today", "arrive today", "expected today", "by today",
        "scheduled for today", "delivery is scheduled for today",
    )

    private val phone = Regex("""(?<!\d)(?:\+?91[\s-]?|0)?([6-9]\d{4})[\s-]?(\d{5})(?!\d)""")

    fun last10(number: String): String = number.filter(Char::isDigit).takeLast(10)

    fun numbersIn(text: String): Set<String> = phone.findAll(text).map { it.groupValues[1] + it.groupValues[2] }.toSet()

    fun strengthOf(text: String): Strength? {
        val t = text.lowercase()
        return when {
            done.any(t::contains) -> Strength.Done
            live.any(t::contains) -> Strength.Live
            today.any(t::contains) -> Strength.Today
            else -> null
        }
    }

    fun brandIn(text: String): String? {
        val t = text.lowercase()
        return brands.firstOrNull { Regex("""\b${Regex.escape(it.lowercase())}\b""").containsMatchIn(t) }
    }

    /** a notification; [appLabel] is what the launcher calls the app that posted it */
    fun fromNotification(packageName: String, appLabel: String, text: String, at: Long): Signal? {
        val strength = strengthOf(text) ?: return null
        val brand = if (packageName in messengers) {
            brandIn(text) ?: DELIVERY
        } else {
            brandIn(appLabel) ?: brandIn(text) ?: appLabel
        }
        return Signal(brand, strength, at, numbersIn(text))
    }

    /** an SMS; DLT headers name the company even when the text doesn't */
    fun fromSms(address: String, body: String, at: Long): Signal? {
        val strength = strengthOf(body) ?: return null
        val header = address.substringAfter('-').substringBefore('-').uppercase()
        val brand = brandIn(body) ?: headers[header] ?: DELIVERY
        return Signal(brand, strength, at, numbersIn(body))
    }

    /** what's still open at [now]: live ones for an hour and a half, today's until midnight, minus anything since delivered */
    fun active(signals: List<Signal>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Signal> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val closedAt = signals.filter { it.strength == Strength.Done }.groupBy { it.brand }.mapValues { (_, v) -> v.maxOf { it.at } }
        return signals.filter { s ->
            val closed = closedAt[s.brand]?.let { it >= s.at } == true
            val open = when (s.strength) {
                Strength.Live -> now - s.at in 0..LIVE_WINDOW_MS
                Strength.Today -> Instant.ofEpochMilli(s.at).atZone(zone).toLocalDate() == today && s.at <= now
                Strength.Done -> false
            }
            open && !closed
        }
    }

    fun guess(number: String, signals: List<Signal>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Guess? {
        val open = active(signals, now, zone)
        val key = last10(number)
        if (key.length == 10) {
            open.lastOrNull { key in it.numbers }?.let { return Guess(tagFor(it.brand), confident = true, ride = it.brand in rideBrands, brands = listOf(it.brand)) }
        }
        val liveBrands = open.filter { it.strength == Strength.Live }.sortedByDescending { it.at }.map { it.brand }.distinct()
        if (liveBrands.size == 1) {
            val brand = liveBrands.single()
            return Guess("Probably ${label(brand)}", ride = brand in rideBrands, brands = listOf(brand))
        }
        if (liveBrands.size > 1) return Guess("Probably a delivery", detail = liveBrands.joinToString(" or "), brands = liveBrands)
        val todayBrands = open.map { it.brand }.distinct()
        if (todayBrands.size == 1) return Guess("Maybe ${label(todayBrands.single())}", brands = todayBrands)
        if (todayBrands.size > 1) return Guess("Maybe a delivery", detail = todayBrands.joinToString(" or "), brands = todayBrands)
        return null
    }

    /**
     * Whether an incoming call rings. Contacts always do; strangers are silenced unless something
     * is on its way, or they called a moment ago (someone who calls twice usually means it).
     */
    fun decide(
        number: String,
        isContact: Boolean,
        signals: List<Signal>,
        lastSilencedFromSame: Long?,
        now: Long,
        settings: ScreenSettings,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Decision {
        val guess = if (isContact) null else guess(number, signals, now, zone)
        if (isContact || !settings.silenceUnknown) return Decision(ring = true, guess = guess)
        if (settings.letDeliveries && guess != null) return Decision(ring = true, guess = guess)
        if (settings.repeatCallers && lastSilencedFromSame != null && now - lastSilencedFromSame <= REPEAT_WINDOW_MS) {
            return Decision(ring = true, guess = guess, repeat = true)
        }
        return Decision(ring = false, guess = guess)
    }

    /** what a confirmed call is remembered as: "Ownly delivery", "Uber driver" */
    fun tagFor(brand: String) = when (brand) {
        DELIVERY -> "Delivery"
        else -> label(brand)
    }

    fun isRide(tag: String) = rideBrands.any { tag == "$it driver" }

    private fun label(brand: String) = when {
        brand == DELIVERY -> "a delivery"
        brand in rideBrands -> "$brand driver"
        else -> "$brand delivery"
    }

    const val DELIVERY = "Delivery"
}
