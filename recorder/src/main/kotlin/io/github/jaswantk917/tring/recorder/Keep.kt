package io.github.jaswantk917.tring.recorder

/**
 * Whether a finished call is worth saving. Silence is judged by speech heard, not loudness: real
 * callers measure as low as -86 dB, under the hiss of other calls.
 */
object Keep {
    const val SHORTEST_MS = 1_500L
    const val LEAST_SPEECH_MS = 300L

    fun worth(callMs: Long, speechMs: Long) = callMs >= SHORTEST_MS && speechMs >= LEAST_SPEECH_MS
}
