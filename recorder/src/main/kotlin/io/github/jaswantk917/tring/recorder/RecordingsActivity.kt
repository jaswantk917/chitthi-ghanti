package io.github.jaswantk917.tring.recorder

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.rounded.ExpandMore
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
    val context = LocalContext.current
    val shell by Shell.status.collectAsState()
    val screening = remember(shell, recordings) { Screening.isOn(context) && Screening.canRun(context) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = bars.calculateTopPadding() + 20.dp, bottom = bars.calculateBottomPadding() + 40.dp),
    ) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Your calls", style = MaterialTheme.typography.displaySmall)
                    Text(
                        when {
                            screening -> "Answering strangers for you"
                            recordings.isNullOrEmpty() -> "Kept on this phone, only for you"
                            recordings.size == 1 -> "1 call kept on this phone"
                            else -> "${recordings.size} calls kept on this phone"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (screening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                FilledTonalIconButton(onClick = onSetup, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Tune, "Settings") }
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
                                label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp).animateItem(),
                            )
                        }
                    }
                    item(key = recording.id) {
                        RecordingRow(
                            recording, notes, tick,
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
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Version ${update.version} is out", style = MaterialTheme.typography.titleMedium)
                val first = update.notes.lineSequence().map { it.trim().removePrefix("- ").removePrefix("* ") }.firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                if (first != null) {
                    Text(first, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { onUpdate(update) }) { Text("Get it") }
        }
    }
}

@Composable
private fun SetupCard(onSetup: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.tertiaryContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("One step left", style = MaterialTheme.typography.titleMedium, color = scheme.onTertiaryContainer)
                Text("Calls can't be recorded until this is done.", style = MaterialTheme.typography.bodySmall, color = scheme.onTertiaryContainer, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onSetup, colors = ButtonDefaults.buttonColors(containerColor = scheme.tertiary, contentColor = scheme.onTertiary)) { Text("Finish") }
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
    // three lines until asked for more, so your calls stay on the first screen
    var open by remember { mutableStateOf(prefs.getBoolean(LATELY_OPEN, false)) }
    val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "fold")
    if (canSummarize == false) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 16.dp).animateContentSize()) {
            val color = MaterialTheme.colorScheme.onSurface
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    open = !open
                    prefs.edit().putBoolean(LATELY_OPEN, open).apply()
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Lately", style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
                if (open) RegenerateButton(enabled = !busy && summarised > 0, onClick = { processor.lately(recordings) })
                Icon(
                    Icons.Rounded.ExpandMore, if (open) "Show less" else "Show more", tint = color,
                    modifier = Modifier.padding(12.dp).size(24.dp).rotate(turn),
                )
            }
            val state = when {
                busy -> 0
                text != null -> 1
                working -> 2
                else -> 3
            }
            Crossfade(state, label = "lately") {
                when (it) {
                    0 -> Thinking(if (summarised == 1) "Reading your last call…" else "Reading your last $summarised calls…", color)
                    1 -> Text(
                        text.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 14.dp),
                    )
                    2 -> Thinking("Listening to your recent calls…", color)
                    else -> Text("What you've been talking about shows here once calls are transcribed.", style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp, end = 12.dp))
                }
            }
        }
    }
}

/** one call: who, what it was about in their words, and how long, with no box around it */
@Composable
private fun RecordingRow(recording: Recording, notes: Notes, tick: Int, working: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val scheme = MaterialTheme.colorScheme
    val gist by produceState<String?>(null, recording.id, tick) {
        value = withContext(Dispatchers.IO) {
            notes.summary(recording.id)?.takeIf { it.isNotEmpty() }?.lineSequence()?.first()?.removePrefix("• ")
                ?: notes.transcript(recording.id)?.let(Excerpt::of)?.text
        }
    }
    val message = remember(recording.id, tick) { notes.isMessage(recording.id) }
    Row(
        modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(recording.who, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(recording.who, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (message) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Message", style = MaterialTheme.typography.labelSmall, color = scheme.onTertiaryContainer, maxLines = 1,
                            modifier = Modifier.clip(CircleShape).background(scheme.tertiaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Crossfade(working, label = "row") { busy ->
                    if (busy) LoadingIndicator(Modifier.size(20.dp))
                    else Text(timeLabel(recording.at), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
            gist?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Text(spokenLength(recording.durationMs), style = MaterialTheme.typography.labelSmall, color = scheme.outline, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Empty() {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.GraphicEq, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
        }
        Text("Your calls will land here", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
        Text(
            "Tap Record during a call in Tring, or let it record calls by itself.",
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 32.dp, end = 32.dp),
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
    val work = all[recording.id]
    val lines = remember(recording.id, tick) { notes.transcript(recording.id) }
    val summary = remember(recording.id, tick) { notes.summary(recording.id)?.let { Summarizer.tidy(it, recording.durationMs) } }
    val clearer = remember(recording.id, tick) { notes.clearer(recording.id) }
    val model by IndicModel.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    val supported = Shruti.available
    val playback = rememberPlayback(recording)
    val context = LocalContext.current
    val fits = remember { IndicModel.runsHere(context) }

    LaunchedEffect(recording.id) {
        IndicModel.refresh(context)
        when {
            work != null -> Unit
            lines == null && supported -> processor.transcribe(recording)
            !lines.isNullOrEmpty() && summary == null -> processor.summarize(recording)
        }
    }

    val bars = WindowInsets.systemBars.asPaddingValues()
    val listening = work as? Processor.Work.Listening
    val shown = listening?.lines ?: lines.orEmpty()
    val list = rememberLazyListState()
    val turns = remember(recording.id, tick) { notes.turns(recording.id) }
    val message = remember(recording.id, tick) { notes.isMessage(recording.id) }
    // the bar only lifts off the page once the page has moved under it
    val lifted by animateFloatAsState(if (list.canScrollBackward) 1f else 0f, label = "bar")
    Column(Modifier.fillMaxSize()) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = lifted))
            .padding(top = bars.calculateTopPadding())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(
            recording.who, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp).alpha(lifted),
        )
        IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "Share") }
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
    }
    LazyColumn(
        Modifier.fillMaxWidth().weight(1f),
        state = list,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bars.calculateBottomPadding() + 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(recording.who, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(recording.who, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (message) "Left a message · " else "") + "${dayLabel(recording.at)}, ${timeLabel(recording.at)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "player") { Player(playback, turns) }
        if (canSummarize != false || summary != null) {
            item(key = "summary") {
                SummaryCard(
                    summary, working = work == Processor.Work.Summarizing, waiting = lines == null,
                    canRegenerate = !lines.isNullOrEmpty() && work == null, onRegenerate = { processor.summarize(recording) },
                )
            }
        }
        item(key = "transcript-head") {
            Column(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("What was said", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (listening == null && shown.isNotEmpty()) CopyButton { Minute.plain(shown) }
                    RegenerateButton(enabled = listening == null && supported, onClick = { processor.transcribe(recording) })
                }
                if (shown.any { it.unsure }) {
                    Text(
                        "Faded words were hard to make out, so they may be wrong.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    )
                }
            }
        }
        item(key = "transcript-state") {
            Column(Modifier.animateContentSize()) {
                if (supported && fits) {
                    ClearerOffer(
                        model, written = clearer, busy = work != null, onMobileData = rememberOnMobileData(),
                        onDownload = { IndicModel.download(context) }, onCancel = IndicModel::cancel, onWrite = { processor.clearer(recording) },
                    )
                }
                listening?.let { TranscribeProgress(it.progress, onStop = if (it.clearer) processor::stopClearer else null) }
                (work as? Processor.Work.Failed)?.let { Text(it.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
                if (!supported) {
                    Text("Transcripts need a phone with a 64-bit ARM processor.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                } else if (work == null && lines?.isEmpty() == true) {
                    Text("No words were made out in this call.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
        items(Minute.of(shown), key = { "minute-${it.index}" }) { SpokenMinute(it, Modifier.animateItem()) }
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
private fun Player(playback: Playback, turns: List<Turn>?) {
    val haptics = LocalHapticFeedback.current
    val player = playback.player
    val playing = playback.playing
    val position = playback.position
    val length = playback.length
    // the button rounds into a circle while it plays and settles back into a soft square at rest
    val corner by animateIntAsState(if (playing) 50 else 30, MaterialTheme.motionScheme.fastSpatialSpec(), label = "shape")
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 18.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                VoiceTimeline(
                    turns, length, position, enabled = player != null,
                    onSeek = { to -> playback.position = to; player?.seekTo(to) },
                )
                Row {
                    Text(durationLabel(position.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(durationLabel(length.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: String?, working: Boolean, waiting: Boolean, canRegenerate: Boolean, onRegenerate: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface
    var whole by rememberSaveable { mutableStateOf(false) }
    var clipped by remember(summary) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 14.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("In short", style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
                RegenerateButton(enabled = canRegenerate, onClick = onRegenerate)
            }
            Crossfade(Triple(working, summary, waiting), label = "summary") { (busy, text, early) ->
                val quiet = color.copy(alpha = 0.7f)
                val inset = Modifier.padding(end = 14.dp)
                when {
                    busy -> Thinking("Summarising on this phone…", color)
                    text == "" -> Text("Too short to tell what it was about.", style = MaterialTheme.typography.bodyMedium, color = quiet, modifier = inset)
                    text != null -> Column {
                        Text(
                            text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (whole) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis, modifier = inset,
                            onTextLayout = { if (!whole) clipped = it.hasVisualOverflow },
                        )
                        if (clipped || whole) {
                            Text(
                                if (whole) "Show less" else "Show all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(12.dp)).clickable { whole = !whole }.padding(vertical = 12.dp, horizontal = 2.dp),
                            )
                        }
                    }
                    early -> Text("Comes once the transcript is ready.", style = MaterialTheme.typography.bodyMedium, color = quiet, modifier = inset)
                    else -> Text("Tap ↻ to summarise.", style = MaterialTheme.typography.bodyMedium, color = quiet, modifier = inset)
                }
            }
        }
    }
}

/** the whole transcript to the clipboard, marked private so the phone doesn't preview a call on screen */
@Composable
private fun CopyButton(text: () -> String) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_600)
            copied = false
        }
    }
    IconButton(onClick = {
        val clip = ClipData.newPlainText("Call transcript", text())
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        copied = true
    }) {
        AnimatedContent(copied, transitionSpec = { (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) }, label = "copy") {
            Icon(if (it) Icons.Rounded.Check else Icons.Rounded.ContentCopy, if (it) "Copied" else "Copy the transcript")
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

/** [onStop]: the clearer model is the one listening, in its own process, and can be stopped */
@Composable
private fun TranscribeProgress(progress: Transcriber.Progress, onStop: (() -> Unit)?) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        val fraction by animateFloatAsState(if (progress.of == 0) 0f else progress.done.toFloat() / progress.of, label = "listening")
        if (progress.of == 0) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        else LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.padding(top = 4.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            val who = if (onStop != null) "Writing a clearer transcript" else "Listening on this phone"
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

/** The clearer model stays one quiet line until you ask about it, or while it's working. */
@Composable
private fun ClearerOffer(
    state: IndicModel.State,
    written: Boolean,
    busy: Boolean,
    onMobileData: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onWrite: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val underway = state is IndicModel.State.Downloading || state == IndicModel.State.Checking || state is IndicModel.State.Failed
    if (asked || underway || written || busy) {
        ClearerCard(state, written, busy, onMobileData, onDownload, onCancel, onWrite)
    } else {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                .clickable { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); asked = true }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Make this transcript clearer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * The clearer transcript, offered beside the built-in one: the model's download while it isn't in,
 * then a button to write this call again with it, and once it has, a quiet line saying so.
 */
@Composable
internal fun ClearerCard(
    state: IndicModel.State,
    written: Boolean,
    busy: Boolean,
    onMobileData: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onWrite: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var downloading by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is IndicModel.State.Downloading || state == IndicModel.State.Checking) downloading = true
        // the one big moment here: a long download landed
        if (state == IndicModel.State.Ready && downloading) {
            downloading = false
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    val scheme = MaterialTheme.colorScheme
    val stage = when {
        state != IndicModel.State.Ready -> "model"
        busy -> "busy"
        written -> "written"
        else -> "offer"
    }
    AnimatedContent(stage, transitionSpec = { (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically()) }, label = "clearer") { now ->
        when (now) {
            "busy" -> Spacer(Modifier.fillMaxWidth())
            "written" -> Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Written by the clearer model", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            else -> Surface(color = scheme.tertiaryContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                val color = scheme.onTertiaryContainer
                Column(Modifier.padding(20.dp).animateContentSize()) {
                    Text("Clearer transcript", style = MaterialTheme.typography.titleMedium, color = color)
                    when (state) {
                        IndicModel.State.Ready -> {
                            Text(
                                "Hear this call again with AI4Bharat's Hindi model, which makes out calls far better. It can take a few minutes, with a note and a Stop while it works.",
                                style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(top = 4.dp),
                            )
                            Button(
                                onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onWrite() },
                                colors = ButtonDefaults.buttonColors(containerColor = scheme.tertiary, contentColor = scheme.onTertiary),
                                modifier = Modifier.padding(top = 12.dp),
                            ) { Text("Write it") }
                        }
                        is IndicModel.State.Downloading -> {
                            val fraction by animateFloatAsState(state.done.toFloat() / IndicModel.SIZE, label = "download")
                            LinearWavyProgressIndicator(
                                progress = { fraction }, color = color, trackColor = color.copy(alpha = 0.24f),
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${state.done / 1_000_000} of ${IndicModel.SIZE / 1_000_000} MB",
                                    style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onCancel() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = color),
                                ) { Text("Cancel") }
                            }
                        }
                        IndicModel.State.Checking -> {
                            LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = color, trackColor = color.copy(alpha = 0.24f))
                            Text("Checking it arrived whole, then unpacking…", style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 8.dp))
                        }
                        IndicModel.State.Missing, is IndicModel.State.Failed -> {
                            Text(
                                "AI4Bharat's Hindi model makes out calls far better than the built-in one. It's a ${IndicModel.SIZE / 1_000_000} MB download, once, and needs ${"%.1f".format(2 * IndicModel.SIZE / 1e9)} GB free while it unpacks. After that it works offline, on the calls you choose.",
                                style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(top = 4.dp),
                            )
                            AnimatedVisibility(onMobileData) {
                                Text(
                                    "You're on mobile data, and this would use ${IndicModel.SIZE / 1_000_000} MB of it.",
                                    style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            if (state is IndicModel.State.Failed) {
                                Row(Modifier.padding(top = 8.dp)) {
                                    Icon(Icons.Rounded.ErrorOutline, null, tint = color, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = color)
                                }
                            }
                            Button(
                                onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onDownload() },
                                colors = ButtonDefaults.buttonColors(containerColor = scheme.tertiary, contentColor = scheme.onTertiary),
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text(
                                    when {
                                        onMobileData -> "Download anyway"
                                        state is IndicModel.State.Failed -> "Try again"
                                        else -> "Download"
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** follows the connection, so turning Wi-Fi on while the card is up changes what it says */
@Composable
private fun rememberOnMobileData(): Boolean {
    val manager = LocalContext.current.getSystemService(ConnectivityManager::class.java)
    var metered by remember { mutableStateOf(manager.isActiveNetworkMetered) }
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        onDispose { manager.unregisterNetworkCallback(callback) }
    }
    return metered
}

/** a minute of the call as one paragraph, its time in the margin; only the words it was unsure of are faded */
@Composable
private fun SpokenMinute(minute: Minute, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val faded = scheme.onSurface.copy(alpha = 0.45f)
    val paragraph = remember(minute, faded) {
        buildAnnotatedString {
            minute.lines.forEachIndexed { i, line ->
                if (i > 0) append(' ')
                if (line.unsure) withStyle(SpanStyle(color = faded)) { append(line.text) } else append(line.text)
            }
        }
    }
    Row(modifier.fillMaxWidth().animateContentSize().padding(vertical = 4.dp)) {
        Text(
            durationLabel(minute.atMs.toLong()), style = MaterialTheme.typography.labelMedium, color = scheme.primary,
            modifier = Modifier.width(48.dp).padding(top = 4.dp),
        )
        SelectionContainer(Modifier.weight(1f)) {
            Text(paragraph, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
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

/** "2 min", the way you'd say how long a call was */
private fun spokenLength(ms: Long): String {
    val seconds = ms / 1_000
    return when {
        seconds < 60 -> "$seconds sec"
        seconds < 3_600 -> "${(seconds + 30) / 60} min"
        else -> "${seconds / 3_600} hr ${seconds / 60 % 60} min"
    }
}

private const val LATELY_OPEN = "lately_full"
