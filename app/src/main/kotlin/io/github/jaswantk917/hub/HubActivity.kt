package io.github.jaswantk917.hub

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.chitthi.R
import io.github.jaswantk917.chitthi.ui.theme.ChitthiTheme
import io.github.jaswantk917.chitthi.ui.util.Haptic
import io.github.jaswantk917.chitthi.ui.util.rememberHaptics
import kotlinx.coroutines.delay
import io.github.jaswantk917.chitthi.MainActivity as ChitthiActivity
import io.github.jaswantk917.ghanti.MainActivity as GhantiActivity

/**
 * What Play's "Open" button lands on (category INFO, so no third home-screen icon).
 * Shown once; after that it goes straight to the side picked here last.
 */
class HubActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("hub", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && prefs.getBoolean("seen", false)) {
            open(prefs.getString("last", SIDE_GHANTI)!!)
            return
        }
        prefs.edit().putBoolean("seen", true).apply()
        enableEdgeToEdge()
        val roles = getSystemService(RoleManager::class.java)
        setContent {
            ChitthiTheme {
                Hub(
                    smsReady = roles.isRoleHeld(RoleManager.ROLE_SMS),
                    phoneReady = roles.isRoleHeld(RoleManager.ROLE_DIALER),
                    onOpen = ::open,
                )
            }
        }
    }

    private fun open(side: String) {
        prefs.edit().putString("last", side).apply()
        val target = if (side == SIDE_CHITTHI) ChitthiActivity::class.java else GhantiActivity::class.java
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private companion object {
        const val SIDE_CHITTHI = "chitthi"
        const val SIDE_GHANTI = "ghanti"
    }
}

private val chitthiColors = listOf(Color(0xFF4A90F5), Color(0xFF1558C9))
private val ghantiColors = listOf(Color(0xFF3DDC84), Color(0xFF0F9D58))

@Composable
private fun Hub(smsReady: Boolean, phoneReady: Boolean, onOpen: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(scheme.surface).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Arrive(0) {
            Text("Tring", style = MaterialTheme.typography.displayMediumEmphasized)
        }
        Spacer(Modifier.height(8.dp))
        Arrive(1) {
            Text(
                "Two apps in one install. Each has its own icon on your home screen, so open them from there.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))
        Arrive(2) {
            SideCard(
                name = "Chitthi",
                what = "Messages that sort themselves. Codes and banks up top, people in their own place, promos out of the way.",
                ready = smsReady,
                readyLine = "Your SMS app",
                setupLine = "Opens to make it your SMS app",
                icon = R.drawable.ic_launcher_foreground,
                colors = chitthiColors,
                onClick = { onOpen("chitthi") },
            )
        }
        Spacer(Modifier.height(12.dp))
        Arrive(3) {
            SideCard(
                name = "Ghanti",
                what = "Calls that know who's calling. Strangers ring quietly, unless a delivery is on its way.",
                ready = phoneReady,
                readyLine = "Your phone app",
                setupLine = "Opens to make it your phone app",
                icon = R.drawable.ghanti_launcher_foreground,
                colors = ghantiColors,
                onClick = { onOpen("ghanti") },
            )
        }
        Spacer(Modifier.height(24.dp))
        Arrive(4) {
            Text(
                "Blocking works for both: Settings → Blocked in either one. Nothing you send, receive or call leaves this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** staged arrival: each block rises in a beat after the one above */
@Composable
private fun Arrive(order: Int, content: @Composable () -> Unit) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order * 70L)
        shown.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 220f))
    }
    Box(Modifier.graphicsLayer { alpha = shown.value.coerceIn(0f, 1f); translationY = (1f - shown.value) * 48.dp.toPx() }) {
        content()
    }
}

@Composable
private fun SideCard(
    name: String,
    what: String,
    ready: Boolean,
    readyLine: String,
    setupLine: String,
    @DrawableRes icon: Int,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    Row(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainer)
            .clickable(interactionSource = press, indication = null) { haptics.play(Haptic.Tap); onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(colors))) {
            Image(painterResource(icon), null, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLargeEmphasized)
            Spacer(Modifier.height(2.dp))
            Text(what, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ready) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = colors.last(), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (ready) readyLine else setupLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ready) scheme.onSurface else scheme.primary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = scheme.onSurfaceVariant)
    }
}
