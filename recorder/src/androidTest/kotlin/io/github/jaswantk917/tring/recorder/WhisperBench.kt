package io.github.jaswantk917.tring.recorder

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * A real call through Whisper the way the app runs it, timed: put the call at files/bench/call.m4a
 * and the model in files/whisper with adb first; the transcript lands in files/bench/whisper.txt.
 */
class WhisperBench {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.getExternalFilesDir(null), "bench")

    @Test
    fun aCall() = runBlocking<Unit> {
        val call = File(dir, "call.m4a")
        assumeTrue("no call at $call", call.exists())
        assumeTrue("Whisper's model isn't in", Whisper.ready(context))
        val started = System.currentTimeMillis()
        val transcript = Transcriber(context).transcribe(Uri.fromFile(call), null, emptyList(), onProgress = {}, onLine = {})
        val took = System.currentTimeMillis() - started
        assertEquals(Transcriber.Engine.Whisper, transcript.engine)
        File(dir, "whisper.txt").writeText(
            "# ${transcript.lines.size} lines in $took ms\n" + transcript.lines.joinToString("\n") { line ->
                "%5.1fs %s %.2f  %s".format(line.atMs / 1000f, when (line.them) { true -> "THEM"; false -> "YOU "; null -> "-   " }, line.confidence, line.text)
            },
        )
    }
}
