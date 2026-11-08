package io.github.jaswantk917.chitthi.ui.thread

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.chitthi.send.SendLaterKind
import io.github.jaswantk917.chitthi.send.sendLaterPresets
import io.github.jaswantk917.chitthi.ui.util.Haptic
import io.github.jaswantk917.chitthi.ui.util.laterLabel
import io.github.jaswantk917.chitthi.ui.util.pressScale
import io.github.jaswantk917.chitthi.ui.util.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Step { Presets, Date, Time }

private fun LocalDateTime.millis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Quick times first, then a full date and time picker. [onPick] gets epoch millis. */
@Composable
fun SendLaterSheet(title: String, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableStateOf(Step.Presets) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val presets = remember { sendLaterPresets(LocalDateTime.now()) }

    fun leaveSheet(then: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion { then() }
    }

    when (step) {
        Step.Presets -> ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(start = 4.dp, bottom = 16.dp))
                ExactTiming()
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    presets.forEachIndexed { i, preset ->
                        SheetRow(
                            icon = when (preset.kind) {
                                SendLaterKind.Morning -> Icons.Rounded.WbSunny
                                SendLaterKind.Evening -> Icons.Rounded.WbTwilight
                                SendLaterKind.Night -> Icons.Rounded.Bedtime
                            },
                            title = preset.label,
                            supporting = laterLabel(preset.at.millis()).replaceFirstChar { it.uppercase() },
                            index = i,
                            count = presets.size + 1,
                            onClick = {
                                haptics.play(Haptic.Success)
                                leaveSheet { onPick(preset.at.millis()) }
                            },
                        )
                    }
                    SheetRow(
                        icon = Icons.Rounded.CalendarMonth,
                        title = "Pick date and time",
                        supporting = null,
                        index = presets.size,
                        count = presets.size + 1,
                        onClick = {
                            haptics.play(Haptic.Tap)
                            leaveSheet { step = Step.Date }
                        },
                    )
                }
            }
        }

        Step.Date -> {
            val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val state = rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
                    override fun isSelectableYear(year: Int) = year >= LocalDate.now().year
                },
            )
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        enabled = state.selectedDateMillis != null,
                        onClick = {
                            haptics.play(Haptic.Tap)
                            date = Instant.ofEpochMilli(state.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                            step = Step.Time
                        },
                    ) { Text("Next") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            ) { DatePicker(state) }
        }

        Step.Time -> {
            val now = LocalTime.now()
            val state = rememberTimePickerState(
                initialHour = if (date == LocalDate.now()) (now.hour + 1) % 24 else 9,
                initialMinute = 0,
                is24Hour = false,
            )
            val at = date.atTime(state.hour, state.minute)
            val future = at > LocalDateTime.now()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TimePicker(state)
                        if (!future) {
                            Text("That time has passed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
                confirmButton = {
                    Button(enabled = future, onClick = { haptics.play(Haptic.Success); onPick(at.millis()) }) { Text("Schedule") }
                },
                dismissButton = { TextButton(onClick = { haptics.play(Haptic.Tap); step = Step.Date }) { Text("Back") } },
            )
        }
    }
}

/** a row of an Expressive segmented list: big outer corners on the group, small ones between rows */
@Composable
internal fun SheetRow(
    icon: ImageVector,
    title: String,
    supporting: String?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val big = 20.dp
    val small = 4.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small,
    )
    val interaction = remember { MutableInteractionSource() }
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        arrival.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = arrival.value
                alpha = p.coerceIn(0f, 1f)
                translationY = (1f - p) * 24.dp.toPx()
            }
            .pressScale(interaction, 0.97f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (tint == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.primary else tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
            supporting?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Android leaves exact alarms off for apps that aren't alarm clocks, so a scheduled message could
 * go out a few minutes late. One tap fixes it; the row goes away once it's allowed.
 */
@Composable
private fun ExactTiming() {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val alarms = remember { context.getSystemService(AlarmManager::class.java) }
    var exact by remember { mutableStateOf(alarms.canScheduleExactAlarms()) }
    LifecycleResumeEffect(Unit) {
        exact = alarms.canScheduleExactAlarms()
        onPauseOrDispose {}
    }
    AnimatedVisibility(!exact, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier
                .padding(bottom = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Send to the minute", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "Without this, Android may send it a few minutes late",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            TextButton(onClick = {
                haptics.play(Haptic.Tap)
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }) { Text("Allow") }
        }
    }
}
