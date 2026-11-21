package io.github.jaswantk917.tring.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * During the call: the raw microphones to a scratch file, two channels when the phone offers them.
 * Phones place and pair their microphones differently, so nothing assumes which channel is where.
 * After the call: [Mixdown] follows whoever is talking from one channel to the other by measurement,
 * [Leveler] brings the quiet side up, and the result is encoded to .m4a. Working after the call lets
 * the choice look ahead and smooth both ways.
 */
@SuppressLint("MissingPermission") // CallRecorder checks RECORD_AUDIO before starting
class Capture(private val scratch: File) {
    @Volatile private var running = true
    private lateinit var worker: Thread
    private var channels = 1

    /** false when no microphone could be opened */
    fun start(): Boolean {
        val record = open() ?: return false
        channels = record.channelCount
        worker = thread(name = "capture") { record(record) }
        return true
    }

    /** stops the microphones and writes the mixed call to [output]; false when nothing was recorded */
    fun finish(output: FileDescriptor): Boolean {
        running = false
        worker.join(5_000)
        return try {
            if (scratch.length() < Mixdown.FRAME * channels * 2) false else mixAndEncode(output)
        } catch (e: Exception) {
            Log.w(TAG, "mixdown failed", e)
            false
        } finally {
            scratch.delete()
        }
    }

    private fun open(): AudioRecord? {
        // unprocessed first: the voice-call noise suppression would treat the earpiece's sound as noise
        for (source in listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            for (mask in listOf(AudioFormat.CHANNEL_IN_STEREO, AudioFormat.CHANNEL_IN_MONO)) {
                val record = runCatching {
                    val format = AudioFormat.Builder().setSampleRate(Mixdown.RATE).setChannelMask(mask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
                    val size = AudioRecord.getMinBufferSize(Mixdown.RATE, mask, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096) * 4
                    AudioRecord.Builder().setAudioSource(source).setAudioFormat(format).setBufferSizeInBytes(size).build()
                }.getOrNull()
                if (record?.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "source $source, ${record.channelCount} channel(s)")
                    return record
                }
                record?.release()
            }
        }
        return null
    }

    private fun record(record: AudioRecord) {
        val pcm = ShortArray(Mixdown.FRAME * channels)
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        try {
            BufferedOutputStream(scratch.outputStream(), 1 shl 16).use { out ->
                record.startRecording()
                while (running) {
                    val read = record.read(pcm, 0, pcm.size)
                    if (read <= 0) continue
                    bytes.clear()
                    bytes.asShortBuffer().put(pcm, 0, read)
                    out.write(bytes.array(), 0, read * 2)
                }
                record.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "capture failed", e)
        } finally {
            record.release()
        }
    }

    /** frame by frame: [block] gets the two channels (the same one twice on a one-microphone phone) */
    private fun frames(block: (bottom: FloatArray, top: FloatArray) -> Unit) {
        val bottom = FloatArray(Mixdown.FRAME)
        val top = FloatArray(Mixdown.FRAME)
        val raw = ByteArray(Mixdown.FRAME * channels * 2)
        val bottomFilter = HighPass()
        val topFilter = HighPass()
        DataInputStream(BufferedInputStream(scratch.inputStream(), 1 shl 16)).use { input ->
            while (true) {
                try {
                    input.readFully(raw)
                } catch (_: EOFException) {
                    return
                }
                val samples = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                for (i in 0 until Mixdown.FRAME) {
                    bottom[i] = bottomFilter.process(samples.get(i * channels) / 32768f)
                    top[i] = if (channels == 1) bottom[i] else topFilter.process(samples.get(i * channels + 1) / 32768f)
                }
                block(bottom, top)
            }
        }
    }

    private fun mixAndEncode(output: FileDescriptor): Boolean {
        val bottomEnergy = ArrayList<Float>()
        val topEnergy = ArrayList<Float>()
        frames { bottom, top ->
            bottomEnergy += bottom.fold(0f) { sum, x -> sum + x * x } / bottom.size
            topEnergy += top.fold(0f) { sum, x -> sum + x * x } / top.size
        }
        val weights = Mixdown.topWeights(bottomEnergy.toFloatArray(), topEnergy.toFloatArray())
        Log.i(TAG, "${weights.size} frames, second channel used for ${(weights.count { it > 0.5f } * 100) / weights.size.coerceAtLeast(1)}%")

        // this phone's own hiss, as measured in this call, rather than one level for every phone
        val hiss = kotlin.math.sqrt(minOf(Mixdown.noiseFloor(bottomEnergy.toFloatArray()), Mixdown.noiseFloor(topEnergy.toFloatArray())))
        val leveler = Leveler(Mixdown.RATE, gate = (hiss * 2.5f).coerceIn(0.0005f, 0.02f))
        val encoder = Encoder(output)
        val mixed = ShortArray(Mixdown.FRAME)
        var index = 0
        frames { bottom, top ->
            val from = weights.getOrElse(index - 1) { weights[0] }
            val to = weights[index.coerceAtMost(weights.lastIndex)]
            for (i in 0 until Mixdown.FRAME) {
                // the weight glides across the frame so neighbouring frames join without a step
                val w = from + (to - from) * i / Mixdown.FRAME
                val x = w * top[i] + (1f - w) * bottom[i]
                mixed[i] = (leveler.process(x) * 32767f).toInt().toShort()
            }
            encoder.write(mixed)
            index++
        }
        return encoder.finish()
    }

    /** removes handling rumble and the microphone's DC offset (below about 80 Hz) before anything is measured */
    private class HighPass {
        private val a = 1f / (1f + 2f * Math.PI.toFloat() * 80f / Mixdown.RATE)
        private var lastIn = 0f
        private var lastOut = 0f
        fun process(x: Float): Float {
            lastOut = a * (lastOut + x - lastIn)
            lastIn = x
            return lastOut
        }
    }

    /** mono 16-bit PCM in, AAC in an .m4a out */
    private class Encoder(output: FileDescriptor) {
        private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, Mixdown.RATE, 1).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 48_000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Mixdown.FRAME * 2)
            }
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        private val muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private val info = MediaCodec.BufferInfo()
        private var track = -1
        private var samples = 0L
        private var wrote = false

        fun write(pcm: ShortArray) {
            val index = codec.dequeueInputBuffer(-1)
            codec.getInputBuffer(index)!!.apply { clear(); order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm) }
            codec.queueInputBuffer(index, 0, pcm.size * 2, samples * 1_000_000L / Mixdown.RATE, 0)
            samples += pcm.size
            drain(end = false)
        }

        fun finish(): Boolean {
            val index = codec.dequeueInputBuffer(-1)
            codec.queueInputBuffer(index, 0, 0, samples * 1_000_000L / Mixdown.RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(end = true)
            codec.stop()
            codec.release()
            runCatching { if (track >= 0) muxer.stop() }
            muxer.release()
            return wrote
        }

        private fun drain(end: Boolean) {
            while (true) {
                val index = codec.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                    }
                    index >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && track >= 0) {
                            muxer.writeSampleData(track, codec.getOutputBuffer(index)!!, info)
                            wrote = true
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                    !end -> return
                }
            }
        }
    }

    companion object {
        private const val TAG = "Capture"
    }
}
