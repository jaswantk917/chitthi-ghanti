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
import kotlin.math.sqrt

/**
 * During the call: the raw microphones to a scratch file, two channels when the phone offers them.
 * Phones place and pair their microphones differently, so nothing assumes which channel is where.
 * After the call: [Mixdown] follows whoever is talking from one channel to the other by measurement,
 * [Voices] tells you and the caller apart, each gets its own [Leveler] so the caller comes up to
 * your loudness, and the result is encoded to .m4a. Working after the call lets every choice look
 * ahead and smooth both ways.
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
        // during a call Android feeds an Accessibility service real sound only from VOICE_RECOGNITION;
        // UNPROCESSED and MIC open fine and then record pure silence (Pixel 9, Android 17, 2026-11-21)
        for (source in listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION)) {
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
        // 1: how loud each microphone is, frame by frame, to follow whoever is talking between them
        val bottomEnergy = ArrayList<Float>()
        val topEnergy = ArrayList<Float>()
        frames { bottom, top ->
            bottomEnergy += bottom.fold(0f) { sum, x -> sum + x * x } / bottom.size
            topEnergy += top.fold(0f) { sum, x -> sum + x * x } / top.size
        }
        val weights = Mixdown.topWeights(bottomEnergy.toFloatArray(), topEnergy.toFloatArray())
        // this phone's own hiss, as measured in this call, rather than one level for every phone
        val hissEnergy = minOf(Mixdown.noiseFloor(bottomEnergy.toFloatArray()), Mixdown.noiseFloor(topEnergy.toFloatArray()))
        val hiss = sqrt(hissEnergy)

        // 2: the two microphones merged into one, and which voice each frame is
        val mixedFile = File(scratch.path + ".mixed")
        val meter = Voices.Meter(Mixdown.RATE)
        val shapes = ArrayList<Voices.Shape>()
        val frame = FloatArray(Mixdown.FRAME)
        val bytes = ByteBuffer.allocate(Mixdown.FRAME * 2).order(ByteOrder.LITTLE_ENDIAN)
        var index = 0
        BufferedOutputStream(mixedFile.outputStream(), 1 shl 16).use { out ->
            frames { bottom, top ->
                val from = weights.getOrElse(index - 1) { weights[0] }
                val to = weights[index.coerceAtMost(weights.lastIndex)]
                bytes.clear()
                for (i in 0 until Mixdown.FRAME) {
                    // the weight glides across the frame so neighbouring frames join without a step
                    val w = from + (to - from) * i / Mixdown.FRAME
                    frame[i] = w * top[i] + (1f - w) * bottom[i]
                    bytes.putShort((frame[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                }
                shapes += meter.shape(frame)
                out.write(bytes.array())
                index++
            }
        }
        val caller = Voices.callerWeights(shapes, floorDb = 10f * kotlin.math.log10(hissEnergy + 1e-12f))
        Log.i(
            TAG,
            "$channels channel(s), ${weights.size} frames, second channel ${percent(weights)}%, caller ${percent(caller)}%, " +
                "loudest ${bottomEnergy.max()} / ${topEnergy.max()}, hiss $hissEnergy",
        )

        // 3: each voice levelled on its own, the caller's thinner sound lifted where speech is made out
        val you = Leveler(Mixdown.RATE, gate = (hiss * 2.5f).coerceIn(0.0005f, 0.02f))
        val them = Leveler(Mixdown.RATE, gate = (hiss * 1.5f).coerceIn(0.0003f, 0.02f), maxGain = CALLER_MAX_GAIN)
        val presence = Biquad.peak(Mixdown.RATE, 2_000f, 4f)
        val encoder = Encoder(output)
        val out = ShortArray(Mixdown.FRAME)
        val raw = ByteArray(Mixdown.FRAME * 2)
        try {
            DataInputStream(BufferedInputStream(mixedFile.inputStream(), 1 shl 16)).use { input ->
                for (f in caller.indices) {
                    input.readFully(raw)
                    val samples = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val c = caller[f]
                    for (i in 0 until Mixdown.FRAME) {
                        val x = samples.get(i) / 32768f
                        val lifted = presence.process(x)
                        val y = c * them.process(lifted, listen = c > 0.5f) + (1f - c) * you.process(x, listen = c <= 0.5f)
                        out[i] = (y * 32767f).toInt().toShort()
                    }
                    encoder.write(out)
                }
            }
        } finally {
            mixedFile.delete()
        }
        return encoder.finish()
    }

    private fun percent(weights: FloatArray) = weights.count { it > 0.5f } * 100 / weights.size.coerceAtLeast(1)

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
        /** 36 dB: the caller arrives thin and quiet, measured 8 to 15 dB under you even after 24 dB of lift */
        private const val CALLER_MAX_GAIN = 64f
    }
}
