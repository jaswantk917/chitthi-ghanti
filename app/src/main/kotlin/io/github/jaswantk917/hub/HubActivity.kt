package io.github.jaswantk917.hub

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * What Play's "Open" button lands on (category INFO, so no third home-screen icon), every time:
 * the two sides, and whether each one's icon shows.
 */
class HubActivity : ComponentActivity() {
    private var sides by mutableStateOf(Sides())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChitthiTheme {
                Hub(
                    sides = sides,
                    onOpen = ::open,
                    onShow = ::show,
                    onDefaultApps = { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
                    onUninstall = { startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))) },
                )
            }
        }
    }

    // roles change in Default apps and come back here
    override fun onResume() {
        super.onResume()
        val roles = getSystemService(RoleManager::class.java)
        sides = Sides(
            sms = roles.isRoleHeld(RoleManager.ROLE_SMS),
            phone = roles.isRoleHeld(RoleManager.ROLE_DIALER),
            screening = roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
            chitthiShown = shown(ChitthiActivity::class.java),
            ghantiShown = shown(GhantiActivity::class.java),
        )
    }

    private fun shown(activity: Class<*>) =
        packageManager.getComponentEnabledSetting(ComponentName(this, activity)) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun open(side: Side) {
        val target = if (side == Side.Chitthi) ChitthiActivity::class.java else GhantiActivity::class.java
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    /** hiding disables the side's launcher activity, so its icon goes from the home screen and the app list; its services stay */
    private fun show(side: Side, on: Boolean) {
        val target = if (side == Side.Chitthi) ChitthiActivity::class.java else GhantiActivity::class.java
        packageManager.setComponentEnabledSetting(
            ComponentName(this, target),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        sides = if (side == Side.Chitthi) sides.copy(chitthiShown = on) else sides.copy(ghantiShown = on)
    }
}

private enum class Side { Chitthi, Ghanti }

private data class Sides(
    val sms: Boolean = false,
    val phone: Boolean = false,
    val screening: Boolean = false,
    val chitthiShown: Boolean = true,
    val ghantiShown: Boolean = true,
) {
    /** why a side can't hide: with no icon, its role would keep working with no way in */
    fun keeps(side: Side): String? = when {
        side == Side.Chitthi && sms -> "It's your SMS app. Choose another one first."
        side == Side.Ghanti && phone -> "It's your phone app. Choose another one first."
        side == Side.Ghanti && screening -> "It's screening your calls. Choose another caller ID app first."
        else -> null
    }

    fun shown(side: Side) = if (side == Side.Chitthi) chitthiShown else ghantiShown
}

private val chitthiColors = listOf(Color(0xFF4A90F5), Color(0xFF1558C9))
private val ghantiColors = listOf(Color(0xFF3DDC84), Color(0xFF0F9D58))

@Composable
private fun Hub(sides: Sides, onOpen: (Side) -> Unit, onShow: (Side, Boolean) -> Unit, onDefaultApps: () -> Unit, onUninstall: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var askUninstall by remember { mutableStateOf(false) }
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
                ready = sides.sms,
                shown = sides.chitthiShown,
                readyLine = "Your SMS app",
                setupLine = "Opens to make it your SMS app",
                icon = R.drawable.ic_launcher_foreground,
                colors = chitthiColors,
                onClick = { onOpen(Side.Chitthi) },
            )
        }
        Spacer(Modifier.height(12.dp))
        Arrive(3) {
            SideCard(
                name = "Ghanti",
                what = "Calls that know who's calling. Strangers ring quietly, unless a delivery is on its way.",
                ready = sides.phone,
                shown = sides.ghantiShown,
                readyLine = "Your phone app",
                setupLine = "Opens to make it your phone app",
                icon = R.drawable.ghanti_launcher_foreground,
                colors = ghantiColors,
                onClick = { onOpen(Side.Ghanti) },
            )
        }
        Spacer(Modifier.height(28.dp))
        Arrive(4) {
            Column {
                Text("App icons", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                Column(Modifier.clip(RoundedCornerShape(24.dp)).background(scheme.surfaceContainer)) {
                    Side.entries.forEach { side ->
                        ShowRow(
                            side = side,
                            sides = sides,
                            onShow = { on ->
                                val other = if (side == Side.Chitthi) Side.Ghanti else Side.Chitthi
                                if (!on && !sides.shown(other)) askUninstall = true else onShow(side, on)
                            },
                            onDefaultApps = onDefaultApps,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Arrive(5) {
            Text(
                "Blocking works for both: Settings → Blocked in either one. Nothing you send, receive or call leaves this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
    if (askUninstall) {
        AlertDialog(
            onDismissRequest = { askUninstall = false },
            title = { Text("Hide both?") },
            text = { Text("With both hidden there's nothing left to open. Uninstall Tring instead?") },
            confirmButton = { TextButton(onClick = { askUninstall = false; onUninstall() }) { Text("Uninstall") } },
            dismissButton = { TextButton(onClick = { askUninstall = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun ShowRow(side: Side, sides: Sides, onShow: (Boolean) -> Unit, onDefaultApps: () -> Unit) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val shown = sides.shown(side)
    val keeps = sides.keeps(side)
    // the reason shows only once they try, and goes when the role does
    var refused by remember(keeps) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(side.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = shown,
                onCheckedChange = { on ->
                    if (!on && keeps != null) {
                        haptics.play(Haptic.Tap)
                        refused = true
                    } else {
                        haptics.play(if (on) Haptic.ToggleOn else Haptic.ToggleOff)
                        onShow(on)
                    }
                },
            )
        }
        if (refused && keeps != null) {
            Text(keeps, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            TextButton(onClick = { haptics.play(Haptic.Tap); onDefaultApps() }, contentPadding = PaddingValues(0.dp)) { Text("Default apps") }
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
    shown: Boolean,
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
    val dim by animateFloatAsState(if (shown) 1f else 0.45f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "dim")
    Row(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale; alpha = dim }.clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainer)
            // a hidden side's activity is off, so there's nothing to open
            .clickable(enabled = shown, interactionSource = press, indication = null) { haptics.play(Haptic.Tap); onClick() }
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
                if (ready && shown) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = colors.last(), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    when {
                        !shown -> "Icon hidden"
                        ready -> readyLine
                        else -> setupLine
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ready || !shown) scheme.onSurface else scheme.primary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = scheme.onSurfaceVariant)
    }
}
