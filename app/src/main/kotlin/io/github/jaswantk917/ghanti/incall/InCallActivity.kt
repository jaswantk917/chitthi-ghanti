package io.github.jaswantk917.ghanti.incall

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.addCallback
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.ghanti.MainActivity
import io.github.jaswantk917.ghanti.ui.incall.InCallScreen
import io.github.jaswantk917.ghanti.ui.theme.GhantiTheme
import io.github.jaswantk917.ghanti.ui.util.LocalBlur
import kotlinx.coroutines.delay

/** The call screen. It shows over the lock screen and stays out of recents. */
class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        // back leaves the call running in its notification instead of closing anything
        onBackPressedDispatcher.addCallback(this) { if (Calls.calls.value.isEmpty()) finishAndRemoveTask() else moveTaskToBack(true) }
        setContent {
            val settings by GhantiApp.instance.prefs.settings.collectAsState()
            GhantiTheme(settings.palette, settings.amoled) {
                CompositionLocalProvider(LocalBlur provides settings.blur) { Route() }
            }
        }
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
    private fun Route() {
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
            )
        }
    }

    companion object {
        const val ACTION_ANSWER = "io.github.jaswantk917.ghanti.ANSWER"
    }
}
