package io.github.jaswantk917.tring.recorder

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Takes the steady hiss out of a call, learning it from that call: each pitch's noise is the
 * quietest that pitch got within about a second either way, so it follows the room changing, or a
 * switch to speaker, and nothing is tuned to one phone. Speech is kept with a Wiener gain that
 * follows a smoothed estimate of how far each pitch stands over the noise, which avoids the
 * twinkling "musical noise" of plain subtraction, and no pitch is cut by more than [FLOOR_DB], so
 * voices never turn watery. It also says, every 16 ms, whether anyone is talking.
 *
 * Works on a whole file after the call, so its window looks ahead as well as back.
 */
internal object Denoiser {
    const val SIZE = 512
    const val HOP = SIZE / 2
    private const val BINS = SIZE / 2 + 1
    /** hops per block of minima: about 0.19 s */
    private const val BLOCK = 12
    /** blocks either side the noise is looked for in: about a second each way */
    private const val SPAN = 5
    /** hops of history the quantile looks over */
    private const val RING = (2 * SPAN + 1) * BLOCK
    /** the share of the time a pitch's hiss is assumed to show on its own: fast speech leaves about 15% */
    private const val QUANTILE = 0.1f
    /**
     * hiss sits at that quantile under its mean; this brings it back. Measured on white noise: 9.6,
     * as theory has it for a tenth of an exponential spread (-ln 0.9 = 0.105)
     */
    private const val BIAS = 9.6f
    /** at most 15 dB taken from any pitch */
    private const val FLOOR_DB = -15.0
    private val floor = Math.pow(10.0, FLOOR_DB / 20)
    /** how much each pitch's speech estimate leans on the previous hop: the musical-noise guard */
    private const val CARRY = 0.98
    /** over the voice band, power this many times the noise on average means someone is talking (3 dB) */
    private const val SPEECH_SNR = 2.0
    /** hops a word's tail is held on for: about 100 ms */
    private const val HANG = 6

    class Result(
        /** per hop, centred on sample hop × [HOP]: someone is talking */
        val speech: BooleanArray,
        /** the hiss before cleaning, as RMS on a -1..1 scale: the call's typical level */
        val noise: Float,
        val bandSnr: FloatArray = FloatArray(0),
    ) {
        fun speechAt(sample: Int) = speech[((sample + HOP / 2) / HOP).coerceIn(0, speech.lastIndex)]
        val noiseDb get() = 20 * log10(noise + 1e-9f)
    }

    /** [input] and [output]: mono 16-bit little-endian PCM at [Mixdown.RATE]; the output is the same length */
    fun run(input: File, output: File): Result {
        val length = (input.length() / 2).toInt()
        val hops = length / HOP + 2
        val window = DoubleArray(SIZE) { sqrt(0.5 - 0.5 * cos(2 * PI * it / SIZE)) }
        val fft = Fft(SIZE)
        val re = DoubleArray(SIZE)
        val im = DoubleArray(SIZE)

        // 1: each pitch's hiss per block: the level it sits under a fifth of the time within about a
        // second either way. A quantile, not the minimum: fast speech leaves only short gaps, and the
        // quietest point between two syllables is still part voice
        val noise = ArrayList<FloatArray>()
        val ring = Array(RING) { FloatArray(BINS) }
        val ringHop = IntArray(RING) { -1 }
        val values = FloatArray(RING)
        fun settle(block: Int) {
            val from = (block - SPAN) * BLOCK
            val to = (block + SPAN + 1) * BLOCK
            val slots = ringHop.indices.filter { ringHop[it] >= 0 && ringHop[it] in from until to }
            noise += FloatArray(BINS) { k ->
                if (slots.isEmpty()) return@FloatArray 1e-14f
                for ((i, slot) in slots.withIndex()) values[i] = ring[slot][k]
                java.util.Arrays.sort(values, 0, slots.size)
                max(values[(slots.size * QUANTILE).toInt()] * BIAS, 1e-14f)
            }
        }
        frames(input, hops, window) { hop, frame ->
            // the first and last hops are half padding, quieter than any real hiss
            if (hop != 0 && hop != hops - 1) {
                spectrum(fft, frame, re, im)
                val slot = hop % RING
                // each hop's own power, unsmoothed: smoothing smears a syllable's end into the gap after it,
                // and fast speech then reads as hiss (1.2 dB of voice lost at 0.5); the quantile is steady enough
                for (k in 0 until BINS) ring[slot][k] = (re[k] * re[k] + im[k] * im[k]).toFloat()
                ringHop[slot] = hop
            }
            // a block's window is whole once the block SPAN after it is
            if (hop % BLOCK == BLOCK - 1 && hop / BLOCK >= SPAN) settle(hop / BLOCK - SPAN)
        }
        while (noise.size <= (hops - 1) / BLOCK) settle(noise.size)

        // 2: the Wiener gain per pitch, overlap-added back into sound
        val speech = BooleanArray(hops)
        val snrs = FloatArray(hops)
        val noiseLevels = FloatArray(hops)
        val voiceBand = (300 * SIZE / Mixdown.RATE)..(3_400 * SIZE / Mixdown.RATE)
        val lastClean = DoubleArray(BINS)
        val sum = DoubleArray(SIZE)
        val pcm = ByteBuffer.allocate(HOP * 2).order(ByteOrder.LITTLE_ENDIAN)
        var written = 0
        BufferedOutputStream(output.outputStream(), 1 shl 16).use { out ->
            frames(input, hops, window) { hop, frame ->
                spectrum(fft, frame, re, im)
                val n = noise[(hop / BLOCK).coerceAtMost(noise.lastIndex)]
                var bandSnr = 0.0
                var noisePower = 0.0
                for (k in 0 until BINS) {
                    val power = re[k] * re[k] + im[k] * im[k]
                    val snr = power / n[k]
                    if (k in voiceBand) bandSnr += snr
                    noisePower += if (k == 0 || k == BINS - 1) n[k] else 2 * n[k]
                    val prior = CARRY * lastClean[k] + (1 - CARRY) * max(snr - 1, 0.0)
                    val gain = max(prior / (1 + prior), floor)
                    lastClean[k] = gain * gain * snr
                    re[k] *= gain; im[k] *= gain
                    if (k in 1 until BINS - 1) { re[SIZE - k] = re[k]; im[SIZE - k] = -im[k] }
                }
                snrs[hop] = (bandSnr / (voiceBand.last - voiceBand.first + 1)).toFloat()
                speech[hop] = snrs[hop] > SPEECH_SNR
                // the window's power sums to SIZE / 2, so this is the hiss's mean square in the signal
                noiseLevels[hop] = sqrt(noisePower * 2 / (SIZE.toDouble() * SIZE)).toFloat()
                fft.inverse(re, im)
                for (i in 0 until SIZE) sum[i] += re[i] * window[i]
                // samples hop × HOP - HOP until hop × HOP are now whole
                val from = hop * HOP - HOP
                pcm.clear()
                var count = 0
                for (i in 0 until HOP) {
                    val at = from + i
                    if (at in 0 until length) {
                        pcm.putShort((sum[i].coerceIn(-1.0, 1.0) * 32767).toInt().toShort())
                        count++
                    }
                }
                if (count > 0) { out.write(pcm.array(), 0, count * 2); written += count }
                System.arraycopy(sum, HOP, sum, 0, HOP)
                sum.fill(0.0, HOP, SIZE)
            }
        }
        check(written == length) { "wrote $written of $length samples" }
        return Result(hold(speech), noiseLevels.sortedArray()[noiseLevels.size / 2], snrs)
    }

    /** a word's tail stays on for [HANG] hops, and its start a couple of hops early */
    private fun hold(raw: BooleanArray) = BooleanArray(raw.size) { i ->
        (i - HANG..i + 2).any { it in raw.indices && raw[it] }
    }

    private fun spectrum(fft: Fft, frame: DoubleArray, re: DoubleArray, im: DoubleArray) {
        System.arraycopy(frame, 0, re, 0, SIZE)
        im.fill(0.0)
        fft.forward(re, im)
    }

    /** hop by hop, the windowed [SIZE] samples centred on sample hop × [HOP], zeros past either end */
    private fun frames(input: File, hops: Int, window: DoubleArray, block: (hop: Int, frame: DoubleArray) -> Unit) {
        val buffer = DoubleArray(SIZE)
        val frame = DoubleArray(SIZE)
        val raw = ByteArray(HOP * 2)
        DataInputStream(BufferedInputStream(input.inputStream(), 1 shl 16)).use { stream ->
            var ended = false
            for (hop in 0 until hops) {
                System.arraycopy(buffer, HOP, buffer, 0, HOP)
                val got = if (ended) 0 else readUpTo(stream, raw).also { if (it < raw.size) ended = true }
                val samples = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                for (i in 0 until HOP) buffer[HOP + i] = if (i * 2 < got) samples.get(i) / 32768.0 else 0.0
                for (i in 0 until SIZE) frame[i] = buffer[i] * window[i]
                block(hop, frame)
            }
        }
    }

    private fun readUpTo(stream: DataInputStream, into: ByteArray): Int {
        var total = 0
        while (total < into.size) {
            val n = try { stream.read(into, total, into.size - total) } catch (_: EOFException) { -1 }
            if (n < 0) break
            total += n
        }
        return total
    }
}
