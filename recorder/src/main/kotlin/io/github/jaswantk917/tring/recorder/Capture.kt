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
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * During the call: the raw microphones to a scratch file, two channels when the phone offers them.
 * Phones place and pair their microphones differently, so nothing assumes which channel is where.
 * After the call: [Mixdown] follows whoever is talking from one channel to the other by measurement,
 * [Denoiser] takes out the hiss and marks when anyone talks, [Voices] tells you and the caller
 * apart, each gets its own [Leveler] so the caller comes up to your loudness, and the result is
 * encoded to .m4a. Working after the call lets every choice look ahead and smooth both ways.
 *
 * [stats] gets one line of numbers per call, and [keep] the raw microphones of the last call (its
 * first five minutes), both in the app's own folder that a computer can read over USB, so a bad
 * recording can be traced and the processing replayed on it.
 */
@SuppressLint("MissingPermission") // CallRecorder checks RECORD_AUDIO before starting
class Capture(private val scratch: File, private val keep: File? = null, private val stats: File? = null) {
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

    /** stops the microphones and writes the mixed call to [output], and who talked when to [turns]; false when nothing was recorded */
    fun finish(output: FileDescriptor, turns: File? = null, onStep: (Step) -> Unit = {}): Boolean {
        running = false
        worker.join(5_000)
        return try {
            if (scratch.length() < Mixdown.FRAME * channels * 2) false else mixAndEncode(output, turns, onStep)
        } catch (e: Exception) {
            Log.w(TAG, "mixdown failed", e)
            false
        } finally {
            if (keep == null || !scratch.renameTo(keep)) scratch.delete()
            else if (keep.length() > KEEP_BYTES * channels) RandomAccessFile(keep, "rw").use { it.setLength(KEEP_BYTES * channels) }
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

    /** what the mixdown is doing, for the "Saving the call" note */
    enum class Step(val label: String) {
        Following("Following who's talking"),
        Merging("Merging the two microphones"),
        Cleaning("Taking out the background hiss"),
        Voices("Telling your voice from theirs"),
        Levelling("Evening out the loudness"),
    }

    private fun mixAndEncode(output: FileDescriptor, turns: File?, onStep: (Step) -> Unit): Boolean {
        // 1: how loud each microphone is, frame by frame, to follow whoever is talking between them
        onStep(Step.Following)
        val bottomEnergy = ArrayList<Float>()
        val topEnergy = ArrayList<Float>()
        frames { bottom, top ->
            bottomEnergy += bottom.fold(0f) { sum, x -> sum + x * x } / bottom.size
            topEnergy += top.fold(0f) { sum, x -> sum + x * x } / top.size
        }
        val weights = Mixdown.topWeights(bottomEnergy.toFloatArray(), topEnergy.toFloatArray())

        // 2: the two microphones merged into one
        onStep(Step.Merging)
        val mixed = File(scratch.path + ".mixed")
        val clean = File(scratch.path + ".clean")
        try {
            val bytes = ByteBuffer.allocate(Mixdown.FRAME * 2).order(ByteOrder.LITTLE_ENDIAN)
            var index = 0
            BufferedOutputStream(mixed.outputStream(), 1 shl 16).use { out ->
                frames { bottom, top ->
                    val from = weights.getOrElse(index - 1) { weights[0] }
                    val to = weights[index.coerceAtMost(weights.lastIndex)]
                    bytes.clear()
                    for (i in 0 until Mixdown.FRAME) {
                        // the weight glides across the frame so neighbouring frames join without a step
                        val w = from + (to - from) * i / Mixdown.FRAME
                        bytes.putShort(((w * top[i] + (1f - w) * bottom[i]).coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                    }
                    out.write(bytes.array())
                    index++
                }
            }

            // 3: the hiss taken out, and when anyone is talking
            onStep(Step.Cleaning)
            val denoised = Denoiser.run(mixed, clean)
            mixed.delete()

            // 4: which voice each frame is
            onStep(Step.Voices)
            val meter = Voices.Meter(Mixdown.RATE)
            val shapes = ArrayList<Voices.Shape>()
            pcmFrames(clean) { shapes += meter.shape(it) }
            val talking = BooleanArray(shapes.size) { denoised.speechAt(it * Mixdown.FRAME + Mixdown.FRAME / 2) }
            val caller = Voices.callerWeights(shapes, talking)
            report(weights, denoised, shapes, talking, caller)
            turns?.let { runCatching { Turns.write(Turns.from(caller, talking), it) } }

            onStep(Step.Levelling)
            // 5: each voice levelled on its own, the caller's thinner sound lifted where speech is made out;
            // the gates sit over what's left of the hiss after cleaning
            val residual = denoised.noise * RESIDUAL
            val you = Leveler(Mixdown.RATE, gate = (residual * 3f).coerceIn(0.0003f, 0.02f))
            val them = Leveler(Mixdown.RATE, gate = (residual * 2f).coerceIn(0.0002f, 0.02f), maxGain = CALLER_MAX_GAIN)
            val presence = Biquad.peak(Mixdown.RATE, 2_000f, 4f)
            val encoder = Encoder(output)
            val out = ShortArray(Mixdown.FRAME)
            var f = 0
            pcmFrames(clean) { frame ->
                val c = caller[f.coerceAtMost(caller.lastIndex)]
                for (i in 0 until Mixdown.FRAME) {
                    val x = frame[i]
                    val speech = denoised.speechAt(f * Mixdown.FRAME + i)
                    val y = c * them.process(presence.process(x), listen = c > 0.5f, speech = speech) +
                        (1f - c) * you.process(x, listen = c <= 0.5f, speech = speech)
                    out[i] = (y * 32767f).toInt().toShort()
                }
                encoder.write(out)
                f++
            }
            return encoder.finish()
        } finally {
            mixed.delete()
            clean.delete()
        }
    }

    /** mono 16-bit PCM, one [Mixdown.FRAME] at a time */
    private fun pcmFrames(file: File, block: (FloatArray) -> Unit) {
        val frame = FloatArray(Mixdown.FRAME)
        val raw = ByteArray(Mixdown.FRAME * 2)
        DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 16)).use { input ->
            while (true) {
                try {
                    input.readFully(raw)
                } catch (_: EOFException) {
                    return
                }
                val samples = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                for (i in 0 until Mixdown.FRAME) frame[i] = samples.get(i) / 32768f
                block(frame)
            }
        }
    }

    /** one line per call: enough to tell afterwards why a recording sounds the way it does */
    private fun report(weights: FloatArray, denoised: Denoiser.Result, shapes: List<Voices.Shape>, talking: BooleanArray, caller: FloatArray) {
        val spoken = shapes.indices.filter { talking[it] }
        fun median(frames: List<Int>) = frames.map { shapes[it].levelDb }.sorted().let { if (it.isEmpty()) Float.NaN else it[it.size / 2] }
        val you = median(spoken.filter { caller[it] <= 0.5f })
        val them = median(spoken.filter { caller[it] > 0.5f })
        val line = String.format(
            Locale.ROOT,
            "%s  %ds  %d mic  second mic %d%%  hiss %.0f dB  talking %d%%  caller %d%% of it  you %.0f dB  caller %.0f dB",
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date()),
            shapes.size / 50, channels, percent(weights), denoised.noiseDb,
            spoken.size * 100 / shapes.size.coerceAtLeast(1),
            spoken.count { caller[it] > 0.5f } * 100 / spoken.size.coerceAtLeast(1),
            you, them,
        )
        Log.i(TAG, line)
        val file = stats ?: return
        runCatching {
            // a few hundred calls at most, so it never grows past a few tens of KB
            val lines = if (file.exists()) file.readLines().takeLast(300) else emptyList()
            file.writeText((lines + line).joinToString("\n", postfix = "\n"))
        }
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
        /** the hiss left after [Denoiser]: at most 15 dB down */
        private const val RESIDUAL = 0.18f
        /** five minutes of one channel of the raw call */
        private const val KEEP_BYTES = 5L * 60 * Mixdown.RATE * 2
    }
}
