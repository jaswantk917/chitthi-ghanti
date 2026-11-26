package io.github.jaswantk917.tring.recorder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IInterface
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import android.speech.SpeechRecognizer
import android.telecom.Call
import android.telecom.VideoProfile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * One screened call, from the silent answer to ringing you or keeping a message. Tring decides who
 * gets screened and offers them through [expect]; every failure here leaves the call ringing.
 * Any other call you let ring is answered just before the network would give up on it, to take a
 * message: voicemail.
 */
object Screening {
    enum class Stage { Greeting, Listening, Deciding, Ringing, Message, Connected, Ended }

    data class Live(val who: String, val stage: Stage, val words: String = "", val reason: String? = null, val saved: Boolean = false)

    private class Active(val service: ScreenService, val call: Call, val who: String, val number: String, val saved: Boolean, val voicemail: Boolean = false) {
        var hearing: Hearing? = null
        var job: Job? = null
        var wake: PowerManager.WakeLock? = null
        val heard = ByteArrayOutputStream()
        @Volatile var lastWordAt = 0L
        var pickedUp = false
    }

    /** what a screened caller said before you picked up, kept for the call's recording; [at]: when you did, wall clock */
    private class Lead(val pcm: ByteArray, val at: Long)

    private val _live = MutableStateFlow<Live?>(null)
    val live: StateFlow<Live?> = _live

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val summarizer by lazy { Summarizer() }
    // the shell watches this binder: it dies with the app, and the shell then hands the call back
    private val alive = Binder()
    private var active: Active? = null
    @Volatile private var expected: Pair<String, Long>? = null
    // the last call Tring kept quiet, which voicemail leaves alone
    @Volatile private var quiet: Pair<String, Long>? = null
    @Volatile private var lead: Lead? = null
    // calls ringing now, each waiting to be answered for a message if nobody picks up
    private val unanswered = HashMap<Call, Runnable>()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            val now = active?.takeIf { it.call == call } ?: return
            when (state) {
                AUDIO_PROCESSING -> started(now)
                SIMULATED_RINGING -> _live.update { it?.copy(stage = Stage.Ringing) }
                Call.STATE_ACTIVE -> connected(now)
                Call.STATE_DISCONNECTED -> ended(now)
            }
        }
    }

    private val notEntered = Runnable {
        val now = active ?: return@Runnable
        @Suppress("DEPRECATION")
        if (now.call.state == Call.STATE_RINGING) {
            Log.w(TAG, "the call never went silent; leaving it to ring")
            runCatching { Shell.service?.settled() }
            now.call.unregisterCallback(callback)
            active = null
        }
    }

    fun attach(context: Context) {
        Shell.onLost = { main.post { active?.let { if (it.call.isAudioProcessing()) ringBack(it, "Lost the connection, so it's ringing") } } }
        Shell.start(context)
    }

    private fun Call.isAudioProcessing(): Boolean {
        @Suppress("DEPRECATION")
        return state == AUDIO_PROCESSING
    }

    private fun prefs(context: Context) = context.getSharedPreferences("screening", Context.MODE_PRIVATE)

    fun isOn(context: Context) = prefs(context).getBoolean(ON, false)

    fun setOn(context: Context, on: Boolean) = prefs(context).edit().putBoolean(ON, on).apply()

    fun voicemailOn(context: Context) = prefs(context).getBoolean(VOICEMAIL, true)

    fun setVoicemail(context: Context, on: Boolean) = prefs(context).edit().putBoolean(VOICEMAIL, on).apply()

    /** the next call is screened whoever it's from, so it can be tried from a number the phone knows */
    fun tryNext(context: Context) = prefs(context).edit().putLong(TRY_UNTIL, System.currentTimeMillis() + TRY_MS).apply()

    fun cancelTry(context: Context) = prefs(context).edit().remove(TRY_UNTIL).apply()

    fun tryUntil(context: Context) = prefs(context).getLong(TRY_UNTIL, 0).takeIf { it > System.currentTimeMillis() }

    fun supported(context: Context) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun canRun(context: Context) = supported(context) && Shell.service != null && Shell.audioWorks && Greeting.ready(context) &&
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Tring asks before a call rings; [stranger]: not in contacts and never called before */
    fun expect(context: Context, number: String, stranger: Boolean): Boolean {
        val trying = tryUntil(context) != null
        if (!(trying || (stranger && isOn(context))) || !canRun(context) || active != null) return false
        if (trying) cancelTry(context)
        Processor.get(context).yieldToCall()
        expected = last10(number) to SystemClock.elapsedRealtime()
        return true
    }

    /** Tring, before a call rings: this one won't make a sound */
    fun keptQuiet(number: String) {
        quiet = last10(number) to SystemClock.elapsedRealtime()
    }

    /** for a recording started as a screened call connected: what the caller said first, else null */
    fun takeLead(recordingStartedAt: Long): ShortArray? {
        val mine = lead?.takeIf { abs(it.at - recordingStartedAt) <= LEAD_MATCH_MS } ?: return null
        lead = null
        return ShortArray(mine.pcm.size / 2).also { ByteBuffer.wrap(mine.pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it) }
    }

    fun added(service: ScreenService, call: Call) {
        val from = call.details.handle?.schemeSpecificPart.orEmpty()
        val (number, at) = expected ?: return waitForAnswer(service, call)
        if (last10(from) != number || SystemClock.elapsedRealtime() - at > EXPECT_MS) return waitForAnswer(service, call)
        expected = null
        @Suppress("DEPRECATION")
        if (call.state != Call.STATE_RINGING || active != null) return
        val contact = call.details.contactDisplayName?.takeIf { it.isNotBlank() }
        val screening = Active(service, call, Callers.live(service, from, contact), from, saved = contact != null)
        active = screening
        call.registerCallback(callback)
        if (!enter(call)) {
            call.unregisterCallback(callback)
            active = null
            return
        }
        main.postDelayed(notEntered, ENTER_MS)
    }

    fun removed(call: Call) {
        unanswered.remove(call)?.let(main::removeCallbacks)
        active?.takeIf { it.call == call }?.let(::ended)
    }

    @Suppress("DEPRECATION")
    private fun waitForAnswer(service: ScreenService, call: Call) {
        if (call.state != Call.STATE_RINGING || call.details.callDirection != Call.Details.DIRECTION_INCOMING) return
        if (!Messages.mayAnswer(last10(call.details.handle?.schemeSpecificPart.orEmpty()), quiet, SystemClock.elapsedRealtime())) return
        val check = Runnable {
            unanswered.remove(call)
            answerForMessage(service, call)
        }
        unanswered[call] = check
        main.postDelayed(check, UNANSWERED_MS)
    }

    /** still ringing, nobody picked up: answer quietly and take a message, or leave it to ring out */
    private fun answerForMessage(service: ScreenService, call: Call) {
        @Suppress("DEPRECATION")
        if (call.state != Call.STATE_RINGING || active != null || service.calls.size > 1) return
        if (!voicemailOn(service) || !canRun(service)) return
        Processor.get(service).yieldToCall()
        val from = call.details.handle?.schemeSpecificPart.orEmpty()
        val contact = call.details.contactDisplayName?.takeIf { it.isNotBlank() }
        val message = Active(service, call, Callers.live(service, from, contact), from, saved = contact != null, voicemail = true)
        active = message
        call.registerCallback(callback)
        if (!enter(call)) {
            call.unregisterCallback(callback)
            active = null
            return
        }
        main.postDelayed(notEntered, ENTER_MS)
    }

    private fun enter(call: Call): Boolean = try {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/telecom/", "Lcom/android/internal/telecom/")
        val adapter = Call::class.java.getDeclaredField("mInCallAdapter").apply { isAccessible = true }.get(call)!!
        val telecom = adapter.javaClass.getDeclaredField("mAdapter").apply { isAccessible = true }.get(adapter) as IInterface
        val id = Call::class.java.getDeclaredMethod("internalGetCallId").apply { isAccessible = true }.invoke(call) as String
        Shell.service?.enter(telecom.asBinder(), id, alive) == true
    } catch (e: Exception) {
        Log.w(TAG, "couldn't take the call", e)
        false
    }

    private fun started(a: Active) {
        main.removeCallbacks(notEntered)
        a.wake = a.service.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tring:screening").apply {
            setReferenceCounted(false)
            acquire(WAKE_MS)
        }
        _live.value = if (a.voicemail) Live(a.who, Stage.Message, reason = UNANSWERED) else Live(a.who, Stage.Greeting)
        a.service.startActivity(Intent(a.service, ScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val hearing = Hearing(a.service) { words ->
            a.lastWordAt = SystemClock.elapsedRealtime()
            _live.update { it?.copy(words = words) }
        }
        a.hearing = hearing
        val hello = Greeting.hello(a.service)
        val pipe = runCatching { Shell.service?.listen(ParcelFileDescriptor.open(hello, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
        if (pipe == null) return ringBack(a, "Couldn't hear the call, so it's ringing")
        thread(name = "screen-audio") {
            ParcelFileDescriptor.AutoCloseInputStream(pipe).use { input ->
                val buffer = ByteArray(1_280)
                while (true) {
                    val n = runCatching { input.read(buffer) }.getOrDefault(-1)
                    if (n <= 0) break
                    hearing.feed(buffer, n)
                    synchronized(a.heard) { if (a.heard.size() < MESSAGE_BYTES) a.heard.write(buffer, 0, n) }
                }
            }
            // the line closed while the call is still held: the caller can't be heard or spoken to
            main.post { if (active === a && !a.pickedUp && a.call.isAudioProcessing()) ringBack(a, "Couldn't reach the call, so it's ringing") }
        }
        val greetingMs = hello.length() / BYTES_PER_MS
        a.job = scope.launch { if (a.voicemail) leaveMessage(a, greetingMs) else listenThenDecide(a, greetingMs) }
    }

    private suspend fun listenThenDecide(a: Active, greetingMs: Long) {
        delay(greetingMs + LINE_SETTLE_MS)
        if (active !== a) return
        _live.update { it?.copy(stage = Stage.Listening) }
        a.hearing?.start()
        val began = SystemClock.elapsedRealtime()
        a.lastWordAt = began
        while (active === a) {
            delay(POLL_MS)
            val now = SystemClock.elapsedRealtime()
            val saidSomething = !_live.value?.words.isNullOrBlank()
            if (now - began >= LISTEN_MS || (saidSomething && now - a.lastWordAt >= PAUSE_MS) || (!saidSomething && now - began >= NO_WORDS_MS)) break
        }
        if (active !== a) return
        _live.update { it?.copy(stage = Stage.Deciding) }
        val verdict = Importance.decide(_live.value?.words.orEmpty(), situation(a.service), summarizer)
        if (active !== a || a.pickedUp) return
        if (verdict.ring) ringBack(a, verdict.reason) else takeMessage(a, verdict.reason)
    }

    /** voicemail: the same greeting, then straight to their message */
    private suspend fun leaveMessage(a: Active, greetingMs: Long) {
        delay(greetingMs + LINE_SETTLE_MS)
        if (active !== a) return
        a.hearing?.start()
        takeMessage(a, UNANSWERED, voicemail = true)
    }

    private suspend fun takeMessage(a: Active, reason: String, voicemail: Boolean = false) {
        _live.update { it?.copy(stage = Stage.Message, reason = reason) }
        val began = SystemClock.elapsedRealtime()
        a.lastWordAt = began
        while (active === a) {
            delay(POLL_MS)
            val now = SystemClock.elapsedRealtime()
            if (Messages.over(now - began, now - a.lastWordAt, !_live.value?.words.isNullOrBlank(), voicemail)) break
        }
        if (active !== a) return
        val thanks = Greeting.thanks(a.service)
        runCatching { Shell.service?.speak(ParcelFileDescriptor.open(thanks, ParcelFileDescriptor.MODE_READ_ONLY)) }
        delay(thanks.length() / BYTES_PER_MS + LINE_SETTLE_MS)
        if (active === a) a.call.disconnect()
    }

    private fun ringBack(a: Active, reason: String) {
        a.job?.cancel()
        settle()
        _live.update { it?.copy(stage = Stage.Ringing, reason = reason) }
        leaveAudioProcessing(a.call, ring = true)
    }

    fun pickUp() {
        val a = active ?: return
        a.pickedUp = true
        a.job?.cancel()
        settle()
        if (a.call.isAudioProcessing()) leaveAudioProcessing(a.call, ring = false) else a.call.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun hangUp() {
        val a = active ?: return
        a.job?.cancel()
        settle()
        a.call.disconnect()
    }

    private fun settle() {
        runCatching { Shell.service?.settled() }
    }

    private fun leaveAudioProcessing(call: Call, ring: Boolean) {
        try {
            Call::class.java.getMethod("exitBackgroundAudioProcessing", Boolean::class.javaPrimitiveType).invoke(call, ring)
        } catch (e: Exception) {
            Log.w(TAG, "couldn't hand the call back", e)
            call.disconnect()
        }
    }

    private fun connected(a: Active) {
        a.pickedUp = true
        a.job?.cancel()
        settle()
        val spoke = a.hearing?.spoke == true || !_live.value?.words.isNullOrBlank()
        stopListening(a)
        if (spoke) lead = Lead(synchronized(a.heard) { a.heard.toByteArray() }, System.currentTimeMillis())
        _live.update { it?.copy(stage = Stage.Connected) }
    }

    private fun stopListening(a: Active) {
        runCatching { Shell.service?.stopAudio() }
        a.hearing?.stop()
        a.hearing = null
        a.wake?.takeIf { it.isHeld }?.release()
    }

    private fun ended(a: Active) {
        if (active !== a) return
        active = null
        main.removeCallbacks(notEntered)
        a.job?.cancel()
        settle()
        val spoke = a.hearing?.spoke == true
        stopListening(a)
        a.call.unregisterCallback(callback)
        val words = _live.value?.words.orEmpty()
        val keep = Messages.keep(a.pickedUp, words, a.voicemail, spoke)
        if (a.pickedUp) {
            _live.value = null
            // the recording takes it as the call ends; a call nobody recorded doesn't keep it
            val mine = lead
            main.postDelayed({ if (lead === mine) lead = null }, LEAD_KEEP_MS)
            return
        }
        _live.update { it?.copy(stage = Stage.Ended, saved = keep) }
        if (keep) {
            val sound = synchronized(a.heard) { a.heard.toByteArray() }
            val context = a.service.applicationContext
            thread(name = "screen-message") { runCatching { Voicemail.save(context, if (a.saved) a.who else a.number.ifBlank { "Private number" }, a.number, a.saved, sound, words) }.onFailure { Log.w(TAG, "couldn't save the message", it) } }
        }
        main.postDelayed({ if (active == null) _live.value = null }, ENDED_SHOWN_MS)
    }

    /** what a person would weigh besides the words: the hour, and whether they asked for quiet */
    private fun situation(context: Context): String {
        val time = SimpleDateFormat("EEEE, h:mm a", Locale.ENGLISH).format(Date())
        val quiet = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val silent = context.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_NORMAL
        return "It is $time." + (if (quiet) " Do Not Disturb is on." else "") + (if (silent) " The phone is on silent." else "")
    }

    fun last10(number: String) = number.filter { it.isDigit() }.takeLast(10)

    private const val TAG = "Screening"
    private const val ON = "on"
    private const val VOICEMAIL = "voicemail"
    private const val UNANSWERED = "You didn't pick up"
    // the network gives up on an unanswered call at 30 seconds in India (TRAI); this leaves room to answer first
    private const val UNANSWERED_MS = 27_000L
    private const val TRY_UNTIL = "try_until"
    private const val TRY_MS = 5 * 60_000L
    private const val AUDIO_PROCESSING = 12
    private const val SIMULATED_RINGING = 13
    private const val EXPECT_MS = 8_000L
    // a recording started this near the moment you picked up is that call's
    private const val LEAD_MATCH_MS = 15_000L
    private const val LEAD_KEEP_MS = 30_000L
    private const val ENTER_MS = 2_500L
    private const val WAKE_MS = 2 * 60_000L
    private const val BYTES_PER_MS = Pcm.RATE * 2 / 1_000L
    private const val LINE_SETTLE_MS = 350L
    private const val POLL_MS = 120L
    private const val LISTEN_MS = 12_000L
    // a caller stopping to think mid-sentence is not done; this only shortens LISTEN_MS for one who is
    private const val PAUSE_MS = 3_000L
    private const val NO_WORDS_MS = 7_000L
    private const val MESSAGE_BYTES = 60 * Pcm.RATE * 2
    private const val ENDED_SHOWN_MS = 2_600L
}
