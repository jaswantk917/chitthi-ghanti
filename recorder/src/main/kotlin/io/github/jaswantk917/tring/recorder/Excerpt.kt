package io.github.jaswantk917.tring.recorder

/** A few lines of a call for Tring's Recents, from the middle because calls open with hellos. */
data class Excerpt(val text: String, val more: Boolean) {
    companion object {
        const val LONGEST = 240

        fun of(lines: List<Line>): Excerpt? {
            val spoken = lines.filter { it.text.isNotBlank() }
            val heard = spoken.filter { !it.unsure }.ifEmpty { spoken }.map { it.text.trim() }
            if (heard.isEmpty()) return null
            val whole = heard.joinToString(" ")
            if (whole.length <= LONGEST) return Excerpt(whole, more = heard.size < spoken.size)

            val starts = heard.runningFold(0) { at, line -> at + line.length + 1 }.dropLast(1)
            val middle = (whole.length - LONGEST) / 2
            val first = starts.indices.minBy { kotlin.math.abs(starts[it] - middle) }
            var last = first
            while (last < heard.lastIndex && starts[last + 1] + heard[last + 1].length - starts[first] <= LONGEST) last++
            val window = heard.subList(first, last + 1).joinToString(" ")
            val cut = window.length > LONGEST
            val shown = if (cut) window.take(LONGEST).substringBeforeLast(' ') else window
            val before = if (first > 0) "… " else ""
            val after = if (cut || last < heard.lastIndex) " …" else ""
            return Excerpt(before + shown + after, more = true)
        }
    }
}
