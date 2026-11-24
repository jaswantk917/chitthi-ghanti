package io.github.jaswantk917.tring.recorder

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

/** What the caller hears, said once by the phone's own voice and kept, so it plays instantly. */
object Greeting {
    private const val HELLO = "नमस्ते, अभी फ़ोन नहीं उठा पा रहे हैं। बीप के बाद बताइए कि आप कौन हैं और क्यों कॉल किया है।"
    private const val THANKS = "धन्यवाद, आपका संदेश पहुँचा दिया जाएगा।"
    private const val MAKE_MS = 30_000L

    fun hello(context: Context) = File(context.filesDir, "screen-hello-1.pcm")
    fun thanks(context: Context) = File(context.filesDir, "screen-thanks-1.pcm")
    fun ready(context: Context) = hello(context).length() > 0 && thanks(context).length() > 0

    /** false when the phone has no Hindi voice to say it with */
    suspend fun prepare(context: Context): Boolean {
        if (ready(context)) return true
        val app = context.applicationContext
        val opened = CompletableDeferred<Int>()
        val tts = TextToSpeech(app) { opened.complete(it) }
        try {
            if (withTimeoutOrNull(MAKE_MS) { opened.await() } != TextToSpeech.SUCCESS) return false
            val language = tts.setLanguage(Locale("hi", "IN"))
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) return false
            val hello = say(tts, app, HELLO) ?: return false
            val thanks = say(tts, app, THANKS) ?: return false
            return withContext(Dispatchers.IO) {
                hello(app).writeBytes(Pcm.bytes(hello + Pcm.silence(300) + Pcm.beep() + Pcm.silence(100)))
                thanks(app).writeBytes(Pcm.bytes(thanks))
                true
            }
        } finally {
            tts.shutdown()
        }
    }

    private suspend fun say(tts: TextToSpeech, context: Context, text: String): ShortArray? {
        val file = File(context.cacheDir, "greeting.wav")
        val done = CompletableDeferred<Boolean>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { done.complete(true) }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { done.complete(false) }
        })
        return try {
            if (tts.synthesizeToFile(text, Bundle(), file, "greeting") != TextToSpeech.SUCCESS) return null
            if (withTimeoutOrNull(MAKE_MS) { done.await() } != true) return null
            withContext(Dispatchers.IO) { Pcm.readWav(file.readBytes()) }?.let { Pcm.resample(it.samples, it.rate) }
        } catch (e: Exception) {
            Log.w("Greeting", "couldn't make the greeting", e)
            null
        } finally {
            file.delete()
        }
    }
}
