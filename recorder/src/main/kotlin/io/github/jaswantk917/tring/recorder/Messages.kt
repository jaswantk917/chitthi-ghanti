package io.github.jaswantk917.tring.recorder

/** When a caller's message is over, and whether it's worth keeping; screened callers and voicemail alike. */
object Messages {
    private const val MESSAGE_MS = 20_000L
    private const val VOICEMAIL_MS = 60_000L
    private const val PAUSE_MS = 3_500L
    // a screened caller is already mid-sentence; someone reaching voicemail takes a moment to start
    private const val FIRST_WORDS_MS = 7_000L
    // Tring says it kept a call quiet a moment before the call reaches the recorder
    private const val SILENCED_MS = 8_000L

    /** it ends at its longest, or once they've gone quiet: briefly after speaking, longer before they start */
    fun over(elapsedMs: Long, quietMs: Long, spoken: Boolean, voicemail: Boolean): Boolean {
        val longest = if (voicemail) VOICEMAIL_MS else MESSAGE_MS
        val quiet = if (spoken || !voicemail) PAUSE_MS else FIRST_WORDS_MS
        return elapsedMs >= longest || quietMs >= quiet
    }

    /** a voicemail is kept when they spoke at all: the recognizer can miss every word of a real message */
    fun keep(pickedUp: Boolean, words: String, voicemail: Boolean, spoke: Boolean) = !pickedUp && (words.isNotBlank() || voicemail && spoke)

    /** voicemail is for calls that rang you; [silenced]: the last call Tring kept quiet, by its last 10 digits, and when */
    fun mayAnswer(number: String, silenced: Pair<String, Long>?, now: Long) =
        silenced == null || silenced.first != number || now - silenced.second > SILENCED_MS
}
