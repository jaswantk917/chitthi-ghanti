package io.github.jaswantk917.tring.recorder

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt


/**
 * Tells the two voices of a call apart by learning them from that call, so nothing is tuned to one
 * phone, one microphone or one kind of call. At the microphone you are always the near voice: far
 * louder than the other person, who only reaches it through the earpiece or speaker, and usually
 * fuller in bass and treble. Each call's speech frames are split into the two groups that differ
 * most in loudness and spread across pitches, and the quieter group is the other person. When the
 * groups barely differ in loudness, only one voice is there and nobody is treated as the caller.
 *
 * A fixed rule ("the caller has no treble") broke on a Pixel 9 handheld call (2026-11-21): HD voice
 * keeps the caller's treble, and some of your own sounds fell inside the rule, so your voice got
 * the caller's boost.
 */
object Voices {
    class Shape(val levelDb: Float, val tiltDb: Float, val topDb: Float)

    /**
     * Measures one frame's spread across pitches with an FFT, the same way the call it was tuned
     * on was measured; a few biquads read the bands too softly (a phone-band voice came out at
     * -6 dB instead of -11 to -24) for those thresholds to hold.
     */
    class Meter(private val rate: Int) {
        private val re = DoubleArray(SIZE)
        private val im = DoubleArray(SIZE)
        private val window = DoubleArray(Mixdown.FRAME) { 0.5 - 0.5 * cos(2 * PI * it / (Mixdown.FRAME - 1)) }

        fun shape(frame: FloatArray): Shape {
            re.fill(0.0)
            im.fill(0.0)
            for (i in frame.indices) re[i] = frame[i] * window[i]
            fft(re, im)
            var bass = 0.0
            var middle = 0.0
            var top = 0.0
            for (k in 1 until SIZE / 2) {
                val hz = k * rate.toDouble() / SIZE
                val power = re[k] * re[k] + im[k] * im[k]
                when {
                    hz < 100 -> Unit
                    hz < 1_000 -> bass += power
                    hz < 3_400 -> middle += power
                    else -> top += power
                }
            }
            val all = frame.fold(0f) { e, x -> e + x * x } / frame.size
            return Shape(db(all.toDouble()), db(bass) - db(middle), db(top) - db(bass + middle + top))
        }

        private fun db(energy: Double) = (10 * log10(energy + 1e-18)).toFloat()

        companion object {
            private const val SIZE = 512

            /** in-place radix-2 FFT */
            private fun fft(re: DoubleArray, im: DoubleArray) {
                val n = re.size
                var j = 0
                for (i in 1 until n) {
                    var bit = n shr 1
                    while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                    j = j xor bit
                    if (i < j) { re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] } }
                }
                var len = 2
                while (len <= n) {
                    val angle = -2 * PI / len
                    for (i in 0 until n step len) {
                        for (k in 0 until len / 2) {
                            val wr = cos(angle * k); val wi = sin(angle * k)
                            val a = i + k; val b = a + len / 2
                            val xr = re[b] * wr - im[b] * wi; val xi = re[b] * wi + im[b] * wr
                            re[b] = re[a] - xr; im[b] = im[a] - xi
                            re[a] += xr; im[a] += xi
                        }
                    }
                    len = len shl 1
                }
            }
        }
    }

    /**
     * For each frame, how much it is the other person, 0..1. [floorDb]: this call's background
     * level; frames within [SPEECH_DB] of it are pauses and keep the last answer. A short majority
     * vote either side stops single frames from flickering the gain.
     */
    fun callerWeights(shapes: List<Shape>, floorDb: Float): FloatArray {
        val speech = shapes.indices.filter { shapes[it].levelDb > floorDb + SPEECH_DB }
        val far = farFrames(shapes, speech)
        val raw = FloatArray(shapes.size)
        var last = 0f
        for (i in shapes.indices) {
            if (shapes[i].levelDb > floorDb + SPEECH_DB) last = if (i in far) 1f else 0f
            raw[i] = last
        }
        val voted = FloatArray(raw.size) { i ->
            val from = (i - VOTE).coerceAtLeast(0)
            val to = (i + VOTE).coerceAtMost(raw.lastIndex)
            if ((from..to).sumOf { raw[it].toDouble() } * 2 > to - from + 1) 1f else 0f
        }
        // a few frames' crossfade at each hand-over, so the gain change never clicks
        val a = 1f - exp(-1f / 2f)
        for (i in 1 until voted.size) voted[i] = voted[i - 1] + a * (voted[i] - voted[i - 1])
        for (i in voted.size - 2 downTo 0) voted[i] = voted[i + 1] + a * (voted[i] - voted[i + 1])
        return voted
    }

    /** two-group k-means over loudness and spread, each scaled by its spread in this call */
    private fun farFrames(shapes: List<Shape>, speech: List<Int>): Set<Int> {
        if (speech.size < MIN_FRAMES) return emptySet()
        val features = speech.map { i -> floatArrayOf(shapes[i].levelDb, shapes[i].tiltDb, shapes[i].topDb) }
        val scale = FloatArray(3) { d ->
            val mean = features.map { it[d] }.average().toFloat()
            sqrt(features.map { (it[d] - mean).pow(2) }.average().toFloat()).coerceAtLeast(1f)
        }
        // loudness is the one cue that holds on every phone, so it counts double
        val weight = floatArrayOf(2f, 1f, 1f)
        val points = features.map { f -> FloatArray(3) { d -> f[d] / scale[d] * weight[d] } }
        val byLevel = speech.indices.sortedBy { features[it][0] }
        var quiet = mean(byLevel.take(byLevel.size / 4).map { points[it] })
        var loud = mean(byLevel.takeLast(byLevel.size / 4).map { points[it] })
        var isFar = BooleanArray(points.size)
        repeat(30) {
            isFar = BooleanArray(points.size) { distance(points[it], quiet) < distance(points[it], loud) }
            val q = points.filterIndexed { i, _ -> isFar[i] }
            val l = points.filterIndexed { i, _ -> !isFar[i] }
            if (q.isEmpty() || l.isEmpty()) return emptySet()
            quiet = mean(q); loud = mean(l)
        }
        val farLevel = features.filterIndexed { i, _ -> isFar[i] }.map { it[0] }.average().toFloat()
        val nearLevel = features.filterIndexed { i, _ -> !isFar[i] }.map { it[0] }.average().toFloat()
        if (nearLevel - farLevel < ONE_VOICE_DB) return emptySet()
        // anything louder than halfway between the two is you, whatever its shape
        val ceiling = (nearLevel + farLevel) / 2
        return speech.filterIndexed { i, _ -> isFar[i] && features[i][0] < ceiling }.toSet()
    }

    private fun mean(points: List<FloatArray>) = FloatArray(3) { d -> points.map { it[d] }.average().toFloat() }

    private fun distance(a: FloatArray, b: FloatArray) = (0 until 3).sumOf { ((a[it] - b[it]) * (a[it] - b[it])).toDouble() }

    /** 5 frames either side: 100 ms */
    private const val VOTE = 5
    /** frames this far over the background are someone talking */
    private const val SPEECH_DB = 8f
    /** two groups closer than this in loudness are one voice, split by how its sounds vary */
    private const val ONE_VOICE_DB = 8f
    /** a second of speech before there's anything to learn from */
    private const val MIN_FRAMES = 50
}

/** a second-order filter (RBJ cookbook) */
class Biquad private constructor(private val b0: Float, private val b1: Float, private val b2: Float, private val a1: Float, private val a2: Float) {
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }

    companion object {
        private fun make(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad((b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(), (a1 / a0).toFloat(), (a2 / a0).toFloat())

        /** a bell around [hz], [db] up or down */
        fun peak(rate: Int, hz: Float, db: Float, q: Double = 1.0): Biquad {
            val a = 10.0.pow(db / 40.0); val w = 2 * PI * hz / rate; val alpha = sin(w) / (2 * q); val c = cos(w)
            return make(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
        }
    }
}

