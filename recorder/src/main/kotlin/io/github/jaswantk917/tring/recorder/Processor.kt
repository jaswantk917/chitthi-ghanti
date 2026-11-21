package io.github.jaswantk917.tring.recorder

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns recordings into transcripts and summaries, one at a time (Shruti and Nano each take one
 * job at once). While the app is open it catches up on recent calls by itself, because
 * AICore only answers the app in front; a call opened on screen goes before the catch-up.
 */
class Processor(
    private val notes: Notes,
    private val transcriber: Transcriber,
    private val summarizer: Summarizer,
    private val scope: CoroutineScope,
) {
    sealed interface Work {
        data class Listening(val progress: Transcriber.Progress, val lines: List<Line>) : Work
        data object Summarizing : Work
        data class Failed(val message: String) : Work
    }

    private val lock = Mutex()
    private val jobs = HashMap<Long, Job>()
    private var catching: Job? = null
    private var nano: Boolean? = null

    private val _work = MutableStateFlow<Map<Long, Work>>(emptyMap())
    /** what is happening to each recording right now */
    val work: StateFlow<Map<Long, Work>> = _work

    private val _saved = MutableStateFlow(0)
    /** goes up whenever a transcript or summary is saved, so screens read them again */
    val saved: StateFlow<Int> = _saved

    private val _latelyBusy = MutableStateFlow(false)
    val latelyBusy: StateFlow<Boolean> = _latelyBusy

    /** null until known; false on a phone without Gemini Nano, where there are no summaries */
    private val _canSummarize = MutableStateFlow<Boolean?>(null)
    val canSummarize: StateFlow<Boolean?> = _canSummarize

    fun transcribe(recording: Recording) {
        jobs[recording.id]?.cancel()
        jobs[recording.id] = scope.launch {
            lock.withLock {
                if (listen(recording)) summarize(recording.id, recording.who)
            }
        }
    }

    fun summarize(recording: Recording) {
        if (_work.value[recording.id] != null) return
        jobs[recording.id] = scope.launch { lock.withLock { summarize(recording.id, recording.who) } }
    }

    /** transcripts and summaries for the newest calls that have none, then "Lately" when it is behind */
    fun catchUp(recordings: List<Recording>) {
        if (catching?.isActive == true || !transcriber.supported) return
        catching = scope.launch {
            for (recording in recordings.take(CATCH_UP)) {
                val stop = lock.withLock {
                    when {
                        _work.value[recording.id] != null -> false
                        notes.transcript(recording.id) == null -> !listen(recording) || !summarize(recording.id, recording.who)
                        notes.summary(recording.id) == null && notes.transcript(recording.id)?.isNotEmpty() == true -> !summarize(recording.id, recording.who)
                        else -> false
                    }
                }
                // the model is missing or failing: trying the next call would only fail the same way
                if (stop) break
            }
            if (latelyBehind(recordings)) lately(recordings)
        }
    }

    fun lately(recordings: List<Recording>) {
        if (_latelyBusy.value) return
        scope.launch {
            _latelyBusy.value = true
            try {
                val calls = summarised(recordings)
                if (calls.isNotEmpty() && nanoReady()) {
                    val text = lock.withLock {
                        summarizer.lately(calls.map { "With ${it.who}, ${heading(it.at)}" to notes.summary(it.id).orEmpty() })
                    }
                    if (text != null) {
                        notes.saveLately(text, calls.first().at)
                        _saved.update { it + 1 }
                    }
                }
            } finally {
                _latelyBusy.value = false
            }
        }
    }

    fun summarised(recordings: List<Recording>) = recordings.filter { notes.summary(it.id) != null }.take(LATELY_CALLS)

    private fun latelyBehind(recordings: List<Recording>): Boolean {
        val newest = summarised(recordings).firstOrNull() ?: return false
        return (notes.lately()?.second ?: 0L) < newest.at
    }

    /** true when the transcript was saved */
    private suspend fun listen(recording: Recording): Boolean {
        set(recording.id, Work.Listening(Transcriber.Progress(0, 0), emptyList()))
        return try {
            val lines = transcriber.transcribe(
                recording.uri, notes.turns(recording.id), listOf(recording.who).filter { name -> name.any { it.isLetter() } },
                onProgress = { progress -> listening(recording.id) { it.copy(progress = progress) } },
                onLine = { line -> listening(recording.id) { it.copy(lines = it.lines + line) } },
            )
            notes.saveTranscript(recording.id, lines)
            _saved.update { it + 1 }
            set(recording.id, null)
            true
        } catch (e: CancellationException) {
            set(recording.id, null)
            throw e
        } catch (_: Transcriber.Unavailable) {
            set(recording.id, Work.Failed("Transcripts need a phone with a 64-bit ARM processor."))
            false
        } catch (e: Exception) {
            set(recording.id, Work.Failed("Couldn't transcribe this call (${e.message ?: e.javaClass.simpleName})."))
            false
        }
    }

    /** true when there is a summary afterwards, or nothing to summarise */
    private suspend fun summarize(id: Long, who: String): Boolean {
        val lines = notes.transcript(id)?.takeIf { it.isNotEmpty() } ?: return true
        if (!nanoReady()) return false
        set(id, Work.Summarizing)
        val text = summarizer.call(who, lines)
        if (text != null) {
            notes.saveSummary(id, text)
            _saved.update { it + 1 }
        }
        set(id, null)
        return text != null
    }

    private suspend fun nanoReady(): Boolean = nano ?: summarizer.ready().also { nano = it; _canSummarize.value = it }

    private fun set(id: Long, work: Work?) = _work.update { if (work == null) it - id else it + (id to work) }

    private fun listening(id: Long, change: (Work.Listening) -> Work.Listening) = _work.update { all ->
        val now = all[id] as? Work.Listening ?: return@update all
        all + (id to change(now))
    }

    companion object {
        /** how many of the newest calls are caught up on by themselves */
        const val CATCH_UP = 10
        const val LATELY_CALLS = 10

        fun heading(at: Long): String = SimpleDateFormat("EEE d MMM, h:mm a", Locale.ENGLISH).format(Date(at))
    }
}
