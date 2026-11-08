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
}
