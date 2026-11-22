package io.github.jaswantk917.tring.recorder

import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.rounded.ExpandMore
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The recorder's home: your recorded calls, each with its transcript and a summary, made on the phone. */
class RecordingsActivity : ComponentActivity() {
    private val notes by lazy { Notes(this) }
    private val processor by lazy { Processor.get(this) }
    private var recordings by mutableStateOf<List<Recording>?>(null)
    private var setupDone by mutableStateOf(true)
    private var update by mutableStateOf<Release?>(null)
    private var open by mutableStateOf<Long?>(null)
    private val deleteRequest = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        open = savedInstanceState?.getLong(EXTRA_OPEN, -1)?.takeIf { it >= 0 } ?: opened(intent)
        Updates.schedule(this)
        lifecycleScope.launch {
            runCatching { Updates.check(this@RecordingsActivity) }
            update = Updates.available(this@RecordingsActivity)
        }
        setContent {
            RecorderTheme {
                RecorderApp(
                    recordings = recordings,
                    setupDone = setupDone,
                    update = update,
                    open = open,
                    notes = notes,
                    processor = processor,
                    onOpen = { open = it },
                    onSetup = { startActivity(Intent(this, SetupActivity::class.java)) },
                    onUpdate = { runCatching { startActivity(Updates.download(it)) } },
                    onShare = ::share,
                    onDelete = ::delete,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opened(intent)?.let { open = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        open?.let { outState.putLong(EXTRA_OPEN, it) }
    }

    override fun onResume() {
        super.onResume()
        update = Updates.available(this)
        refresh()
    }

    private fun opened(intent: Intent?) = intent?.getLongExtra(EXTRA_OPEN, -1)?.takeIf { it >= 0 }

    private fun refresh() {
        setupDone = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && recorderOn(this)
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { Recordings.list(this@RecordingsActivity) }
            recordings = found
            processor.catchUp(found)
        }
    }

    private fun share(recording: Recording) {
        val send = Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, recording.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, recording.who))
    }

    private fun delete(recording: Recording) {
        notes.forget(recording.id)
        try {
            contentResolver.delete(recording.uri, null, null)
            refresh()
        } catch (e: SecurityException) {
            // one saved before a reinstall isn't this install's to delete: Android asks the person
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val ask = MediaStore.createDeleteRequest(contentResolver, listOf(recording.uri))
                deleteRequest.launch(IntentSenderRequest.Builder(ask.intentSender).build())
            }
        }
    }

    companion object {
        /** the MediaStore id of a recording to open straight away, from the "Call recorded" note */
        const val EXTRA_OPEN = "open"
    }
}

@Composable
fun RecorderTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
}

@Composable
fun RecorderApp(
    recordings: List<Recording>?,
    setupDone: Boolean,
    update: Release?,
    open: Long?,
    notes: Notes,
    processor: Processor,
    onOpen: (Long?) -> Unit,
    onSetup: () -> Unit,
    onUpdate: (Release) -> Unit,
    onShare: (Recording) -> Unit,
    onDelete: (Recording) -> Unit,
) {
    val current = recordings?.firstOrNull { it.id == open }
    BackHandler(current != null) { onOpen(null) }
    // a Surface, not a background: it is what gives text and icons their on-surface colour
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    AnimatedContent(
        current,
        contentKey = { it?.id },
        transitionSpec = {
            if (targetState != null) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 } + fadeOut())
            else (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
        },
        modifier = Modifier.fillMaxSize(),
        label = "screen",
    ) { recording ->
        if (recording == null) {
            RecordingList(recordings, setupDone, update, notes, processor, onSetup, onUpdate, onOpen = { onOpen(it.id) })
        } else {
            RecordingDetail(recording, notes, processor, onBack = { onOpen(null) }, onShare = { onShare(recording) }, onDelete = { onOpen(null); onDelete(recording) })
        }
    }
    }
}

@Composable
private fun RecordingList(
    recordings: List<Recording>?,
    setupDone: Boolean,
    update: Release?,
    notes: Notes,
    processor: Processor,
    onSetup: () -> Unit,
    onUpdate: (Release) -> Unit,
    onOpen: (Recording) -> Unit,
) {
    val bars = WindowInsets.systemBars.asPaddingValues()
    val work by processor.work.collectAsState()
    val tick by processor.saved.collectAsState()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = bars.calculateTopPadding() + 24.dp, bottom = bars.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Recordings", style = MaterialTheme.typography.headlineLarge)
                    if (!recordings.isNullOrEmpty()) {
                        Text(
                            if (recordings.size == 1) "1 call" else "${recordings.size} calls",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onSetup) { Icon(Icons.Rounded.Tune, "Setup") }
            }
        }
        if (update != null) item(key = "update") { UpdateCard(update, onUpdate) }
        if (!setupDone) item(key = "setup") { SetupCard(onSetup) }
        if (!recordings.isNullOrEmpty()) item(key = "lately") { LatelyCard(recordings, notes, processor, tick, working = work.isNotEmpty()) }
        when {
            recordings == null -> Unit
            recordings.isEmpty() -> item(key = "empty") { Empty() }
            else -> {
                var day = ""
                recordings.forEach { recording ->
                    val label = dayLabel(recording.at)
                    if (label != day) {
                        day = label
                        item(key = "day-$label-${recording.id}") {
                            Text(
                                label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp).animateItem(),
                            )
                        }
                    }
                    item(key = recording.id) {
                        RecordingRow(
                            recording,
                            summarised = remember(recording.id, tick) { !notes.summary(recording.id).isNullOrEmpty() },
                            working = work[recording.id].let { it != null && it !is Processor.Work.Failed },
                            onClick = { onOpen(recording) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(update: Release, onUpdate: (Release) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Version ${update.version} is out", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            val first = update.notes.lineSequence().map { it.trim().removePrefix("- ").removePrefix("* ") }.firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            if (first != null) {
                Text(first, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Button(
                onClick = { onUpdate(update) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary),
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("Download") }
        }
    }
}

@Composable
private fun SetupCard(onSetup: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Finish setting up", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "Calls can't be recorded until the microphone and Accessibility are on.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Button(
                onClick = onSetup,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text("Set up") }
        }
    }
}

/** what you've been talking about lately: a summary of your recent calls' summaries */
@Composable
private fun LatelyCard(recordings: List<Recording>, notes: Notes, processor: Processor, tick: Int, working: Boolean) {
    val busy by processor.latelyBusy.collectAsState()
    val canSummarize by processor.canSummarize.collectAsState()
    val text = remember(tick) { notes.lately()?.first }
    val summarised = remember(recordings, tick) { processor.summarised(recordings).size }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // folded away stays folded away, the next time the app opens too
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var open by remember { mutableStateOf(prefs.getBoolean(LATELY_OPEN, true)) }
    val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "fold")
    if (canSummarize == false) return
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 16.dp).animateContentSize()) {
            val color = MaterialTheme.colorScheme.onPrimaryContainer
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    open = !open
                    prefs.edit().putBoolean(LATELY_OPEN, open).apply()
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Lately", style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
                if (open) RegenerateButton(enabled = !busy && summarised > 0, onClick = { processor.lately(recordings) })
                Icon(
                    Icons.Rounded.ExpandMore, if (open) "Fold Lately away" else "Show Lately", tint = color,
                    modifier = Modifier.padding(12.dp).size(24.dp).rotate(turn),
                )
            }
            if (!open) return@Column
            val state = when {
                busy -> 0
                text != null -> 1
                working -> 2
                else -> 3
            }
            Crossfade(state, label = "lately") {
                when (it) {
                    0 -> Thinking(if (summarised == 1) "Reading your last call…" else "Reading your last $summarised calls…", color)
                    1 -> Text(text.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(top = 8.dp, end = 12.dp))
                    2 -> Thinking("Listening to your recent calls…", color)
                    else -> Text("What you've been talking about shows here once calls are transcribed.", style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp, end = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(recording: Recording, summarised: Boolean, working: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    Row(
        modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(recording.who.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(recording.who, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${timeLabel(recording.at)} · ${durationLabel(recording.durationMs)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Crossfade(if (working) 1 else if (summarised) 2 else 0, label = "row") {
            when (it) {
                1 -> LoadingIndicator(Modifier.size(28.dp))
                2 -> Icon(Icons.Rounded.AutoAwesome, "Summarised", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                else -> Unit
            }
        }
    }
}

@Composable
private fun Empty() {
    Column(Modifier.fillMaxWidth().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text("No recordings yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Tap Record during a call in Tring, or let it record calls by itself.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp),
        )
    }
}

@Composable
private fun RecordingDetail(
    recording: Recording,
    notes: Notes,
    processor: Processor,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val tick by processor.saved.collectAsState()
    val all by processor.work.collectAsState()
    val canSummarize by processor.canSummarize.collectAsState()
    val resting by processor.resting.collectAsState()
    val model by WhisperModel.state.collectAsState()
    val work = all[recording.id]
    val lines = remember(recording.id, tick) { notes.transcript(recording.id) }
    val summary = remember(recording.id, tick) { notes.summary(recording.id) }
    val madeBy = remember(recording.id, tick) { notes.madeBy(recording.id) }
    var confirmDelete by remember { mutableStateOf(false) }
    val supported = Shruti.available || Whisper.supported
    val whisper = model == WhisperModel.State.Ready
    val playback = rememberPlayback(recording)
    val context = LocalContext.current

    LaunchedEffect(recording.id) {
        WhisperModel.refresh(context)
        when {
            work != null -> Unit
            lines == null && supported && processor.due(recording.id) -> processor.transcribe(recording)
            !lines.isNullOrEmpty() && summary == null -> processor.summarize(recording)
        }
    }

    val bars = WindowInsets.systemBars.asPaddingValues()
    val listening = work as? Processor.Work.Listening
    val shown = listening?.lines ?: lines.orEmpty()
    val list = rememberLazyListState()
    // the bar only lifts off the page once the page has moved under it
    val lifted by animateFloatAsState(if (list.canScrollBackward) 1f else 0f, label = "bar")
    Column(Modifier.fillMaxSize()) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = lifted))
            .padding(top = bars.calculateTopPadding())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "Share") }
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
    }
    LazyColumn(
        Modifier.fillMaxWidth().weight(1f),
        state = list,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bars.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text(recording.who, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${dayLabel(recording.at)}, ${timeLabel(recording.at)} · ${durationLabel(recording.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "player") { Player(playback) }
        if (canSummarize != false || summary != null) {
            item(key = "summary") {
                SummaryCard(
                    summary, working = work == Processor.Work.Summarizing, waiting = lines == null,
                    canRegenerate = !lines.isNullOrEmpty() && work == null, onRegenerate = { processor.summarize(recording) },
                )
            }
        }
        item(key = "transcript-head") {
            Column(Modifier.padding(start = 8.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Transcript", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    RegenerateButton(enabled = listening == null && supported, onClick = { processor.transcribe(recording) })
                }
                if (whisper && madeBy == Transcriber.Engine.Shruti && listening == null) {
                    RedoWithWhisper(onRedo = { processor.transcribe(recording) })
                }
                if (shown.any { it.unsure }) {
                    Text(
                        "Faded lines were hard to make out, so their words may be wrong.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    )
                }
            }
        }
        item(key = "transcript-state") {
            Column(Modifier.animateContentSize()) {
                if (Whisper.supported) WhisperCard(model, onDownload = { WhisperModel.download(context) }, onCancel = WhisperModel::cancel)
                if (lines == null && work == null && whisper && !resting) WaitingForCharger(onNow = { processor.transcribe(recording) })
                listening?.let { TranscribeProgress(it.progress, onStop = if (it.whisper) processor::stopWhisper else null) }
                (work as? Processor.Work.Failed)?.let { Text(it.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
                if (!supported) {
                    Text("Transcripts need a phone with a 64-bit ARM processor.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                } else if (work == null && lines?.isEmpty() == true) {
                    Text("No words were made out in this call.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                }
            }
        }
        items(shown.withIndex().toList(), key = { (i, _) -> "line-$i" }) { (_, line) -> Bubble(line, Modifier.animateItem()) }
    }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this recording?") },
            text = { Text("The recording, its transcript and its summary go for good.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

/** a call's player, held by the page: a list item is dropped once it scrolls off, and the sound went with it */
private class Playback(val player: MediaPlayer?) {
    var playing by mutableStateOf(false)
    var position by mutableIntStateOf(0)
    val length = player?.duration?.coerceAtLeast(1) ?: 1
}

@Composable
private fun rememberPlayback(recording: Recording): Playback {
    val context = LocalContext.current
    val playback = remember(recording.id) {
        Playback(runCatching { MediaPlayer().apply { setDataSource(context, recording.uri); prepare() } }.getOrNull())
    }
    DisposableEffect(playback) {
        playback.player?.setOnCompletionListener { playback.playing = false; playback.position = 0 }
        onDispose { playback.player?.release() }
    }
    LaunchedEffect(playback, playback.playing) {
        while (playback.playing && playback.player != null) {
            playback.position = playback.player.currentPosition
            delay(50)
        }
    }
    return playback
}

@Composable
private fun Player(playback: Playback) {
    val haptics = LocalHapticFeedback.current
    val player = playback.player
    val playing = playback.playing
    val position = playback.position
    val length = playback.length
    // the button rounds into a circle while it plays and settles back into a soft square at rest
    val corner by animateIntAsState(if (playing) 50 else 30, MaterialTheme.motionScheme.fastSpatialSpec(), label = "shape")
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = {
                    val p = player ?: return@FilledIconButton
                    if (playing) { p.pause(); playback.playing = false; haptics.performHapticFeedback(HapticFeedbackType.ToggleOff) }
                    else { p.start(); playback.playing = true; haptics.performHapticFeedback(HapticFeedbackType.ToggleOn) }
                },
                enabled = player != null,
                shape = RoundedCornerShape(corner),
                colors = IconButtonDefaults.filledIconButtonColors(),
                modifier = Modifier.size(64.dp),
            ) {
                Crossfade(playing, label = "icon") { Icon(if (it) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (it) "Pause" else "Play", modifier = Modifier.size(32.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Slider(
                    value = position.toFloat() / length,
                    onValueChange = { playback.position = (it * length).toInt(); player?.seekTo(playback.position) },
                    enabled = player != null,
                )
                Row {
                    Text(durationLabel(position.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(durationLabel(length.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: String?, working: Boolean, waiting: Boolean, canRegenerate: Boolean, onRegenerate: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Summary", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                RegenerateButton(enabled = canRegenerate, onClick = onRegenerate)
            }
            val color = MaterialTheme.colorScheme.onSecondaryContainer
            Crossfade(Triple(working, summary, waiting), label = "summary") { (busy, text, early) ->
                when {
                    busy -> Thinking("Summarising on this phone…", color)
                    text == "" -> Text("Too short to tell what it was about.", style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp, end = 12.dp))
                    text != null -> Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(top = 8.dp, end = 12.dp))
                    early -> Text("Comes once the transcript is ready.", style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp, end = 12.dp))
                    else -> Text("Tap ↻ to summarise.", style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp, end = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun RegenerateButton(enabled: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onClick() }, enabled = enabled) {
        Icon(Icons.Rounded.Refresh, "Regenerate")
    }
}

@Composable
private fun Thinking(label: String, color: Color) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LoadingIndicator(Modifier.size(32.dp), color = color)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/**
 * The offer of Whisper, beside the transcript it would write: what it costs, its download as it
 * goes, and a moment of "it's in" once it lands here, after which it folds away.
 */
@Composable
internal fun WhisperCard(state: WhisperModel.State, onDownload: () -> Unit, onCancel: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    // seen arriving on this screen: worth saying so before folding away
    var arrived by remember { mutableStateOf(false) }
    var wasDownloading by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is WhisperModel.State.Downloading || state == WhisperModel.State.Checking) wasDownloading = true
        if (state == WhisperModel.State.Ready && wasDownloading) {
            wasDownloading = false
            arrived = true
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(4_000)
            arrived = false
        }
    }
    AnimatedVisibility(
        visible = state != WhisperModel.State.Ready || arrived,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val scheme = MaterialTheme.colorScheme
        Surface(color = scheme.tertiaryContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(Modifier.padding(20.dp).animateContentSize()) {
                val color = scheme.onTertiaryContainer
                AnimatedContent(state, contentKey = { it::class }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "whisper") { now ->
                    Column {
                        when (now) {
                            WhisperModel.State.Missing, is WhisperModel.State.Failed -> {
                                Text("Clearer transcripts", style = MaterialTheme.typography.titleMedium, color = color)
                                Text(
                                    "Whisper makes out calls far better than the built-in model. It's a ${WhisperModel.SIZE / 1_000_000} MB download, and it writes transcripts while the phone charges with its screen off.",
                                    style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(top = 4.dp),
                                )
                                if (now is WhisperModel.State.Failed) {
                                    Row(Modifier.padding(top = 8.dp)) {
                                        Icon(Icons.Rounded.ErrorOutline, null, tint = color, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(now.message, style = MaterialTheme.typography.bodyMedium, color = color)
                                    }
                                }
                                Button(
                                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onDownload() },
                                    colors = ButtonDefaults.buttonColors(containerColor = scheme.tertiary, contentColor = scheme.onTertiary),
                                    modifier = Modifier.padding(top = 12.dp),
                                ) { Text(if (now is WhisperModel.State.Failed) "Try again" else "Download") }
                            }
                            is WhisperModel.State.Downloading -> {
                                val fraction by animateFloatAsState(now.done.toFloat() / WhisperModel.SIZE, label = "download")
                                Text("Downloading Whisper", style = MaterialTheme.typography.titleMedium, color = color)
                                LinearWavyProgressIndicator(
                                    progress = { fraction }, color = color, trackColor = color.copy(alpha = 0.24f),
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                )
                                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${now.done / 1_000_000} of ${WhisperModel.SIZE / 1_000_000} MB",
                                        style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onCancel() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = color),
                                    ) { Text("Cancel") }
                                }
                            }
                            WhisperModel.State.Checking -> {
                                Text("Downloading Whisper", style = MaterialTheme.typography.titleMedium, color = color)
                                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = color, trackColor = color.copy(alpha = 0.24f))
                                Text("Checking it arrived whole…", style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 8.dp))
                            }
                            WhisperModel.State.Ready -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = color, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Whisper is in", style = MaterialTheme.typography.titleMedium, color = color)
                                }
                                Text(
                                    "From now on it writes transcripts while the phone charges with its screen off.",
                                    style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** a call Whisper hasn't written up yet because the phone isn't resting on the charger, with the choice not to wait */
@Composable
private fun WaitingForCharger(onNow: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.BatteryChargingFull, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Whisper writes this while the phone charges with its screen off.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onNow() }) { Text("Now") }
    }
}

/** a transcript Shruti wrote before Whisper came in */
@Composable
private fun RedoWithWhisper(onRedo: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Written by the built-in model.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onRedo() }) { Text("Redo with Whisper") }
    }
}

/** [onStop]: Whisper is listening, and the phone is slow until it stops */
@Composable
private fun TranscribeProgress(progress: Transcriber.Progress, onStop: (() -> Unit)?) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (progress.of == 0) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        else LinearWavyProgressIndicator(progress = { progress.done.toFloat() / progress.of }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val who = if (onStop != null) "Whisper is listening" else "Listening on this phone"
            Text(
                if (progress.of == 0) "$who…" else "$who · ${progress.done} of ${progress.of}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            if (onStop != null) {
                TextButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onStop() }) { Text("Stop") }
            }
        }
    }
}

/** a line as a chat bubble: yours on the right, theirs on the left */
@Composable
private fun Bubble(line: Line, modifier: Modifier = Modifier) {
    val mine = line.them == false
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (line.them != null || line.unsure) {
            val who = when (line.them) { false -> "You · "; true -> "Them · "; null -> "" }
            Text(
                who + durationLabel(line.atMs.toLong()) + if (line.unsure) " · unclear" else "",
                style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        Surface(
            color = if (mine) scheme.primaryContainer else scheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp),
            modifier = Modifier.widthIn(max = 300.dp).alpha(if (line.unsure) 0.45f else 1f),
        ) {
            Text(
                line.text, style = MaterialTheme.typography.bodyLarge,
                color = if (mine) scheme.onPrimaryContainer else scheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

private fun dayLabel(at: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        same(day, today) -> "Today"
        same(day, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(at))
    }
}

private fun timeLabel(at: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(at))

private fun durationLabel(ms: Long): String {
    val seconds = ms / 1_000
    return if (seconds >= 3_600) "%d:%02d:%02d".format(seconds / 3_600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}

private const val LATELY_OPEN = "lately_open"
