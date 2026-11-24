package io.github.jaswantk917.tring.recorder

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
 * job at once). Each call is transcribed as soon as it ends and its summary tried then too; what
 * that misses is caught up on while the app is open, since AICore usually only answers the app in
 * front. One per process ([get]), so the screens and the recorder service share the queue.
 */
class Processor(
    private val notes: Notes,
    private val transcriber: Transcriber,
    private val summarizer: Summarizer,
    private val scope: CoroutineScope,
    /** for the clearer transcript's own process; none in screen tests */
    private val app: Context? = null,
) {
    sealed interface Work {
        /** [clearer]: IndicConformer listening in its own process, which can be stopped */
        data class Listening(val progress: Transcriber.Progress, val lines: List<Line>, val clearer: Boolean = false) : Work
        data object Summarizing : Work
        data class Failed(val message: String) : Work
    }

    private val lock = Mutex()
    private val jobs = HashMap<Long, Job>()
    private var catching: Job? = null
    private var clearing: IndicService.Run? = null
    private var clearingByItself = false
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

    /** right after a call is saved: its transcript, then a try at its summary; [done] gets the summary, null when there is none yet */
    fun afterCall(recording: Recording, done: (String?) -> Unit) {
        val context = app
        if (context != null && clearing == null && _work.value[recording.id] == null && BigModel.canTake(context, recording.durationMs)) {
            // if it can't finish, the built-in model still writes this call; if a new call stopped it, catch-up does
            clearer(recording) { wrote -> if (wrote == false) builtIn(recording, done) else done(notes.summary(recording.id)) }
        } else {
            builtIn(recording, done)
        }
    }

    private fun builtIn(recording: Recording, done: (String?) -> Unit) {
        jobs[recording.id] = scope.launch {
            lock.withLock {
                if (listen(recording)) summarize(recording.id, recording.who, recording.durationMs)
            }
            done(notes.summary(recording.id))
        }
    }

    /** a call is starting: the clearer model gives its memory back, unless you asked for this run yourself */
    fun yieldToCall() {
        Handler(Looper.getMainLooper()).post { if (clearingByItself) clearing?.stop() }
    }

    fun transcribe(recording: Recording) {
        jobs[recording.id]?.cancel()
        jobs[recording.id] = scope.launch {
            lock.withLock {
                if (listen(recording)) summarize(recording.id, recording.who, recording.durationMs)
            }
        }
    }

    /**
     * The clearer transcript of [recording]: IndicConformer in its own process beside the queue,
     * replacing the built-in model's transcript and then its summary. [byItself] is a run nobody
     * asked for, told true when written, false when it failed, null when stopped.
     */
    fun clearer(recording: Recording, byItself: ((Boolean?) -> Unit)? = null) {
        val context = app ?: return
        if (_work.value[recording.id] != null || clearing != null) return
        set(recording.id, Work.Listening(Transcriber.Progress(0, 0), emptyList(), clearer = true))
        clearingByItself = byItself != null
        val run = IndicService.Run(
            context, recording,
            onProgress = { progress -> listening(recording.id) { it.copy(progress = progress) } },
            onLine = { line -> listening(recording.id) { it.copy(lines = it.lines + line) } },
            onFinished = {
                clearing = null
                set(recording.id, null)
                notes.forgetSummary(recording.id)
                _saved.update { it + 1 }
                summarize(recording)
                if (byItself != null) scope.launch {
                    jobs[recording.id]?.join()
                    byItself(true)
                }
            },
            onFailed = { message ->
                clearing = null
                if (byItself == null) {
                    set(recording.id, Work.Failed("Couldn't write the clearer transcript ($message)."))
                } else {
                    set(recording.id, null)
                    byItself(false)
                }
            },
            onStopped = {
                clearing = null
                set(recording.id, null)
                byItself?.invoke(null)
            },
        )
        clearing = run
        try {
            run.start()
        } catch (e: IllegalStateException) {
            // Android can refuse a foreground service from the background
            clearing = null
            if (byItself == null) {
                set(recording.id, Work.Failed("Couldn't write the clearer transcript (${e.message})."))
            } else {
                set(recording.id, null)
                byItself(false)
            }
        }
    }

    fun stopClearer() {
        clearing?.stop()
    }

    fun summarize(recording: Recording) {
        if (_work.value[recording.id] != null) return
        jobs[recording.id] = scope.launch { lock.withLock { summarize(recording.id, recording.who, recording.durationMs) } }
    }

    /** transcripts and summaries for the newest calls that have none, then "Lately" when it is behind */
    fun catchUp(recordings: List<Recording>) {
        if (catching?.isActive == true || !transcriber.supported) return
        catching = scope.launch {
            // once Nano says no, the rest still get transcripts (a phone without Nano used to stop after one)
            var summarizing = true
            for (recording in recordings.take(CATCH_UP)) {
                val stop = lock.withLock {
                    if (_work.value[recording.id] != null) return@withLock false
                    // Shruti is missing or failing: the next call would fail the same way
                    if (notes.transcript(recording.id) == null && !listen(recording)) return@withLock true
                    if (summarizing && notes.summary(recording.id) == null) summarizing = summarize(recording.id, recording.who, recording.durationMs)
                    false
                }
                if (stop) break
            }
            if (latelyBehind(recordings)) updateLately(recordings)
        }
    }

    fun lately(recordings: List<Recording>) {
        scope.launch { updateLately(recordings) }
    }

    /** for an assistant asking: summaries for the newest calls already transcribed, then "Lately" when it is behind */
    suspend fun freshen(recordings: List<Recording>) {
        for (recording in recordings.take(LATELY_CALLS)) {
            val stop = lock.withLock {
                _work.value[recording.id] == null && notes.transcript(recording.id) != null && notes.summary(recording.id) == null &&
                    !summarize(recording.id, recording.who, recording.durationMs)
            }
            if (stop) return
        }
        if (latelyBehind(recordings)) updateLately(recordings)
    }

    private suspend fun updateLately(recordings: List<Recording>) {
        if (_latelyBusy.value) return
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

    /** the newest calls that have something to say, for "Lately" */
    fun summarised(recordings: List<Recording>) = recordings.filter { !notes.summary(it.id).isNullOrEmpty() }.take(LATELY_CALLS)

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
    private suspend fun summarize(id: Long, who: String, callMs: Long): Boolean {
        val lines = notes.transcript(id) ?: return true
        if (!Summarizer.worthSummarising(lines)) {
            notes.saveSummary(id, "")
            _saved.update { it + 1 }
            return true
        }
        if (!nanoReady()) return false
        set(id, Work.Summarizing)
        val text = summarizer.call(who, lines, callMs)
        if (text != null) {
            notes.saveSummary(id, text)
            _saved.update { it + 1 }
        }
        set(id, null)
        return text != null
    }

    // only a yes is kept: a no can be AICore turning down an app in the background, which the app on screen gets past
    private suspend fun nanoReady(): Boolean = nano ?: summarizer.ready().also { if (it) nano = true; _canSummarize.value = it }

    private fun set(id: Long, work: Work?) = _work.update { if (work == null) it - id else it + (id to work) }

    private fun listening(id: Long, change: (Work.Listening) -> Work.Listening) = _work.update { all ->
        val now = all[id] as? Work.Listening ?: return@update all
        all + (id to change(now))
    }

    companion object {
        @Volatile
        private var shared: Processor? = null

        fun get(context: Context): Processor = shared ?: synchronized(this) {
            shared ?: context.applicationContext.let { app ->
                Processor(Notes(app), Transcriber(app), Summarizer(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), app)
            }.also { shared = it }
        }

        /** how many of the newest calls are caught up on by themselves */
        const val CATCH_UP = 10
        const val LATELY_CALLS = 10

        fun heading(at: Long): String = SimpleDateFormat("EEE d MMM, h:mm a", Locale.ENGLISH).format(Date(at))
    }
}
