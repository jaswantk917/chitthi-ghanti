package io.github.jaswantk917.tring.recorder

import java.io.File

/** One stretch of one person talking, in milliseconds from the start of the recording. */
data class Turn(val startMs: Int, val endMs: Int, val them: Boolean)

/**
 * The call as turns, from the recorder's own split of who was talking: transcribing each turn on
 * its own labels the transcript "You" and "Them" without any guessing from the words.
 */
object Turns {
    private const val FRAME_MS = 1_000 / 50
    /** a pause this short doesn't end a turn */
    private const val JOIN_MS = 700
    /** shorter than this is a cough or a "hm", not worth a line */
    private const val SHORTEST_MS = 300
    /** kept either side so a word's start and end aren't clipped */
    private const val PAD_MS = 150

    /** [caller]: per frame, how much it was the other person (0..1); [talking]: per frame, whether anyone was */
    fun from(caller: FloatArray, talking: BooleanArray): List<Turn> {
        val turns = ArrayList<Turn>()
        var start = -1
        var end = -1
        var them = false
        fun close() {
            if (start >= 0 && (end - start) * FRAME_MS >= SHORTEST_MS) turns += Turn(start * FRAME_MS, end * FRAME_MS, them)
            start = -1
        }
        for (i in talking.indices) {
            if (!talking[i]) continue
            val who = caller.getOrElse(i) { 0f } > 0.5f
            if (start >= 0 && (who != them || (i - end) * FRAME_MS > JOIN_MS)) close()
            if (start < 0) { start = i; them = who }
            end = i + 1
        }
        close()
        val last = talking.size * FRAME_MS
        return turns.mapIndexed { i, t ->
            // padding stops halfway to the neighbour, so no stretch of sound is in two turns
            val before = turns.getOrNull(i - 1)?.let { (it.endMs + t.startMs) / 2 } ?: 0
            val after = turns.getOrNull(i + 1)?.let { (t.endMs + it.startMs) / 2 } ?: last
            Turn((t.startMs - PAD_MS).coerceAtLeast(before), (t.endMs + PAD_MS).coerceAtMost(after), t.them)
        }
    }

    fun write(turns: List<Turn>, file: File) {
        file.parentFile?.mkdirs()
        file.writeText(turns.joinToString("\n") { "${it.startMs} ${it.endMs} ${if (it.them) "T" else "Y"}" })
    }

    fun read(file: File): List<Turn>? = if (!file.exists()) null else file.readLines().mapNotNull { line ->
        val parts = line.split(' ')
        if (parts.size != 3) return@mapNotNull null
        Turn(parts[0].toIntOrNull() ?: return@mapNotNull null, parts[1].toIntOrNull() ?: return@mapNotNull null, parts[2] == "T")
    }
}
