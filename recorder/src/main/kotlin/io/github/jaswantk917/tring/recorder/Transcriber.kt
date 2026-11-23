package io.github.jaswantk917.tring.recorder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Words from a recording, on the phone, with Shruti. A call with turns is transcribed turn by
 * turn; an older one without turns goes in half-minute pieces. Nobody is named on either.
 */
class Transcriber(private val context: Context) {
    data class Progress(val done: Int, val of: Int)

    class Unavailable : Exception("Shruti needs a 64-bit ARM phone")

    val supported get() = Shruti.available

    /** [names]: who the call was with, so Shruti spells them as saved */
    suspend fun transcribe(
        uri: Uri,
        turns: List<Turn>?,
        names: List<String>,
        onProgress: (Progress) -> Unit,
        onLine: (Line) -> Unit,
    ): List<Line> = withContext(Dispatchers.IO) {
        if (!supported) throw Unavailable()
        val pcm = decode(context, uri, File(context.cacheDir, "call-decoded.pcm")) ?: error("This recording can't be read")
        try {
            val pieces = turns?.takeIf { it.isNotEmpty() }
                ?: (0 until pcm.length().toInt() / BYTES_PER_MS step WINDOW_MS).map { Turn(it, it + WINDOW_MS, false) }
            val lines = ArrayList<Line>()
            pieces.forEachIndexed { i, piece ->
                ensureActive()
                onProgress(Progress(i, pieces.size))
                val heard = Shruti.transcribe(context, read(pcm, piece.startMs, piece.endMs), names)
                if (heard.text.isNotEmpty()) lines += Line(null, piece.startMs, heard.text, heard.confidence).also(onLine)
            }
            onProgress(Progress(pieces.size, pieces.size))
            lines
        } finally {
            pcm.delete()
        }
    }

    internal companion object {
        private const val TAG = "Transcriber"
        const val BYTES_PER_MS = Mixdown.RATE * 2 / 1_000
        /** a recording without turns goes in pieces this long, Shruti's own window */
        private const val WINDOW_MS = 30_000

        /** the recording as raw 16 kHz mono PCM into [out], what Shruti and IndicConformer take; null for anything else */
        fun decode(context: Context, uri: Uri, out: File): File? {
            val extractor = MediaExtractor()
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

        /** [fromMs] to [toMs] of decoded PCM as samples in [-1, 1] */
        fun read(pcm: File, fromMs: Int, toMs: Int): FloatArray = RandomAccessFile(pcm, "r").use { source ->
            val from = (fromMs.toLong() * BYTES_PER_MS).coerceAtMost(source.length())
            val to = (toMs.toLong() * BYTES_PER_MS).coerceAtMost(source.length())
            val bytes = ByteArray(((to - from).toInt()) and 1.inv())
            source.seek(from)
            source.readFully(bytes)
            val samples = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            FloatArray(samples.remaining()) { samples.get(it) / 32_768f }
        }
    }
}
