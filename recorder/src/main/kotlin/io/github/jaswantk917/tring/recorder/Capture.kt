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
import java.io.FileDescriptor
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * The microphones to an .m4a, levelled on the way. Asks for two channels first: on most phones that
 * is the bottom microphone (your mouth) and the top one (beside the earpiece, where the caller
 * leaks out), each levelled on its own and kept apart, since mixing two microphones a few
 * centimetres apart makes voices sound hollow.
 */
@SuppressLint("MissingPermission") // CallRecorder checks RECORD_AUDIO before starting
class Capture(private val output: FileDescriptor) {
    @Volatile private var running = true
    @Volatile private var wrote = false
    private lateinit var worker: Thread

    /** false when no microphone could be opened */
    fun start(): Boolean {
        val record = open() ?: return false
        worker = thread(name = "capture") { run(record) }
        return true
    }

    /** blocks until the file is complete; false when nothing was recorded */
    fun stop(): Boolean {
        running = false
        worker.join(5_000)
        return wrote
    }

    private fun open(): AudioRecord? {
        // unprocessed first: the voice-call noise suppression would treat the earpiece's sound as noise
        for (source in listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            for (mask in listOf(AudioFormat.CHANNEL_IN_STEREO, AudioFormat.CHANNEL_IN_MONO)) {
                val record = runCatching {
                    val format = AudioFormat.Builder().setSampleRate(RATE).setChannelMask(mask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
                    val size = AudioRecord.getMinBufferSize(RATE, mask, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096) * 4
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

    private fun run(record: AudioRecord) {
        val channels = record.channelCount
        val levelers = List(channels) { Leveler(RATE) }
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000 * channels)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME * channels * 2)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        val muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        val info = MediaCodec.BufferInfo()
        val pcm = ShortArray(FRAME * channels)
        var frames = 0L

        fun drain(end: Boolean) {
            while (true) {
                val index = codec.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                    }
                    index >= 0 -> {
                        val buffer = codec.getOutputBuffer(index)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && track >= 0) {
                            muxer.writeSampleData(track, buffer, info)
                            wrote = true
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                    !end -> return
                }
            }
        }

        try {
            record.startRecording()
            while (running) {
                val read = record.read(pcm, 0, pcm.size)
                if (read <= 0) continue
                for (i in 0 until read) {
                    val leveled = levelers[i % channels].process(pcm[i] / 32768f)
                    pcm[i] = (leveled * 32767f).toInt().toShort()
                }
                val input = codec.dequeueInputBuffer(10_000)
                if (input >= 0) {
                    val buffer = codec.getInputBuffer(input)!!
                    buffer.clear()
                    buffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm, 0, read)
                    codec.queueInputBuffer(input, 0, read * 2, frames * 1_000_000L / RATE, 0)
                    frames += read / channels
                }
                drain(end = false)
            }
            record.stop()
            val input = codec.dequeueInputBuffer(10_000)
            if (input >= 0) codec.queueInputBuffer(input, 0, 0, frames * 1_000_000L / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(end = true)
        } catch (e: Exception) {
            Log.w(TAG, "capture failed", e)
        } finally {
            record.release()
            runCatching { codec.stop() }
            codec.release()
            runCatching { if (track >= 0) muxer.stop() }
            muxer.release()
        }
    }

    companion object {
        private const val TAG = "Capture"
        private const val RATE = 48_000
        private const val FRAME = 1024
    }
}
