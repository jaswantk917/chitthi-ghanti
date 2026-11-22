package io.github.jaswantk917.tring.recorder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Words from a recording, on the phone: with Whisper once its model is in ([WhisperModel]), else
 * with Shruti. Shruti goes turn by turn, so each line is already "You" or "Them"; Whisper goes in
 * pieces of about a minute and a half ([Pieces]) and its words are put on whoever's turn they fall
 * in. An older call without turns gets lines with no names.
 */
class Transcriber(private val context: Context) {
    data class Progress(val done: Int, val of: Int)

    enum class Engine { Shruti, Whisper }

    data class Transcript(val engine: Engine, val lines: List<Line>)

    class Unavailable : Exception("Transcripts need a phone with a 64-bit ARM processor")

    val supported get() = Shruti.available || Whisper.supported

    /** what the next transcript is written with */
    fun engine() = if (Whisper.ready(context)) Engine.Whisper else Engine.Shruti

    /** [names]: who the call was with, so Shruti spells them as saved */
    suspend fun transcribe(
        uri: Uri,
        turns: List<Turn>?,
        names: List<String>,
        onProgress: (Progress) -> Unit,
        onLine: (Line) -> Unit,
    ): Transcript = withContext(Dispatchers.IO) {
        val engine = engine()
        if (engine == Engine.Shruti && !Shruti.available) throw Unavailable()
        val pcm = decode(uri) ?: error("This recording can't be read")
        try {
            val lines = when (engine) {
                Engine.Whisper -> whisper(pcm, turns, onProgress, onLine)
                Engine.Shruti -> shruti(pcm, turns, names, onProgress, onLine)
            }
            Transcript(engine, lines)
        } finally {
            pcm.delete()
        }
    }

    private suspend fun shruti(pcm: File, turns: List<Turn>?, names: List<String>, onProgress: (Progress) -> Unit, onLine: (Line) -> Unit): List<Line> {
        val pieces = turns?.takeIf { it.isNotEmpty() }
            ?: (0 until pcm.length().toInt() / BYTES_PER_MS step WINDOW_MS).map { Turn(it, it + WINDOW_MS, false) }
        val named = !turns.isNullOrEmpty()
        val lines = ArrayList<Line>()
        pieces.forEachIndexed { i, piece ->
            currentCoroutineContext().ensureActive()
            onProgress(Progress(i, pieces.size))
            val heard = Shruti.transcribe(context, read(pcm, piece.startMs, piece.endMs), names)
            if (heard.text.isNotEmpty()) lines += Line(if (named) piece.them else null, piece.startMs, heard.text, heard.confidence).also(onLine)
        }
        onProgress(Progress(pieces.size, pieces.size))
        return lines
    }

    private suspend fun whisper(pcm: File, turns: List<Turn>?, onProgress: (Progress) -> Unit, onLine: (Line) -> Unit): List<Line> {
        val bounds = listOf(0) + Pieces.cuts(loudness(pcm)) + (pcm.length() / BYTES_PER_MS).toInt()
        val pieces = bounds.zipWithNext()
        return Whisper.session(context) {
            val lines = ArrayList<Line>()
            pieces.forEachIndexed { i, (from, to) ->
                currentCoroutineContext().ensureActive()
                onProgress(Progress(i, pieces.size))
                val heard = transcribe(read(pcm, from, to)).map { it.copy(startMs = it.startMs + from, endMs = it.endMs + from) }
                Pieces.lines(Pieces.cutRepeats(heard), turns).forEach { line ->
                    lines += line
                    onLine(line)
                }
            }
            onProgress(Progress(pieces.size, pieces.size))
            lines
        }
    }

    /** how loud each [Pieces.FRAME_MS] of the call is, for cutting it where it's quiet */
    private fun loudness(pcm: File): FloatArray {
        val frameBytes = Pieces.FRAME_MS * BYTES_PER_MS
        val frames = (pcm.length() / frameBytes).toInt()
        val bytes = ByteArray(frameBytes)
        return DataInputStream(BufferedInputStream(pcm.inputStream(), 1 shl 16)).use { input ->
            FloatArray(frames) {
                input.readFully(bytes)
                val samples = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                var sum = 0L
                while (samples.hasRemaining()) sum += abs(samples.get().toInt())
                sum.toFloat() / (frameBytes / 2)
            }
        }
    }

    /** the recording as raw 16 kHz mono PCM, what Shruti takes; null for anything else */
    private fun decode(uri: Uri): File? {
        val extractor = MediaExtractor()
        val out = File(context.cacheDir, "call-decoded.pcm")
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            val format = extractor.getTrackFormat(track)
            if (format.getInteger(MediaFormat.KEY_SAMPLE_RATE) != Mixdown.RATE || format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != 1) return null
            extractor.selectTrack(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            BufferedOutputStream(out.outputStream(), 1 shl 16).use { sink ->
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, 10_000)
                    if (index >= 0) {
                        val buffer = codec.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                        buffer.position(info.offset)
                        var left = info.size
                        while (left > 0) {
                            val n = minOf(left, chunk.size)
                            buffer.get(chunk, 0, n)
                            sink.write(chunk, 0, n)
                            left -= n
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            codec.stop()
            codec.release()
            return out
        } catch (e: Exception) {
            Log.w(TAG, "couldn't decode for slicing", e)
            out.delete()
            return null
        } finally {
            extractor.release()
        }
    }

    private fun read(pcm: File, fromMs: Int, toMs: Int): FloatArray = RandomAccessFile(pcm, "r").use { source ->
        val from = (fromMs.toLong() * BYTES_PER_MS).coerceAtMost(source.length())
        val to = (toMs.toLong() * BYTES_PER_MS).coerceAtMost(source.length())
        val bytes = ByteArray(((to - from).toInt()) and 1.inv())
        source.seek(from)
        source.readFully(bytes)
        val samples = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        FloatArray(samples.remaining()) { samples.get(it) / 32_768f }
    }

    private companion object {
        const val TAG = "Transcriber"
        const val BYTES_PER_MS = Mixdown.RATE * 2 / 1_000
        /** a recording without turns goes to Shruti in pieces this long, its own window */
        const val WINDOW_MS = 30_000
    }
}
