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
 * Short summaries with Gemini Nano, on the phone (Pixel 9 and newer, some others). They are tried
 * right after a call and when an assistant asks; AICore may only answer the app in front, so what
 * it turns down is made while the recordings screen is open.
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
            You summarise a phone call from its transcript, which may be Hindi, English or a mix and
            has speech-recognition mistakes. Write plain text in simple English, 1 to 3 short lines,
            each starting with "• ": what was talked about, anything agreed or promised, and any
            time, amount, place or task said. Only what the transcript says: never guess, never
            mention what wasn't said, don't repeat the name or number. No markdown, no preamble.
        """.trimIndent(),
        prompt = "Call with $who\n" + transcriptText(lines),
        maxTokens = 160,
    )?.let(::clean)

    /** what the person has been talking about lately, from the summaries of their recent calls */
    suspend fun lately(calls: List<Pair<String, String>>): String? = generate(
        system = """
            Below are summaries of someone's recent phone calls, newest first, each with who it was
            with and when. Tell them what they have been talking about lately in 2 to 5 short lines,
            each starting with "• ": one line per topic, not per call, with who it was with; then
            any open task or promise. Plain text in simple English, no markdown, no preamble.
        """.trimIndent(),
        prompt = calls.joinToString("\n\n") { (heading, summary) -> "$heading\n$summary" }.take(MAX_CHARS),
        maxTokens = 320,
    )?.let(::clean)

    private suspend fun generate(system: String, prompt: String, maxTokens: Int): String? = try {
        busy.withLock {
            val options: GenerateContentRequest.Builder.() -> Unit = {
                temperature = 0.2f
                topK = 16
                maxOutputTokens = maxTokens
            }
            model.generateContent(generateContentRequest(SystemInstruction(system), TextPart(prompt), options))
                .candidates.firstOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() }
                ?.also { Log.i(TAG, "made ${it.length} chars") }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Nano generation failed", e)
        null
    }

    companion object {
        private const val TAG = "Summarizer"
        /** below this many words there is nothing to summarise, and Nano makes something up (it did, for a 1 s call) */
        const val FEWEST_WORDS = 12
        /** the words Shruti is at least this sure of must make up the count, or the call is mostly guesses */
        const val SURE = 0.6f

        private fun words(line: Line) = line.text.split(' ').count { it.any(Char::isLetterOrDigit) }

        /** enough words Shruti is sure of; anything less and Nano would be summarising its own guesses */
        fun worthSummarising(lines: List<Line>) = lines.filter { it.confidence >= SURE }.sumOf(::words) >= FEWEST_WORDS

        /** Nano writes markdown now and then, which shows as stray asterisks */
        fun clean(text: String) = text.replace("**", "").replace("__", "").lines().joinToString("\n") { it.trimEnd() }.trim()

        /** Nano takes under 4,000 tokens; Hindi in Devanagari costs about a token per 1.5 characters */
        const val MAX_CHARS = 5_000

        fun transcriptText(lines: List<Line>) = lines.joinToString("\n") { line ->
            when (line.them) { true -> "Them: "; false -> "Me: "; null -> "" } + line.text
        }.let { if (it.length <= MAX_CHARS) it else it.take(MAX_CHARS / 2) + "\n…\n" + it.takeLast(MAX_CHARS / 2) }
    }
}
