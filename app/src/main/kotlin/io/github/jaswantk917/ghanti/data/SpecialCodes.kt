package io.github.jaswantk917.ghanti.data

/** Codes the phone app acts on as soon as they're typed, before any call. */
sealed interface SpecialCode {
    /** `*#*#4636#*#*`: Android hands [code] to whichever app or system menu listens for it */
    data class Secret(val code: String) : SpecialCode

    /** `*#06#` */
    data object Imei : SpecialCode

    companion object {
        private val secret = Regex("""\*#\*#(\d+)#\*#\*""")

        fun parse(typed: String): SpecialCode? = when {
            typed == "*#06#" -> Imei
            else -> secret.matchEntire(typed)?.let { Secret(it.groupValues[1]) }
        }
    }
}
