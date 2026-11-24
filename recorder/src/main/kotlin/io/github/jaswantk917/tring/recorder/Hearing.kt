package io.github.jaswantk917.tring.recorder

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * The phone's own speech recognition on the caller's audio, word by word. One segmented session
 * runs until the audio stops, handing over each stretch between pauses as it finishes.
 */
class Hearing(private val context: Context, private val onWords: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val pipe = ParcelFileDescriptor.createPipe()
    private val sink = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var open = true
    /** the recognizer heard speech, whether or not it made out any words */
    @Volatile var spoke = false
        private set
    private val said = Said()

    fun start() {
        recognizer = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull() ?: return
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() {
                spoke = true
                said.began()
            }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) { Log.i(TAG, "recognizer stopped: $error") }
            override fun onResults(results: Bundle?) = finished(results)
            override fun onSegmentResults(segmentResults: Bundle) = finished(segmentResults)
            override fun onEndOfSegmentedSession() = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                said.partial(words(partialResults))
                tell()
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30_000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, Pcm.RATE),
        )
    }

    /** from the audio thread; a recognizer that has stopped just isn't fed any more */
    fun feed(bytes: ByteArray, count: Int) {
        if (!open) return
        try {
            sink.write(bytes, 0, count)
        } catch (_: Exception) {
            open = false
        }
    }

    fun stop() {
        open = false
        runCatching { sink.close() }
        main.post {
            runCatching { recognizer?.destroy() }
            recognizer = null
            runCatching { pipe[0].close() }
        }
    }

    private fun finished(results: Bundle?) {
        said.finished(words(results))
        tell()
    }

    private fun words(results: Bundle?) = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private fun tell() {
        said.text.takeIf { it.isNotBlank() }?.let(onWords)
    }

    private companion object {
        const val TAG = "Hearing"
    }
}
