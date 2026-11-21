package io.github.jaswantk917.ghanti.incall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The bridge to Tring Recorder, a separate app you install from a file: Android lets only an
 * Accessibility service use the microphone during a call, and Play doesn't allow one in Tring.
 * The strings match CallRecorder in the recorder module.
 */
internal object Recorder {
    const val PACKAGE = "io.github.jaswantk917.tring.recorder"
    private const val ACTION_RECORD = "$PACKAGE.RECORD"
    private const val ACTION_STATE = "io.github.jaswantk917.tring.RECORDING"
    // its receiver lives only while its Accessibility service is on, so silence means it isn't
    private const val REPLY_MS = 1_500L

    enum class State { Off, Starting, Recording, NeedsSetup }

    private val _state = MutableStateFlow(State.Off)
    val state: StateFlow<State> = _state
    private val handler = Handler(Looper.getMainLooper())
    private val noReply = Runnable { if (_state.value == State.Starting) _state.value = State.NeedsSetup }

    private val replies = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(noReply)
            _state.value = when (intent.getStringExtra("state")) {
                "recording" -> State.Recording
                "needs_setup" -> State.NeedsSetup
                else -> State.Off
            }
        }
    }

    fun installed(context: Context) = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    fun listen(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(replies, IntentFilter(ACTION_STATE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(replies, IntentFilter(ACTION_STATE))
        }
    }

    fun unlisten(context: Context) {
        runCatching { context.unregisterReceiver(replies) }
    }

    fun record(context: Context, who: String) {
        _state.value = State.Starting
        context.sendBroadcast(Intent(ACTION_RECORD).setPackage(PACKAGE).putExtra("on", true).putExtra("who", who))
        handler.postDelayed(noReply, REPLY_MS)
    }

    fun stop(context: Context) {
        handler.removeCallbacks(noReply)
        if (_state.value == State.Off) return
        _state.value = State.Off
        context.sendBroadcast(Intent(ACTION_RECORD).setPackage(PACKAGE).putExtra("on", false))
    }

    /** the recorder's own screen: the switches to flip once */
    fun openSetup(context: Context) {
        _state.value = State.Off
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
