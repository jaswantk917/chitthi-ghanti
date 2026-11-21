package io.github.jaswantk917.tring.recorder

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh

/**
 * Brings whoever is quiet up to the loud one's level, one microphone at a time. The phone's
 * microphones hear the caller only as a leak from the earpiece, or hear you from arm's length on
 * speaker, so one side always comes out faint. Gain follows a slow envelope of the signal, capped,
 * and is held back on near silence so the room's hiss isn't lifted with it.
 */
class Leveler(sampleRate: Int, private val gate: Float = GATE, private val maxGain: Float = MAX_GAIN) {
    private val attack = 1f - exp(-1f / (0.01f * sampleRate))
    private val release = 1f - exp(-1f / (0.4f * sampleRate))
    private val glide = 1f - exp(-1f / (0.08f * sampleRate))
    private val duck = 1f - exp(-1f / (0.001f * sampleRate))
    private var envelope = 0f
    private var gain = 1f

    /**
     * [x] and the result are in -1..1. With [listen] false the gain is applied but not changed:
     * while the other voice talks, this one's level shouldn't follow it.
     */
    fun process(x: Float, listen: Boolean = true): Float {
        val level = abs(x)
        if (listen) {
            envelope += (level - envelope) * (if (level > envelope) attack else release)
            val wanted = when {
                envelope < gate -> 1f
                else -> (TARGET / envelope).coerceIn(MIN_GAIN, maxGain)
            }
            // down fast, up slowly: a loud sound under a high gain is pulled back within a millisecond
            gain += (wanted - gain) * (if (wanted < gain) duck else glide)
        }
        // whatever the guess about who's talking, nothing is driven past full scale
        if (level * gain > CEILING) gain = (CEILING / level).coerceAtLeast(MIN_GAIN)
        return tanh(x * gain)
    }

    companion object {
        /** the envelope speech settles at: about -14 dBFS */
        const val TARGET = 0.2f
        /** 24 dB: enough to bring a leak from the earpiece up to talking level */
        const val MAX_GAIN = 16f
        const val MIN_GAIN = 0.5f
        /** the most any sample is driven to before the soft clip */
        const val CEILING = 0.9f
        /** about -52 dBFS: below this is room noise, left as it is; the recorder measures each phone's own */
        const val GATE = 0.0025f
    }
}
