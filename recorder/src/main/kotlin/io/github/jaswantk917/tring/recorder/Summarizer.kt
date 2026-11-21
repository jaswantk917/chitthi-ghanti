package io.github.jaswantk917.tring.recorder

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Short summaries with Gemini Nano, on the phone (Pixel 9 and newer, some others). AICore only
 * answers the app in front, so these run while the recordings screen is open.
 */
class Summarizer {
    private val model by lazy { Generation.getClient() }
    private val busy = Mutex()

    /** true once Nano is ready; starts its download where the phone can get it */
    suspend fun ready(): Boolean {
        val status = try { model.checkStatus() } catch (e: Exception) { Log.w(TAG, "AICore unavailable", e); return false }
        return when (status) {
            FeatureStatus.AVAILABLE -> true
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> try {
                var ok = false
                model.download().collect { if (it is DownloadStatus.DownloadCompleted) ok = true }
                ok
            } catch (e: Exception) { Log.w(TAG, "Nano download failed", e); false }
            else -> false
        }
    }

    /** a few lines on what one call was about */
    suspend fun call(who: String, lines: List<Line>): String? = generate(
        system = """
            You summarise a phone call from its transcript. The transcript may be Hindi, English or
            a mix, and has mistakes from speech recognition. Write in simple English, 2 to 4 short
            lines, each starting with "• ": what the call was about, anything agreed or promised,
            and any time, amount, place or task mentioned. Use the names as given. No preamble.
        """.trimIndent(),
        prompt = "Call with $who\n" + transcriptText(lines),
        maxTokens = 160,
    )

    /** what the person has been talking about lately, from the summaries of their recent calls */
    suspend fun lately(calls: List<Pair<String, String>>): String? = generate(
        system = """
            Below are summaries of someone's recent phone calls, newest first, each with who it was
            with and when. Tell them in 3 to 5 short lines, each starting with "• ", what they have
            been talking about lately: the main topics, open tasks or promises, and who about what.
            Simple English, no preamble.
        """.trimIndent(),
        prompt = calls.joinToString("\n\n") { (heading, summary) -> "$heading\n$summary" }.take(MAX_CHARS),
        maxTokens = 220,
    )

    private suspend fun generate(system: String, prompt: String, maxTokens: Int): String? = try {
        busy.withLock {
            val options: GenerateContentRequest.Builder.() -> Unit = {
                temperature = 0.2f
                topK = 16
                maxOutputTokens = maxTokens
            }
            model.generateContent(generateContentRequest(SystemInstruction(system), TextPart(prompt), options))
                .candidates.firstOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Nano generation failed", e)
        null
    }

    companion object {
        private const val TAG = "Summarizer"
        /** Nano takes under 4,000 tokens; Hindi in Devanagari costs about a token per 1.5 characters */
        const val MAX_CHARS = 5_000

        fun transcriptText(lines: List<Line>) = lines.joinToString("\n") { line ->
            when (line.them) { true -> "Them: "; false -> "Me: "; null -> "" } + line.text
        }.let { if (it.length <= MAX_CHARS) it else it.take(MAX_CHARS / 2) + "\n…\n" + it.takeLast(MAX_CHARS / 2) }
    }
}
