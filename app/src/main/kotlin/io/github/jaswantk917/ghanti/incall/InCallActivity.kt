package io.github.jaswantk917.ghanti.incall

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.ghanti.MainActivity
import io.github.jaswantk917.ghanti.data.CallStyle
import io.github.jaswantk917.ghanti.ui.incall.InCallScreen
import io.github.jaswantk917.ghanti.ui.theme.GhantiTheme
import io.github.jaswantk917.ghanti.ui.util.LocalBlur
import kotlinx.coroutines.delay

/** The call screen. It shows over the lock screen and stays out of recents. */
class InCallActivity : ComponentActivity() {
    /** the lock screen is up behind this one: answering then takes a swipe */
    private val locked = mutableStateOf(false)
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    // unlocking with a finger while it rings, or the screen going off, changes it under the call screen
    private val lockChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            locked.value = keyguard.isKeyguardLocked
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        // back leaves the call running in its notification instead of closing anything
        onBackPressedDispatcher.addCallback(this) { if (Calls.calls.value.isEmpty()) finishAndRemoveTask() else moveTaskToBack(true) }
        locked.value = keyguard.isKeyguardLocked
        ContextCompat.registerReceiver(
            this, lockChanges,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            val settings by GhantiApp.instance.prefs.settings.collectAsState()
            GhantiTheme(settings.palette, settings.amoled) {
                CompositionLocalProvider(LocalBlur provides settings.blur) { Route(settings.callStyle) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        locked.value = keyguard.isKeyguardLocked
    }

    override fun onDestroy() {
        unregisterReceiver(lockChanges)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) {
            Calls.calls.value.firstOrNull { it.phase == Phase.Ringing }?.let { Calls.answer(it.call) }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Route(style: CallStyle) {
        val calls by Calls.calls.collectAsState()
        val audio by Calls.audio.collectAsState()
        val ended by Calls.ended.collectAsState()
        var last by remember { mutableStateOf<CallUi?>(null) }
        // a note being written keeps the screen up after the call ends
        var noting by remember { mutableStateOf(false) }
        val primary = calls.firstOrNull { it.phase == Phase.Ringing || it.phase == Phase.ChooseSim }
            ?: calls.firstOrNull { it.phase == Phase.Active || it.phase == Phase.Dialing }
            ?: calls.firstOrNull()
        if (primary != null) last = primary
        val shown = primary ?: last
        LaunchedEffect(primary == null, noting) {
            if (primary == null && !noting) {
                // a declined ring just goes; a finished call holds "Call ended" for a beat
                if (ended != null) delay(1_300)
                finishAndRemoveTask()
            }
        }
        if (shown != null) {
            InCallScreen(
                call = shown,
                others = calls - shown,
                audio = audio,
                endedAt = if (primary == null) ended?.endedAt ?: System.currentTimeMillis() else null,
                noting = noting,
                onNoting = { noting = it },
                onAddCall = {
                    startActivity(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                style = style,
                locked = locked.value,
            )
        }
    }

    companion object {
        const val ACTION_ANSWER = "io.github.jaswantk917.ghanti.ANSWER"
    }
}
