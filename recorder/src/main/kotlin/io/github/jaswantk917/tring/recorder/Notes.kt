package io.github.jaswantk917.tring.recorder

import android.content.Context
import java.io.File

/**
 * One line of a transcript: who said it, when it starts (ms), the words, and how sure Shruti was of
 * them (0..1). [them] is null when the recording has no turns.
 */
data class Line(val them: Boolean?, val atMs: Int, val text: String, val confidence: Float = 1f)

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
        return file.readLines().filter { it.isNotEmpty() }.map { row ->
            val parts = row.split('\t', limit = 4)
            // written before confidences were kept: made again, so short or unsure calls aren't summarised from guesses
            if (parts.size != 4) return null
            val them = when (parts[0]) { "T" -> true; "Y" -> false; else -> null }
            Line(them, parts[1].toIntOrNull() ?: 0, parts[3], parts[2].toFloatOrNull() ?: 0f)
        }
    }

    fun saveTranscript(id: Long, lines: List<Line>) {
        File(dir, "$id.transcript").writeText(lines.joinToString("\n") { line ->
            "${when (line.them) { true -> "T"; false -> "Y"; null -> "-" }}\t${line.atMs}\t${line.confidence}\t${line.text.replace('\n', ' ').replace('\t', ' ')}"
        })
    }

    /**
     * A call's summary; empty when the call was too short to have one; null when it hasn't been
     * made yet, or was made by an older prompt and is due to be made again.
     */
    fun summary(id: Long): String? = File(dir, "$id.summary").takeIf { it.exists() }?.readText()
        ?.takeIf { it.startsWith(VERSION) }?.removePrefix(VERSION)

    fun saveSummary(id: Long, text: String) {
        File(dir, "$id.summary").writeText(VERSION + text)
        RecordingsProvider.changed(context)
    }

    /** what you've been talking about lately, and the newest call it took in */
    fun lately(): Pair<String, Long>? {
        val text = File(dir, "lately").takeIf { it.exists() }?.readText()?.takeIf { it.startsWith(VERSION) }?.removePrefix(VERSION) ?: return null
        return text.substringAfter('\n') to (text.substringBefore('\n').toLongOrNull() ?: 0L)
    }

    fun saveLately(text: String, newest: Long) = File(dir, "lately").writeText("$VERSION$newest\n$text")

    fun forget(id: Long) {
        listOf("turns", "transcript", "summary").forEach { File(dir, "$id.$it").delete() }
    }

    private companion object {
        /** bumped whenever the summary prompts change, so older summaries are made again */
        const val VERSION = "v2\n"
    }
}
