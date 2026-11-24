package io.github.jaswantk917.tring.recorder

import android.content.Context
import java.io.File

/**
 * One line of a transcript: when it starts (ms), the words, and how sure the model was of them
 * (0..1). [them] stays null: telling the two voices apart was too often wrong (2026-11-23).
 */
data class Line(val them: Boolean?, val atMs: Int, val text: String, val confidence: Float = 1f) {
    /** heard too poorly to trust: on real calls these are mostly guesses */
    val unsure get() = confidence < UNSURE

    companion object {
        const val UNSURE = 0.5f

        /** a saved row, its side ignored; null for one written before confidences were kept */
        fun read(row: String): Line? {
            val parts = row.split('\t', limit = 4)
            if (parts.size != 4) return null
            return Line(null, parts[1].toIntOrNull() ?: 0, parts[3], parts[2].toFloatOrNull() ?: 0f)
        }
    }
}

/** One minute of the call, shown as a single paragraph. */
data class Minute(val index: Int, val lines: List<Line>) {
    val atMs get() = index * MS

    companion object {
        private const val MS = 60_000

        fun of(lines: List<Line>) = lines.groupBy { it.atMs / MS }.map { (index, heard) -> Minute(index, heard) }

        /** the transcript to paste somewhere: a plain paragraph per minute */
        fun plain(lines: List<Line>) = of(lines).joinToString("\n\n") { minute -> minute.lines.joinToString(" ") { it.text.trim() } }
    }
}

/**
 * What the recorder knows about each recording besides the sound: who talked when (written at the
 * end of the call), the transcript, and the summary. In the app's private files, keyed by the
 * recording's MediaStore id, so they go when the app does.
 */
class Notes(private val context: Context) {
    private val dir = File(context.filesDir, "notes").apply { mkdirs() }

    fun turnsFile(id: Long) = File(dir, "$id.turns")
    fun turns(id: Long) = Turns.read(turnsFile(id))

    fun transcript(id: Long): List<Line>? {
        val file = File(dir, "$id.transcript")
        if (!file.exists()) return null
        // a "#" line is the model's name, which an early 1.11 wrote first
        // an old row makes the whole transcript due again, so short or unsure calls aren't summarised from guesses
        return file.readLines().filter { it.isNotEmpty() && !it.startsWith("#") }.map { Line.read(it) ?: return null }
    }

    /** [clearer]: written by IndicConformer, which the call screen then doesn't offer again */
    fun saveTranscript(id: Long, lines: List<Line>, clearer: Boolean = false) {
        File(dir, "$id.transcript").writeText((if (clearer) "$CLEARER\n" else "") + lines.joinToString("\n") { line ->
            "${when (line.them) { true -> "T"; false -> "Y"; null -> "-" }}\t${line.atMs}\t${line.confidence}\t${line.text.replace('\n', ' ').replace('\t', ' ')}"
        })
    }

    fun clearer(id: Long) = File(dir, "$id.transcript").takeIf { it.exists() }?.useLines { it.firstOrNull() } == CLEARER

    /** made again from a transcript written anew */
    fun forgetSummary(id: Long) {
        File(dir, "$id.summary").delete()
    }

    /**
     * A call's summary; empty when the call was too short to have one; null when it hasn't been
     * made yet, or was made by an older prompt and is due to be made again.
     */
    fun summary(id: Long): String? = File(dir, "$id.summary").takeIf { it.exists() }?.readText()
        ?.takeIf { it.startsWith(VERSION) }?.removePrefix(VERSION)?.let(Summarizer::clean)

    fun saveSummary(id: Long, text: String) {
        File(dir, "$id.summary").writeText(VERSION + text)
        RecordingsProvider.changed(context)
    }

    /** what you've been talking about lately, and the newest call it took in */
    fun lately(): Pair<String, Long>? {
        val text = File(dir, "lately").takeIf { it.exists() }?.readText()?.takeIf { it.startsWith(VERSION) }?.removePrefix(VERSION) ?: return null
        return Summarizer.clean(text.substringAfter('\n')) to (text.substringBefore('\n').toLongOrNull() ?: 0L)
    }

    fun saveLately(text: String, newest: Long) = File(dir, "lately").writeText("$VERSION$newest\n$text")

    /** a message a screened caller left, rather than a call you were on */
    fun isMessage(id: Long) = File(dir, "$id.message").exists()

    fun markMessage(id: Long) = File(dir, "$id.message").writeText("")

    fun forget(id: Long) {
        listOf("turns", "transcript", "summary", "message").forEach { File(dir, "$id.$it").delete() }
    }

    private companion object {
        /** bumped whenever the summary prompts change, so older summaries are made again */
        const val VERSION = "v2\n"
        // a "#" line, which the reader skips
        const val CLEARER = "#Indic"
    }
}
