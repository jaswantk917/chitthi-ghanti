package io.github.jaswantk917.ghanti.data

/** Dialpad search: typed digits match a name's letters on the keys, or the number itself. */
object T9 {
    private val keys = mapOf(
        'a' to '2', 'b' to '2', 'c' to '2', 'd' to '3', 'e' to '3', 'f' to '3', 'g' to '4', 'h' to '4', 'i' to '4',
        'j' to '5', 'k' to '5', 'l' to '5', 'm' to '6', 'n' to '6', 'o' to '6', 'p' to '7', 'q' to '7', 'r' to '7',
        's' to '7', 't' to '8', 'u' to '8', 'v' to '8', 'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9',
    )

    fun digitsOf(name: String): String = name.lowercase().map { keys[it] ?: if (it.isDigit()) it else ' ' }.joinToString("")

    /** 0 = no match; higher is better: a word's start beats the middle of a name beats the number */
    fun score(typed: String, name: String, number: String): Int {
        if (typed.isEmpty()) return 0
        val words = digitsOf(name).split(' ').filter { it.isNotEmpty() }
        return when {
            words.firstOrNull()?.startsWith(typed) == true -> 4
            words.any { it.startsWith(typed) } -> 3
            initials(words).startsWith(typed) && typed.length > 1 -> 2
            number.filter(Char::isDigit).contains(typed) -> 1
            else -> 0
        }
    }

    private fun initials(words: List<String>) = words.joinToString("") { it.take(1) }

    /**
     * How much you deal with a number: each call counts, halving every two weeks, a missed one half as much and
     * a blocked or declined one not at all; a favourite starts three calls ahead.
     */
    fun affinity(starred: Boolean, calls: List<CallEntry>, now: Long): Double = (if (starred) 3.0 else 0.0) + calls.sumOf { c ->
        val weight = when (c.kind) {
            Kind.Incoming, Kind.Outgoing -> 1.0
            Kind.Missed -> 0.5
            Kind.Declined, Kind.Blocked -> 0.0
        }
        weight * Math.pow(0.5, (now - c.date).coerceAtLeast(0) / HALF_LIFE_MS)
    }

    /** names before numbers, since a typed digit is in nearly every number; then the people you call most, then the closer match */
    fun <T> rank(items: List<T>, score: (T) -> Int, affinity: (T) -> Double): List<T> =
        items.filter { score(it) > 0 }.sortedWith(compareByDescending<T> { score(it) > 1 }.thenByDescending(affinity).thenByDescending(score))

    private const val HALF_LIFE_MS = 14 * 24 * 60 * 60_000.0
}
