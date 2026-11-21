package io.github.jaswantk917.tring.recorder

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A phone call the user recorded. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class RecordedCall(
    /** Who the call was with: the contact's name, or the number when they aren't a contact. */
    val with: String,
    /** When the call started, in the phone's local time, like "Fri 21 Nov, 3:16 PM". */
    val startedAt: String,
    /** How long the call lasted, in seconds. */
    val durationSeconds: Int,
    /** A few lines on what was said, made on the phone from the call's transcript; null when the call hasn't been summarised yet. */
    val summary: String?,
)

/** The user's recent recorded calls, newest first. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class RecentCalls(
    /** The calls, newest first. */
    val calls: List<RecordedCall>,
)

/** What the user has been talking about lately on the phone. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class RecentTalk(
    /** An overview of the recent calls' topics, open tasks and promises; null when none has been made yet. */
    val overview: String?,
    /** The recent calls behind it, newest first, each with its summary. */
    val calls: List<RecordedCall>,
)

/**
 * What assistants can ask the recorder: who you talked to recently and what about. These only
 * read what is already on the phone; transcripts and summaries are made while the app is open,
 * because Gemini Nano only answers the app in front.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(serviceName = "RecorderFunctionService", appFunctionXmlFileName = "recorder_app_functions")
abstract class RecorderFunctions : AppFunctionService() {
    /**
     * Lists the phone calls the user recorded, newest first: who each call was with, when it started,
     * how long it lasted, and a short summary of what was said when one has been made. Use it for
     * questions like "who did I talk to today" or "when did I last call Asha".
     *
     * @param count How many of the newest calls to list, from 1 to 50; 10 when not given.
     * @return The recorded calls, newest first.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun listRecentCalls(count: Int = 10): RecentCalls = withContext(Dispatchers.IO) {
        val notes = Notes(this@RecorderFunctions)
        RecentCalls(Recordings.list(this@RecorderFunctions).take(if (count in 1..50) count else 10).map { it.described(notes) })
    }

    /**
     * Tells what the user has been talking about lately on their recorded phone calls: an overview
     * of the topics, tasks and promises across recent calls, and each recent call's own summary
     * with who it was with and when. Use it for questions like "what have I been talking about
     * lately" or "what did I promise people on calls this week".
     *
     * @return The overview and the summarised calls behind it, newest first.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getRecentTalk(): RecentTalk = withContext(Dispatchers.IO) {
        val notes = Notes(this@RecorderFunctions)
        val calls = Recordings.list(this@RecorderFunctions).filter { notes.summary(it.id) != null }.take(Processor.LATELY_CALLS)
        RecentTalk(notes.lately()?.first, calls.map { it.described(notes) })
    }

    private fun Recording.described(notes: Notes) = RecordedCall(
        with = who,
        startedAt = Processor.heading(at),
        durationSeconds = (durationMs / 1_000).toInt(),
        summary = notes.summary(id),
    )
}
