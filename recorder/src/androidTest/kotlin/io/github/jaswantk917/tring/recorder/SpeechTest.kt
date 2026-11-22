package io.github.jaswantk917.tring.recorder

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A spoken Hindi sentence, saved where calls are saved, goes through the phone's speech model: Whisper when its model is in, else Shruti. */
class SpeechTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var uri: Uri

    @Before
    fun save() {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "Speech test · 21 Nov, 6.00 pm.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Recordings.folder())
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        uri = context.contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        context.contentResolver.openOutputStream(uri)!!.use { out ->
            InstrumentationRegistry.getInstrumentation().context.assets.open("spoken-hindi.m4a").use { it.copyTo(out) }
        }
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
    }

    @After
    fun remove() {
        context.contentResolver.delete(uri, null, null)
    }

    @Test
    fun aSavedCallIsListedUnderWhoItWasWith() {
        val listed = Recordings.list(context).first { it.uri == uri }
        assertTrue(listed.who == "Speech test" && listed.durationMs in 4_000..7_000)
    }

    @Test
    fun turnsAreHeardAsWords() = runBlocking {
        val lines = withTimeout(600_000) {
            Transcriber(context).transcribe(uri, listOf(Turn(0, 5_600, true)), emptyList(), onProgress = { Log.i(TAG, "$it") }, onLine = {})
        }.lines
        Log.i(TAG, "turns: $lines")
        assertTrue("heard: $lines", lines.size == 1 && lines[0].them == true && HEARD.any { it in lines[0].text })
    }

    @Test
    fun aRecordingWithoutTurnsIsHeardWhole() = runBlocking {
        val lines = withTimeout(600_000) {
            Transcriber(context).transcribe(uri, null, emptyList(), onProgress = {}, onLine = {})
        }.lines
        Log.i(TAG, "whole: $lines")
        assertTrue("heard: $lines", lines.isNotEmpty() && lines.all { it.them == null } && HEARD.any { word -> lines.any { word in it.text } })
    }

    private companion object {
        const val TAG = "SpeechTest"
        /** words of "नमस्ते, कल शाम छह बजे बाज़ार में मिलते हैं। बिजली का बिल भी भर देना।" any decent model gets */
        val HEARD = listOf("नमस्ते", "शाम", "बजे", "बिजली", "बिल")
    }
}
