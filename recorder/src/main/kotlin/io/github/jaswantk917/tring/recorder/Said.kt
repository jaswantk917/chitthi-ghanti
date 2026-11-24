package io.github.jaswantk917.tring.recorder

/**
 * Everything the caller has said. The phone's recognizer hears in stretches between pauses and
 * starts each one empty, so its partial results only ever hold the stretch being heard.
 */
internal class Said {
    private val stretches = mutableListOf<String>()
    private var current = ""
    private var segmented = false

    /** the stretch being heard so far */
    fun partial(words: String) {
        current = words.trim()
    }

    /** the recognizer finished a stretch; empty when the audio ran out mid-stretch */
    fun finished(words: String) {
        segmented = true
        val final = words.trim().ifEmpty { current }
        current = ""
        // a stretch already moved along by began() arrives again, finished
        if (final.isNotEmpty() && final != stretches.lastOrNull()) stretches += final
    }

    /** speech starts again; a recognizer that never hands over finished stretches loses nothing */
    fun began() {
        if (segmented || current.isEmpty()) return
        stretches += current
        current = ""
    }

    val text: String get() = (stretches + current).filter { it.isNotEmpty() }.joinToString(" ")
}
