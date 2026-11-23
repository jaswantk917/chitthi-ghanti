package io.github.jaswantk917.tring.recorder

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AI4Bharat's IndicConformer (MIT), its Hindi part with an 8-bit encoder: fetched when asked for,
 * as one pack from this app's GitHub release, checked against its digest, then unpacked. A
 * download cut short picks up where it stopped.
 */
object IndicModel {
    private const val NAME = "indic-hi-1"
    private const val ADDRESS = "https://github.com/jaswantk917/tring-recorder/releases/download/model-$NAME/$NAME.zip"
    const val SIZE = 925_772_266L
    private const val SHA256 = "49132f183cf53c50673c078fc4028e9b20856b7bd32c4201181c323fc881816b"
    // written last, so a pack only half unpacked is never taken for one that's in
    private const val READY = "ready"
    // a "4 GB" phone reports about 3.6 GB, a "3 GB" one under 3.1; the model alone takes 1.1 to 1.25
    private const val LEAST_RAM = 3_300_000_000L

    sealed interface State {
        data object Missing : State
        data class Downloading(val done: Long) : State
        data object Checking : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Missing)
    val state: StateFlow<State> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private fun root(context: Context) = context.getExternalFilesDir(null) ?: context.filesDir

    fun dir(context: Context) = File(root(context), NAME)

    private fun pack(context: Context) = File(root(context), "$NAME.zip.part")

    fun ready(context: Context) = File(dir(context), READY).exists()

    /** whether to offer the model on this phone at all */
    fun runsHere(context: Context): Boolean {
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        return runsOn(memory.totalMem, runCatching { File("/proc/cpuinfo").readText() }.getOrDefault(""))
    }

    /** 4 GB of RAM, and the dot-product instructions (Cortex-A55 on), without which its 8-bit maths crawls and heats the phone */
    internal fun runsOn(ramBytes: Long, cpuinfo: String) = ramBytes >= LEAST_RAM &&
        cpuinfo.lineSequence().any { it.startsWith("Features") && "asimddp" in it.split(Regex("\\s+")) }

    fun refresh(context: Context) {
        if (job?.isActive == true) return
        _state.value = if (ready(context)) State.Ready else State.Missing
    }

    fun download(context: Context) {
        if (job?.isActive == true) return
        // already in (put there some other way): say so instead of leaving an old failure up
        if (ready(context)) {
            _state.value = State.Ready
            return
        }
        val app = context.applicationContext
        job = scope.launch {
            try {
                fetch(app)
                _state.value = State.Checking
                if (sha256(pack(app)) != SHA256) {
                    pack(app).delete()
                    error("The download came out damaged. Try again.")
                }
                unpack(app)
                pack(app).delete()
                _state.value = State.Ready
            } catch (e: CancellationException) {
                _state.value = State.Missing
                throw e
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: "The download failed. Try again.")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = State.Missing
    }

    fun delete(context: Context) {
        job?.cancel()
        dir(context).deleteRecursively()
        pack(context).delete()
        _state.value = State.Missing
    }

    private suspend fun fetch(context: Context) {
        val part = pack(context)
        // all here already: the app was closed while checking it
        if (part.length() == SIZE) return
        var done = part.length().takeIf { it < SIZE } ?: 0L
        if (done == 0L) part.delete()
        // the pack, and the model unpacked beside it until the pack goes
        val free = root(context).usableSpace
        if (free < 2 * SIZE - done) error("Needs %.1f GB free while it unpacks. This phone has %.1f GB.".format((2 * SIZE - done) / 1e9, free / 1e9))
        _state.value = State.Downloading(done)
        val connection = (URL(ADDRESS).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            // a compressed reply would make the byte offset for resuming meaningless
            setRequestProperty("Accept-Encoding", "identity")
            if (done > 0) setRequestProperty("Range", "bytes=$done-")
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> Unit
                // the server sent all of it, not the rest: start the file again
                HttpURLConnection.HTTP_OK -> done = 0
                else -> error("The download failed (${connection.responseCode}). Try again.")
            }
            connection.inputStream.use { input ->
                FileOutputStream(part, done > 0).use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var shown = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (done - shown >= 1_000_000) {
                            shown = done
                            _state.value = State.Downloading(done)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (done != SIZE) error("The download stopped early. Try again to pick it up.")
    }

    private fun unpack(context: Context) {
        val dir = dir(context).apply { deleteRecursively(); mkdirs() }
        ZipInputStream(pack(context).inputStream().buffered(1 shl 16)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // the pack has only plain names; anything else isn't ours
                require(!entry.name.contains('/') && !entry.name.contains("..")) { "The download isn't the model" }
                File(dir, entry.name).outputStream().use { zip.copyTo(it, 1 shl 16) }
            }
        }
        File(dir, READY).writeText(NAME)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * NeMo's log-mel features, as AI4Bharat's preprocessor.ts makes them: pre-emphasis, a reflected
 * 512-point STFT every 10 ms over a 25 ms window, 80 mel bands, log, then each band normalised over
 * the piece. [window] (400) and [bank] (257 × 80) come from that preprocessor.
 */
class Mel(private val window: FloatArray, private val bank: FloatArray) {
    private val twiddleCos = DoubleArray(FFT / 2) { cos(-2 * PI * it / FFT) }
    private val twiddleSin = DoubleArray(FFT / 2) { sin(-2 * PI * it / FFT) }
    private val reversed = IntArray(FFT) { Integer.reverse(it) ushr (32 - 9) }

    /** [BANDS] × frames, band by band, and the number of frames; [samples] must be over 256 long */
    fun features(samples: FloatArray): Pair<FloatArray, Int> {
        val n = samples.size
        fun emphasised(i: Int) = if (i == 0) samples[0].toDouble() else samples[i] - 0.97 * samples[i - 1]
        val padded = DoubleArray(n + FFT)
        for (i in 0 until n) padded[i + PAD] = emphasised(i)
        for (i in 1..PAD) {
            padded[PAD - i] = emphasised(i)
            padded[PAD + n - 1 + i] = emphasised(n - 1 - i)
        }
        val frames = 1 + n / HOP
        val logMel = Array(BANDS) { DoubleArray(frames) }
        val re = DoubleArray(FFT)
        val im = DoubleArray(FFT)
        val offset = (FFT - window.size) / 2
        for (f in 0 until frames) {
            re.fill(0.0)
            im.fill(0.0)
            for (k in window.indices) re[offset + k] = padded[f * HOP + offset + k] * window[k]
            fft(re, im)
            for (band in 0 until BANDS) {
                var energy = 0.0
                for (bin in 0..FFT / 2) energy += (re[bin] * re[bin] + im[bin] * im[bin]) * bank[bin * BANDS + band]
                logMel[band][f] = ln(energy + LOG_GUARD)
            }
        }
        val out = FloatArray(BANDS * frames)
        for (band in 0 until BANDS) {
            val row = logMel[band]
            val mean = row.average()
            var spread = 0.0
            for (v in row) spread += (v - mean) * (v - mean)
            val std = sqrt((spread / (frames - 1)).coerceAtLeast(VARIANCE_FLOOR))
            for (f in 0 until frames) out[band * frames + f] = ((row[f] - mean) / (std + STD_GUARD)).toFloat()
        }
        return out to frames
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until FFT) {
            val j = reversed[i]
            if (j > i) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var size = 2
        while (size <= FFT) {
            val half = size / 2
            val step = FFT / size
            for (start in 0 until FFT step size) {
                for (k in 0 until half) {
                    val wr = twiddleCos[k * step]
                    val wi = twiddleSin[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
            }
            size *= 2
        }
    }

    companion object {
        const val BANDS = 80
        const val HOP = 160
        private const val FFT = 512
        private const val PAD = FFT / 2
        private const val LOG_GUARD = 5.960464477539063e-08
        private const val VARIANCE_FLOOR = 5.9604644775390625e-08
        private const val STD_GUARD = 1e-5
    }
}

/**
 * Where a call is cut for IndicConformer, which hears nothing in a piece much over 12 s: each of
 * the recorder's turns (so every line keeps who said it), or the whole call when it has none, cut
 * again at its quietest moment while it runs longer than that.
 */
object IndicPieces {
    data class Piece(val startMs: Int, val endMs: Int, val them: Boolean?)

    const val LONGEST_MS = 12_000
    private const val SHORTEST_MS = 7_000

    /** [quietest]: the quietest moment between two times (ms) */
    fun of(turns: List<Turn>?, callMs: Int, quietest: (Int, Int) -> Int): List<Piece> {
        val stretches = turns?.takeIf { it.isNotEmpty() }?.map { Piece(it.startMs, it.endMs.coerceAtMost(callMs), it.them) }
            ?: listOf(Piece(0, callMs, null))
        return stretches.filter { it.endMs > it.startMs }.flatMap { stretch ->
            val pieces = ArrayList<Piece>()
            var start = stretch.startMs
            while (stretch.endMs - start > LONGEST_MS) {
                val cut = quietest(start + SHORTEST_MS, start + LONGEST_MS).coerceIn(start + SHORTEST_MS, start + LONGEST_MS)
                pieces += Piece(start, cut, stretch.them)
                start = cut
            }
            pieces += Piece(start, stretch.endMs, stretch.them)
            pieces
        }
    }
}

/**
 * IndicConformer itself, on ONNX Runtime: [hear] takes a piece of 16 kHz audio to Hindi text. Runs
 * in [IndicService]'s process only; the best part of a gigabyte while it's open.
 */
class IndicEngine(dir: File) : AutoCloseable {
    data class Heard(val text: String, val confidence: Float)

    // its telemetry off before any session, as the manifest keeps its uploader from starting
    private val env = OrtEnvironment.getEnvironment().apply { setTelemetry(false) }
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(THREADS) }
    private val encoder = env.createSession(File(dir, "encoder.onnx").path, options)
    private val jointEnc = env.createSession(File(dir, "joint_enc.onnx").path, options)
    private val decoder = env.createSession(File(dir, "rnnt_decoder.onnx").path, options)
    private val jointPred = env.createSession(File(dir, "joint_pred.onnx").path, options)
    private val preNet = env.createSession(File(dir, "joint_pre_net.onnx").path, options)
    private val postNet = env.createSession(File(dir, "joint_post_net_hi.onnx").path, options)
    private val vocab = JSONArray(File(dir, "vocab.json").readText()).let { all -> List(all.length()) { all.getString(it) } }
    private val mel = Mel(floats(File(dir, "window.f32")), floats(File(dir, "mel.f32")))

    fun hear(samples: FloatArray): Heard {
        // a cough's worth: nothing the model can make out, and too short to pad
        if (samples.size <= 400) return Heard("", 0f)
        val (features, frames) = mel.features(samples)
        val encoded = encoder.run(
            mapOf(
                "audio_signal" to OnnxTensor.createTensor(env, FloatBuffer.wrap(features), longArrayOf(1, Mel.BANDS.toLong(), frames.toLong())),
                "length" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(frames.toLong())), longArrayOf(1)),
            ),
        ).use { result ->
            @Suppress("UNCHECKED_CAST")
            (result.get(0).value as Array<Array<FloatArray>>)[0]
        }
        val steps = encoded[0].size
        val sequence = FloatBuffer.allocate(steps * encoded.size)
        for (t in 0 until steps) for (c in encoded.indices) sequence.put(encoded[c][t])
        sequence.rewind()
        val joined = jointEnc.run(mapOf("input" to OnnxTensor.createTensor(env, sequence, longArrayOf(1, steps.toLong(), encoded.size.toLong())))).use {
            @Suppress("UNCHECKED_CAST")
            (it.get(0).value as Array<Array<FloatArray>>)[0]
        }
        val (tokens, sure) = greedy(joined)
        return Heard(text(tokens, vocab), if (sure.isEmpty()) 0f else sure.average().toFloat())
    }

    /** greedy RNN-T as AI4Bharat's model_onnx.py does it: at most ten tokens a frame, a blank moves on; and how sure each token was */
    private fun greedy(joined: Array<FloatArray>): Pair<List<Int>, List<Float>> {
        val tokens = ArrayList<Int>()
        val sure = ArrayList<Float>()
        var last = SOS
        var hidden = FloatArray(STATE)
        var cell = FloatArray(STATE)
        val stateShape = longArrayOf(2, 1, 640)
        for (frame in joined) {
            var added = 0
            while (added < MAX_SYMBOLS) {
                val step = decoder.run(
                    mapOf(
                        "targets" to OnnxTensor.createTensor(env, IntBuffer.wrap(intArrayOf(last)), longArrayOf(1, 1)),
                        "target_length" to OnnxTensor.createTensor(env, IntBuffer.wrap(intArrayOf(1)), longArrayOf(1)),
                        "states.1" to OnnxTensor.createTensor(env, FloatBuffer.wrap(hidden), stateShape),
                        "onnx::Slice_3" to OnnxTensor.createTensor(env, FloatBuffer.wrap(cell), stateShape),
                    ),
                )
                val (g, nextHidden, nextCell) = step.use { result ->
                    @Suppress("UNCHECKED_CAST")
                    val out = (result.get("outputs").get().value as Array<Array<FloatArray>>)[0]
                    Triple(FloatArray(out.size) { out[it][0] }, state(result.get("states").get()), state(result.get("162").get()))
                }
                val pred = one(jointPred, g)
                val logits = one(postNet, one(preNet, FloatArray(pred.size) { frame[it] + pred[it] }))
                val token = logits.indices.maxBy { logits[it] }
                if (token == BLANK) break
                tokens += token
                sure += probability(logits, token)
                last = token
                hidden = nextHidden
                cell = nextCell
                added++
            }
        }
        return tokens to sure
    }

    private fun one(session: OrtSession, input: FloatArray): FloatArray =
        session.run(mapOf("input" to OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 1, input.size.toLong())))).use {
            @Suppress("UNCHECKED_CAST")
            (it.get(0).value as Array<Array<FloatArray>>)[0][0]
        }

    private fun state(value: ai.onnxruntime.OnnxValue): FloatArray {
        @Suppress("UNCHECKED_CAST")
        val layers = value.value as Array<Array<FloatArray>>
        return FloatArray(STATE) { layers[it / 640][0][it % 640] }
    }

    override fun close() {
        listOf(encoder, jointEnc, decoder, jointPred, preNet, postNet).forEach { it.close() }
        options.close()
    }

    companion object {
        // the phone's big cores, when it runs in front ([IndicService] is a foreground service)
        private const val THREADS = 4
        private const val BLANK = 256
        private const val SOS = 5632
        private const val MAX_SYMBOLS = 10
        private const val STATE = 2 * 640

        /** the tokens as text: "▁" starts a word */
        fun text(tokens: List<Int>, vocab: List<String>) = tokens.joinToString("") { vocab[it] }.replace('▁', ' ').trim()

        /** softmax of [logits] at [token] */
        fun probability(logits: FloatArray, token: Int): Float {
            val top = logits.max()
            var sum = 0.0
            for (v in logits) sum += exp((v - top).toDouble())
            return (exp((logits[token] - top).toDouble()) / sum).toFloat()
        }

        private fun floats(file: File): FloatArray {
            val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            return FloatArray(buffer.remaining()).also { buffer.get(it) }
        }
    }
}
