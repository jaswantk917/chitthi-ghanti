package io.github.jaswantk917.tring.recorder

import kotlin.math.abs

/**
 * How a call goes through Whisper and comes back: in pieces of about a minute and a half, each cut
 * where it was quietest, each started afresh so one piece's mishearing can't carry into the next;
 * then its words as lines, with anything said three or more times running cut to once (Whisper's
 * way of failing on a weak line is to say one phrase over and over).
 */
object Pieces {
    const val TARGET_MS = 90_000
    const val FRAME_MS = 20
    /** a cut is the quietest moment this close to each mark */
    private const val SEARCH_MS = 10_000
    /** how long that quiet must last */
    private const val QUIET_MS = 300
    private const val LONGEST_REPEAT = 10

    /** where to cut a call whose loudness per [FRAME_MS] frame is [energy]: in ms, never at its very start or end */
    fun cuts(energy: FloatArray): List<Int> {
        val length = energy.size * FRAME_MS
        val span = QUIET_MS / FRAME_MS
        val cuts = ArrayList<Int>()
        var mark = TARGET_MS
        // the last piece may run up to half a piece long rather than leave a scrap
        while (mark + TARGET_MS / 2 < length) {
            val from = (mark - SEARCH_MS) / FRAME_MS
            val to = minOf((mark + SEARCH_MS) / FRAME_MS, energy.size - span)
            var best = from
            var quietest = Float.MAX_VALUE
            for (start in from..to) {
                var sum = 0f
                for (i in start until start + span) sum += energy[i]
                // ties go to the one nearest the mark
                if (sum < quietest || (sum == quietest && abs(start - mark / FRAME_MS) < abs(best - mark / FRAME_MS))) {
                    quietest = sum
                    best = start
                }
            }
            val cut = (best + span / 2) * FRAME_MS
            cuts += cut
            mark = cut + TARGET_MS
        }
        return cuts
    }

    /** a phrase of up to ten words said three or more times running, kept once */
    fun cutRepeats(words: List<Whisper.Word>): List<Whisper.Word> {
        var kept = words.filter { plain(it.text).isNotEmpty() }
        var changed = true
        while (changed) {
            changed = false
            for (n in 1..LONGEST_REPEAT) {
                var i = 0
                while (i + 3 * n <= kept.size) {
                    var times = 1
                    while (i + (times + 1) * n <= kept.size && same(kept, i, i + times * n, n)) times++
                    if (times >= 3) {
                        kept = kept.subList(0, i + n) + kept.subList(i + times * n, kept.size)
                        changed = true
                    }
                    i++
                }
            }
        }
        return kept
    }

    /**
     * Words as lines: a new line at each of Whisper's sentence breaks and wherever the speaker
     * changes. [turns] say who was talking when; without them no line has a speaker.
     */
    fun lines(words: List<Whisper.Word>, turns: List<Turn>?): List<Line> {
        val lines = ArrayList<Line>()
        var current = ArrayList<Whisper.Word>()
        var who: Boolean? = null
        fun close() {
            if (current.isEmpty()) return
            val text = current.joinToString(" ") { it.text.trim() }.trim()
            if (text.any { it.isLetterOrDigit() }) lines += Line(who, current.first().startMs, text, current.map { it.probability }.average().toFloat())
            current = ArrayList()
        }
        for (word in words) {
            val speaker = turns?.takeIf { it.isNotEmpty() }?.let { speaker(it, (word.startMs + word.endMs) / 2) }
            val last = current.lastOrNull()
            if (last != null && (last.segment != word.segment || speaker != who)) close()
            if (current.isEmpty()) who = speaker
            current += word
        }
        close()
        return lines
    }

    /** the person talking at [atMs]: the turn it falls in, else the nearest */
    private fun speaker(turns: List<Turn>, atMs: Int): Boolean =
        (turns.firstOrNull { atMs in it.startMs until it.endMs }
            ?: turns.minBy { minOf(abs(it.startMs - atMs), abs(it.endMs - atMs)) }).them

    private fun same(words: List<Whisper.Word>, a: Int, b: Int, n: Int) = (0 until n).all { plain(words[a + it].text) == plain(words[b + it].text) }

    private fun plain(word: String) = word.trim().trim(',', '?', '.', '!', '।', '"', '\'')
}
