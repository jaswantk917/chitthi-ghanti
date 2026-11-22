package io.github.jaswantk917.tring.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns recordings into transcripts and summaries, one at a time (the speech models and Nano each
 * take one job at once). With Shruti each call is transcribed as soon as it ends. Whisper takes
 * about twice a call's length and chokes the phone while it runs, so it waits until the phone is
 * charging with its screen off, and stops when it's unplugged or picked up (a tap on a call still
 * runs it there and then).
 * Summaries are tried as soon as there's a transcript; what that misses is caught up on while the
 * app is open, since AICore usually only answers the app in front. One per process ([get]), so the
 * screens and the recorder service share the queue.
 */
class Processor(
    private val notes: Notes,
    private val transcriber: Transcriber,
    private val summarizer: Summarizer,
    private val scope: CoroutineScope,
    /** whether Whisper may run by itself now: the phone on the charger with its screen off, or any time when [anytime] */
    val resting: StateFlow<Boolean>,
    private val prefs: SharedPreferences? = null,
    private val onAnytime: () -> Unit = {},
) {
    /** Whisper runs whenever a call needs it, even while the phone is in use: for trying it out, as it slows the phone; off stops it */
    var anytime: Boolean
        get() = prefs?.getBoolean(ANYTIME, false) ?: false
        set(value) {
            prefs?.edit()?.putBoolean(ANYTIME, value)?.apply()
            onAnytime()
            if (!value) cancelWhisper()
        }

    sealed interface Work {
        /** [whisper]: Whisper is the one listening, which slows the phone and can be stopped */
        data class Listening(val progress: Transcriber.Progress, val lines: List<Line>, val whisper: Boolean = false) : Work
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

    /** right after a call is saved: its transcript, then a try at its summary; [done] gets the summary, null when there is none yet */
    fun afterCall(recording: Recording, done: (String?) -> Unit) {
        if (!due(recording.id)) {
            done(null)
            return
        }
        jobs[recording.id] = scope.launch {
            lock.withLock {
                if (listen(recording)) summarize(recording.id, recording.who)
            }
            done(notes.summary(recording.id))
        }
    }

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
            // once Nano says no, the rest still get transcripts (a phone without Nano used to stop after one)
            var summarizing = true
            for (recording in recordings.take(CATCH_UP)) {
                val stop = lock.withLock {
                    if (_work.value[recording.id] != null) return@withLock false
                    // the model is missing or failing: the next call would fail the same way
                    if (due(recording.id) && !listen(recording)) return@withLock true
                    if (summarizing && notes.summary(recording.id) == null) summarizing = summarize(recording.id, recording.who)
                    false
                }
                if (stop) break
            }
            if (latelyBehind(recordings)) updateLately(recordings)
        }
    }

    /**
     * Stopped by hand (the call screen or the note while it runs): Whisper lets go of the phone
     * now, and "any time" goes off, so it waits for the charger again instead of starting over.
     */
    fun stopWhisper() {
        anytime = false
    }

    private fun cancelWhisper() {
        val running = _work.value.filterValues { it is Work.Listening && it.whisper }.keys
        running.forEach { jobs[it]?.cancel() }
        if (running.isNotEmpty()) catching?.cancel()
    }

    /** Whisper stops when the phone is unplugged or picked up, and starts the call over next time */
    fun woken() {
        if (transcriber.engine() == Transcriber.Engine.Whisper) catching?.cancel()
    }

    /**
     * Whether a call's transcript should be written now without being asked: when it has none, or
     * only Shruti's once Whisper is in; and Whisper only while the phone rests on the charger.
     */
    fun due(id: Long): Boolean {
        val made = notes.madeBy(id)
        return when (transcriber.engine()) {
            Transcriber.Engine.Shruti -> made == null
            Transcriber.Engine.Whisper -> resting.value && made != Transcriber.Engine.Whisper
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
                    !summarize(recording.id, recording.who)
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
        set(recording.id, Work.Listening(Transcriber.Progress(0, 0), emptyList(), whisper = transcriber.engine() == Transcriber.Engine.Whisper))
        return try {
            val replaced = notes.madeBy(recording.id) != null
            val transcript = transcriber.transcribe(
                recording.uri, notes.turns(recording.id), listOf(recording.who).filter { name -> name.any { it.isLetter() } },
                onProgress = { progress -> listening(recording.id) { it.copy(progress = progress) } },
                onLine = { line -> listening(recording.id) { it.copy(lines = it.lines + line) } },
            )
            notes.saveTranscript(recording.id, transcript.lines, transcript.engine)
            if (replaced) notes.forgetSummary(recording.id)
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
        val lines = notes.transcript(id) ?: return true
        if (!Summarizer.worthSummarising(lines)) {
            notes.saveSummary(id, "")
            _saved.update { it + 1 }
            return true
        }
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
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                val prefs = app.getSharedPreferences("whisper", Context.MODE_PRIVATE)
                var plugged = pluggedIn(app)
                var screenOn = app.getSystemService(PowerManager::class.java).isInteractive
                fun free() = prefs.getBoolean(ANYTIME, false) || (plugged && !screenOn)
                val resting = MutableStateFlow(free())
                lateinit var processor: Processor
                fun changed() {
                    val now = free()
                    if (now == resting.value) return
                    resting.value = now
                    if (!now) processor.woken()
                    else scope.launch { processor.catchUp(withContext(Dispatchers.IO) { Recordings.list(app) }) }
                }
                processor = Processor(Notes(app), Transcriber(app), Summarizer(), scope, resting, prefs, ::changed)
                processor.also {
                    // the recorder service keeps the process, and with it this, alive between calls
                    ContextCompat.registerReceiver(
                        app,
                        object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                when (intent.action) {
                                    Intent.ACTION_POWER_CONNECTED -> plugged = true
                                    Intent.ACTION_POWER_DISCONNECTED -> plugged = false
                                    Intent.ACTION_SCREEN_ON -> screenOn = true
                                    Intent.ACTION_SCREEN_OFF -> screenOn = false
                                }
                                changed()
                            }
                        },
                        IntentFilter().apply {
                            addAction(Intent.ACTION_POWER_CONNECTED)
                            addAction(Intent.ACTION_POWER_DISCONNECTED)
                            addAction(Intent.ACTION_SCREEN_ON)
                            addAction(Intent.ACTION_SCREEN_OFF)
                        },
                        ContextCompat.RECEIVER_NOT_EXPORTED,
                    )
                }
            }.also { shared = it }
        }

        private fun pluggedIn(context: Context) =
            (context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        // "anytime" was 1.11's first key: dropped, so the update starts with it off
        private const val ANYTIME = "run_any_time"

        /** how many of the newest calls are caught up on by themselves */
        const val CATCH_UP = 10
        const val LATELY_CALLS = 10

        fun heading(at: Long): String = SimpleDateFormat("EEE d MMM, h:mm a", Locale.ENGLISH).format(Date(at))
    }
}
