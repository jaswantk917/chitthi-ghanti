package io.github.jaswantk917.ghanti.ui.home

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.data.CallRecording
import io.github.jaswantk917.ghanti.data.CallRecordings
import io.github.jaswantk917.ghanti.ui.components.pressMorph
import io.github.jaswantk917.ghanti.ui.util.Haptic
import io.github.jaswantk917.ghanti.ui.util.rememberHaptics
import kotlinx.coroutines.delay

/**
 * The one recording playing in Recents, held by the page rather than the row: a row scrolled off
 * the list is dropped, and the sound used to go with it.
 */
internal class RecentsPlayback(private val context: Context) {
    var id by mutableStateOf<Long?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var position by mutableIntStateOf(0)
        private set
    var failed by mutableStateOf<Long?>(null)
        private set
    private var player: MediaPlayer? = null

    fun positionOf(recording: CallRecording) = if (id == recording.id) position else 0

    fun toggle(recording: CallRecording) {
        val p = playerFor(recording) ?: return
        if (p.isPlaying) p.pause() else p.start()
        playing = p.isPlaying
    }

    fun seek(recording: CallRecording, to: Int) {
        val p = playerFor(recording) ?: return
        position = to
        p.seekTo(to)
    }

    /** while playing, where it's got to */
    fun tick() {
        player?.takeIf { playing }?.let { position = it.currentPosition }
    }

    fun stop() {
        player?.release()
        player = null
        id = null
        playing = false
        position = 0
    }

    private fun playerFor(recording: CallRecording): MediaPlayer? {
        if (id == recording.id) return player
        stop()
        return runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                // the recorder hands over the file itself; safe to close once the player has it
                context.contentResolver.openFileDescriptor(CallRecordings.sound(recording.id), "r")!!.use { setDataSource(it.fileDescriptor) }
                prepare()
                setOnCompletionListener {
                    playing = false
                    position = 0
                    it.seekTo(0)
                }
            }
        }.onFailure { failed = recording.id }.getOrNull()?.also {
            player = it
            id = recording.id
        }
    }
}

@Composable
internal fun rememberRecentsPlayback(): RecentsPlayback {
    val context = LocalContext.current
    val playback = remember { RecentsPlayback(context.applicationContext) }
    DisposableEffect(playback) { onDispose { playback.stop() } }
    LaunchedEffect(playback.playing) {
        while (playback.playing) {
            playback.tick()
            delay(200)
        }
    }
    return playback
}

/** A recorded call under its line in Recents: play, scrub, and a few lines of its transcript, the rest in the recorder. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecordingPlayer(recording: CallRecording, playback: RecentsPlayback, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val playing = playback.id == recording.id && playback.playing
    val position = playback.positionOf(recording)
    val failed = playback.failed == recording.id
    val length = recording.durationMs.toInt().coerceAtLeast(1)

    Column(
        modifier
            .padding(top = 4.dp, bottom = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val press = remember { MutableInteractionSource() }
            FilledIconButton(
                onClick = {
                    haptics.play(Haptic.Tap)
                    playback.toggle(recording)
                },
                enabled = !failed,
                shape = pressMorph(MaterialShapes.Circle, MaterialShapes.Cookie6Sided, press),
                interactionSource = press,
            ) {
                AnimatedContent(playing, transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "play") { on ->
                    Icon(if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (on) "Pause" else "Play recording")
                }
            }
            Spacer(Modifier.width(4.dp))
            Slider(
                value = position.toFloat().coerceIn(0f, length.toFloat()),
                onValueChange = { to -> playback.seek(recording, to.toInt()) },
                valueRange = 0f..length.toFloat(),
                enabled = !failed,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (failed) "Couldn't play this recording" else "${clock(position)} / ${clock(length)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (failed) scheme.error else scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (recording.excerpt == null) {
                TextButton(onClick = { haptics.play(Haptic.Tap); CallRecordings.open(context, recording.id) }) { Text("Transcript") }
            }
        }
        recording.excerpt?.let { excerpt ->
            TranscriptPeek(excerpt, recording.more) {
                haptics.play(Haptic.Tap)
                CallRecordings.open(context, recording.id)
            }
        }
    }
}

/** Up to four lines of what was said; anything longer opens whole in the recorder. */
@Composable
private fun TranscriptPeek(excerpt: String, more: Boolean, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var clipped by remember(excerpt) { mutableStateOf(false) }
    val opens = more || clipped
    Column(
        Modifier
            .padding(start = 4.dp, bottom = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (opens) Modifier.clickable(onClickLabel = "Read the whole call", onClick = onOpen) else Modifier)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            excerpt,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            maxLines = PEEK_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { clipped = it.hasVisualOverflow },
        )
        if (opens) {
            Text(
                "Read the whole call",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private const val PEEK_LINES = 4

private fun clock(ms: Int): String {
    val seconds = ms / 1_000
    return if (seconds >= 3_600) "%d:%02d:%02d".format(seconds / 3_600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}
