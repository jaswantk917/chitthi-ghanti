package io.github.jaswantk917.tring.recorder

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale

/** Hindi said by the phone's own voice, as call-line audio, for feeding the recognizer. */
object TestSpeech {
    const val FIRST = "मेरा नाम राहुल है, मैं दिल्ली से बोल रहा हूँ।"
    const val SECOND = "कल शाम को घर पर मिलना है, बहुत ज़रूरी बात है।"
    val FIRST_WORDS = listOf("राहुल", "दिल्ली")
    val SECOND_WORDS = listOf("शाम", "ज़रूरी", "जरूरी", "मिलना")

    suspend fun say(context: Context, text: String): ShortArray = withTimeout(30_000) {
        val opened = CompletableDeferred<Int>()
        val tts = TextToSpeech(context) { opened.complete(it) }
        try {
            check(opened.await() == TextToSpeech.SUCCESS) { "no voice" }
            tts.setLanguage(Locale("hi", "IN"))
            val file = File(context.cacheDir, "spoken.wav")
            val done = CompletableDeferred<Boolean>()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) { done.complete(true) }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { done.complete(false) }
            })
            check(tts.synthesizeToFile(text, Bundle(), file, "spoken") == TextToSpeech.SUCCESS)
            check(done.await())
            val wav = Pcm.readWav(file.readBytes())!!
            file.delete()
            Pcm.resample(wav.samples, wav.rate)
        } finally {
            tts.shutdown()
        }
    }

    /** at the call line's pace, 40 ms at a time */
    fun feed(sound: ByteArray, write: (ByteArray) -> Unit) {
        var at = 0
        while (at < sound.size) {
            val end = minOf(at + CHUNK, sound.size)
            write(sound.copyOfRange(at, end))
            at = end
            Thread.sleep(40)
        }
    }

    private const val CHUNK = 1_280
}
