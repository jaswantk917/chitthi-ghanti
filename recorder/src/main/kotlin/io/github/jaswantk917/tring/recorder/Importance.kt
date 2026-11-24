package io.github.jaswantk917.tring.recorder

import kotlinx.coroutines.withTimeoutOrNull

/**
 * Whether what a stranger said is worth ringing for. It only ever chooses between ringing and
 * taking a message, and a doubt rings, because a caller can say anything.
 */
object Importance {
    data class Verdict(val ring: Boolean, val reason: String)

    private val urgent = listOf("emergency", "hospital", "accident", "ambulance", "police", "urgent", "इमरजेंसी", "अस्पताल", "हॉस्पिटल", "एक्सीडेंट", "दुर्घटना", "एंबुलेंस", "पुलिस", "अर्जेंट", "जरूरी", "ज़रूरी")
    private val delivery = listOf("delivery", "parcel", "courier", "order", "zomato", "swiggy", "blinkit", "zepto", "amazon", "flipkart", "uber", "ola", "rapido", "डिलीवरी", "पार्सल", "कूरियर", "कुरियर", "ऑर्डर", "जोमैटो", "ज़ोमैटो", "स्विगी", "ब्लिंकिट", "जेप्टो", "अमेज़न", "अमेजन", "फ्लिपकार्ट", "उबर", "ओला", "रैपिडो", "गेट")
    private val sales = listOf("loan", "credit card", "insurance", "investment", "offer", "survey", "property", "demat", "trading", "लोन", "क्रेडिट कार्ड", "बीमा", "इंश्योरेंस", "निवेश", "ऑफर", "सर्वे", "प्रॉपर्टी", "डीमैट", "ट्रेडिंग")

    /** null when the words alone don't settle it */
    fun byRules(words: String): Verdict? {
        val said = words.lowercase()
        return when {
            // no words can mean the recognizer failed on a real person, so it's never a reason to hang up
            said.isBlank() -> Verdict(true, "Couldn't make out any words, so it's ringing")
            has(said, urgent) -> Verdict(true, "Sounds urgent")
            has(said, delivery) -> Verdict(true, "Sounds like a delivery or ride")
            has(said, sales) -> Verdict(false, "Sounds like a sales call")
            else -> null
        }
    }

    private fun has(said: String, terms: List<String>): Boolean {
        val words = said.split(Regex("[\\s,.!?।]+")).toSet()
        return terms.any { term -> if (' ' in term) term in said else term in words }
    }

    suspend fun decide(words: String, situation: String, summarizer: Summarizer): Verdict {
        byRules(words)?.let { return it }
        val important = withTimeoutOrNull(MODEL_MS) { if (summarizer.ready()) summarizer.important(words, situation) else null }
        return when (important) {
            true -> Verdict(true, "Sounds important")
            false -> Verdict(false, "Doesn't sound urgent")
            null -> Verdict(true, "Couldn't tell, so it's ringing")
        }
    }

    private const val MODEL_MS = 3_500L
}
