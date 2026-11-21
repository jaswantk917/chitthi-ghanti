package io.github.jaswantk917.tring.recorder

import android.content.Context
import java.io.File

/** One line of a transcript: who said it, when it starts (ms), and the words. [them] is null when the recording has no turns. */
data class Line(val them: Boolean?, val atMs: Int, val text: String)

/**
 * What the recorder knows about each recording besides the sound: who talked when (written at the
 * end of the call), the transcript, and the summary. In the app's private files, keyed by the
 * recording's MediaStore id, so they go when the app does.
 */
class Notes(context: Context) {
    private val dir = File(context.filesDir, "notes").apply { mkdirs() }

    fun turnsFile(id: Long) = File(dir, "$id.turns")
    fun turns(id: Long) = Turns.read(turnsFile(id))

    fun transcript(id: Long): List<Line>? {
        val file = File(dir, "$id.transcript")
        if (!file.exists()) return null
        return file.readLines().mapNotNull { row ->
            val parts = row.split('\t', limit = 3)
            if (parts.size != 3) return@mapNotNull null
            val them = when (parts[0]) { "T" -> true; "Y" -> false; else -> null }
            Line(them, parts[1].toIntOrNull() ?: 0, parts[2])
        }
    }

    fun saveTranscript(id: Long, lines: List<Line>) {
        File(dir, "$id.transcript").writeText(lines.joinToString("\n") { line ->
            "${when (line.them) { true -> "T"; false -> "Y"; null -> "-" }}\t${line.atMs}\t${line.text.replace('\n', ' ').replace('\t', ' ')}"
        })
    }

    fun summary(id: Long): String? = File(dir, "$id.summary").takeIf { it.exists() }?.readText()

    fun saveSummary(id: Long, text: String) = File(dir, "$id.summary").writeText(text)

    /** what you've been talking about lately, and the newest call it took in */
    fun lately(): Pair<String, Long>? {
        val file = File(dir, "lately")
        if (!file.exists()) return null
        val text = file.readText()
        return text.substringAfter('\n') to (text.substringBefore('\n').toLongOrNull() ?: 0L)
    }

    fun saveLately(text: String, newest: Long) = File(dir, "lately").writeText("$newest\n$text")

    fun forget(id: Long) {
        listOf("turns", "transcript", "summary").forEach { File(dir, "$id.$it").delete() }
    }
}
