package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummarizerTest {
    private fun line(text: String, confidence: Float = 0.9f) = Line(false, 0, text, confidence)

    @Test
    fun `a greeting is not worth a summary`() =
        assertFalse(Summarizer.worthSummarising(listOf(line("hello hello हाँ इधर लिफ़्"))))

    @Test
    fun `a single word is not worth a summary`() = assertFalse(Summarizer.worthSummarising(listOf(line("हाँ"))))

    @Test
    fun `no words is not worth a summary`() = assertFalse(Summarizer.worthSummarising(emptyList()))

    @Test
    fun `a real exchange is worth a summary`() = assertTrue(
        Summarizer.worthSummarising(
            listOf(
                line("कल शाम छह बजे बाजार में मिलते हैं"),
                line("ठीक है और बिजली का bill भी भर देना आखिरी तारीख परसों है"),
            )
        )
    )

    @Test
    fun `many words Shruti was unsure of are not worth a summary`() = assertFalse(
        Summarizer.worthSummarising(
            listOf(
                line("कल शाम छह बजे बाजार में मिलते हैं", confidence = 0.3f),
                line("ठीक है और बिजली का bill भी भर देना आखिरी तारीख परसों है", confidence = 0.4f),
            )
        )
    )

    @Test
    fun `punctuation and dashes are not words`() =
        assertFalse(Summarizer.worthSummarising(listOf(line("- - … . , ! ? - - - - - - - - - - - -"))))

    @Test
    fun `markdown asterisks are taken out`() =
        assertEquals("• Jio: plans till 2027\n• Pranil: UPI", Summarizer.clean("**• Jio:** plans till 2027  \n• **Pranil:** UPI\n"))

    @Test
    fun `Shruti's confidence is the mean of its words`() {
        val heard = Shruti.parse("""{"text":" हाँ ठीक है ","words":[{"word":"हाँ","probability":0.9},{"word":"ठीक","probability":0.5},{"word":"है","probability":0.7}]}""")
        assertEquals("हाँ ठीक है", heard.text)
        assertEquals(0.7f, heard.confidence, 0.001f)
    }

    @Test
    fun `no word list means Shruti gave no confidence`() = assertEquals(1f, Shruti.parse("""{"text":""}""").confidence, 0f)
}
