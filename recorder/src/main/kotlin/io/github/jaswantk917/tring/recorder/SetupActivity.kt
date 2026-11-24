package io.github.jaswantk917.tring.recorder

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Everything to turn on once: recording first, then call screening. */
class SetupActivity : ComponentActivity() {
    private var resumed by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RecorderTheme { SetupPage(resumed, onBack = ::finish, ask = { requestPermissions(arrayOf(it), 1) }) } }
    }

    override fun onResume() {
        super.onResume()
        Shell.refresh()
        resumed++
    }

    @Deprecated("Deprecated in ComponentActivity")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        resumed++
    }
}

private const val REPO = "https://github.com/jaswantk917/tring-recorder"
private const val SHIZUKU = "moe.shizuku.privileged.api"
private const val SHIZUKU_PAGE = "https://shizuku.rikka.app/download/"

@Composable
internal fun SetupPage(resumed: Int, onBack: () -> Unit, ask: (String) -> Unit) {
    val context = LocalContext.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    fun open(intent: Intent) = runCatching { context.startActivity(intent) }
    fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    val mic = remember(resumed) { granted(Manifest.permission.RECORD_AUDIO) }
    val notes = remember(resumed) { Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS) }
    val service = remember(resumed) { recorderOn(context) }
    var modelIn by remember(resumed) { mutableStateOf(IndicModel.ready(context)) }
    var confirmRemove by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = bars.calculateTopPadding() + 4.dp, bottom = bars.calculateBottomPadding() + 32.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.padding(bottom = 4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.displaySmall)
            Text(
                "Everything stays on this phone.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 20.dp),
            )

            Section("Recording", if (mic && service && notes) "On" else null) {
                Step("Microphone", "So your side of the call is heard.", mic, "Allow") { ask(Manifest.permission.RECORD_AUDIO) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Step("Notifications", "A note when a call is saved.", notes, "Allow") { ask(Manifest.permission.POST_NOTIFICATIONS) }
                }
                Step("Accessibility", "The only way Android allows recording during a call. It never reads your screen.", service, "Open") {
                    open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                AnimatedVisibility(!service) {
                    Column(Modifier.padding(top = 4.dp)) {
                        Text(
                            "Greyed out? Open App info, tap ⋮, then Allow restricted settings.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }) {
                            Text("Open App info")
                        }
                    }
                }
            }

            Spacer(Modifier.size(14.dp))
            ScreeningSection(resumed, mic, ::open)

            Spacer(Modifier.size(14.dp))
            Section("More", null) {
                if (modelIn) LinkRow("Remove the clearer model", "Frees ${IndicModel.SIZE / 1_000_000} MB. Transcripts it wrote stay.", external = false) { confirmRemove = true }
                LinkRow("Play Protect", "Paused it to install this? Turn it back on.") { open(Intent("com.google.android.gms.settings.VERIFY_APPS_SETTINGS")) }
                LinkRow("Guide and source", "New versions are on GitHub.") { open(Intent(Intent.ACTION_VIEW, Uri.parse(REPO))) }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove the clearer model?") },
            text = { Text("Transcripts it wrote stay. Getting it again is another ${IndicModel.SIZE / 1_000_000} MB download.") },
            confirmButton = { TextButton(onClick = { IndicModel.delete(context); modelIn = false; confirmRemove = false }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun ScreeningSection(resumed: Int, mic: Boolean, open: (Intent) -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val shell by Shell.status.collectAsState()
    val supported = remember { Screening.supported(context) }
    var voice by remember(resumed) { mutableStateOf(Greeting.ready(context)) }
    var making by remember { mutableStateOf(false) }
    var noVoice by remember { mutableStateOf(false) }
    var on by remember(resumed) { mutableStateOf(Screening.isOn(context)) }
    var voicemail by remember(resumed) { mutableStateOf(Screening.voicemailOn(context)) }
    var tryUntil by remember(resumed) { mutableLongStateOf(Screening.tryUntil(context) ?: 0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val connected = shell == Shell.Status.Ready

    LaunchedEffect(connected, resumed) {
        if (connected && !voice) {
            making = true
            voice = Greeting.prepare(context)
            noVoice = !voice
            making = false
        }
    }
    LaunchedEffect(tryUntil) {
        while (tryUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }
    val ready = supported && mic && connected && voice
    Section("Call screening", if (ready && on) "On" else null) {
        Text(
            "A stranger who has never called you hears a short greeting and says why they're calling. You watch their words live and can pick up. It rings if it sounds important, and otherwise keeps a message.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
        )
        if (!supported) {
            Text("This phone can't recognise speech on its own, so screening isn't available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            return@Section
        }
        Step("Shizuku running", "Lends the permission to answer quietly. Start it again after a restart.", shell != Shell.Status.NotRunning, "Open") {
            val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU)
            open(launch ?: Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_PAGE)))
        }
        if (shell != Shell.Status.NotRunning) {
            Step("Allowed in Shizuku", "One tap, once.", shell == Shell.Status.Ready || shell == Shell.Status.Starting || shell == Shell.Status.NoCallAudio, "Allow", onClick = Shell::askPermission)
        }
        if (shell == Shell.Status.NoCallAudio) {
            Step("Call audio", "This phone won't share the call's sound, so calls keep ringing as usual.", false, null) {}
        }
        if (connected) {
            Step(
                if (making) "Preparing the greeting…" else "Hindi voice",
                if (noVoice) "Add a Hindi voice in the phone's text-to-speech settings." else "Your phone says the greeting in its own voice.",
                voice, if (noVoice) "Open" else null,
            ) { open(Intent("com.android.settings.TTS_SETTINGS")) }
        }
        AnimatedVisibility(ready) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Screen unknown callers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(on, onCheckedChange = {
                        haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        on = it
                        Screening.setOn(context, it)
                    })
                }
                val left = ((tryUntil - now) / 1_000).coerceAtLeast(0)
                AnimatedContent(left > 0, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "try") { waiting ->
                    if (waiting) {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Call this phone now · %d:%02d".format(left / 60, left % 60), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                Screening.cancelTry(context)
                                tryUntil = 0
                            }) { Text("Cancel") }
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                Screening.tryNext(context)
                                tryUntil = Screening.tryUntil(context) ?: 0
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 52.dp),
                        ) { Text("Try it on the next call") }
                    }
                }
                VoicemailRow(voicemail) {
                    haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    voicemail = it
                    Screening.setVoicemail(context, it)
                }
            }
        }
    }
}

@Composable
internal fun VoicemailRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Take a message when you don't pick up", style = MaterialTheme.typography.titleMedium)
            Text(
                "A call still ringing at 27 seconds hears the greeting and can leave a message; pick up any time. Calls Tring silences are left alone.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(on, onCheckedChange = onChange)
    }
}

@Composable
private fun Section(title: String, badge: String?, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                AnimatedVisibility(badge != null, enter = fadeIn() + scaleIn()) {
                    Text(
                        badge.orEmpty(), style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer,
                        modifier = Modifier.clip(CircleShape).background(scheme.primaryContainer).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun Step(title: String, detail: String, done: Boolean, action: String?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pop by animateFloatAsState(if (done) 1f else 0.8f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "done")
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).scale(pop).clip(CircleShape).background(if (done) scheme.primary else scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { if (done) Icon(Icons.Rounded.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (!done && action != null) {
            Spacer(Modifier.width(10.dp))
            Button(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun LinkRow(title: String, detail: String, external: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (external) Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/** whether Tring Recorder is switched on in Accessibility */
fun recorderOn(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val me = ComponentName(context, CallRecorder::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}
