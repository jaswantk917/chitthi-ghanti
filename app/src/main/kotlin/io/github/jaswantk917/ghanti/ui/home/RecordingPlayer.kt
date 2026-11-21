package io.github.jaswantk917.ghanti.ui.home

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.data.CallRecording
import io.github.jaswantk917.ghanti.data.CallRecordings
import io.github.jaswantk917.ghanti.ui.components.pressMorph
import io.github.jaswantk917.ghanti.ui.util.Haptic
import io.github.jaswantk917.ghanti.ui.util.rememberHaptics
import kotlinx.coroutines.delay

/** A recorded call under its line in Recents: play, scrub, what it was about, and its transcript in the recorder. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecordingPlayer(recording: CallRecording, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val length = recording.durationMs.toInt().coerceAtLeast(1)

    DisposableEffect(recording.id) {
        onDispose {
            player?.release()
            player = null
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { position = it.currentPosition }
            delay(200)
        }
    }

    fun prepared(): MediaPlayer? = player ?: runCatching {
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            // the recorder hands over the file itself; safe to close once the player has it
            context.contentResolver.openFileDescriptor(CallRecordings.sound(recording.id), "r")!!.use { setDataSource(it.fileDescriptor) }
            prepare()
            seekTo(position)
            setOnCompletionListener {
                playing = false
                position = 0
                it.seekTo(0)
            }
        }
    }.onFailure { failed = true }.getOrNull()?.also { player = it }

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
                    val p = prepared() ?: return@FilledIconButton
                    if (p.isPlaying) p.pause() else p.start()
                    playing = p.isPlaying
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
                onValueChange = { to ->
                    position = to.toInt()
                    player?.seekTo(position)
                },
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
            TextButton(onClick = { haptics.play(Haptic.Tap); CallRecordings.open(context, recording.id) }) { Text("Transcript") }
        }
        recording.summary?.let { summary ->
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(start = 10.dp, end = 4.dp, bottom = 8.dp),
            )
        }
    }
}

private fun clock(ms: Int): String {
    val seconds = ms / 1_000
    return if (seconds >= 3_600) "%d:%02d:%02d".format(seconds / 3_600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}
