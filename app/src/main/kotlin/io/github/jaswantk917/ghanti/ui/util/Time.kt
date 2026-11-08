package io.github.jaswantk917.ghanti.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()
private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val full = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val dayHeader = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

/** "now", "7 min", "2:14 pm", "Yesterday", "Fri", "29 Oct", "29 Oct 2024" */
fun shortTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - millis) / 60_000
    if (minutes < 1) return "now"
    if (minutes < 60) return "$minutes min"
    val then = Instant.ofEpochMilli(millis).atZone(zone)
    val days = ChronoUnit.DAYS.between(then.toLocalDate(), LocalDate.now(zone))
    return when {
        days == 0L -> then.format(clock).lowercase()
        days == 1L -> "Yesterday"
        days < 7 -> then.format(weekday)
        then.year == LocalDate.now(zone).year -> then.format(dayMonth)
        else -> then.format(full)
    }
}

fun clockTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(clock).lowercase()

fun dayLabel(millis: Long): String {
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, LocalDate.now(zone))
    return when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        day.year == LocalDate.now(zone).year -> day.format(dayHeader)
        else -> day.format(full)
    }
}

fun sameDay(a: Long, b: Long): Boolean =
    Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** when something later happens: "today, 6:00 pm", "tomorrow, 8:00 am", "Tue 12 Nov, 9:00 am" */
fun laterLabel(millis: Long, now: Long = System.currentTimeMillis()): String {
    val then = Instant.ofEpochMilli(millis).atZone(zone)
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), then.toLocalDate())
    val time = then.format(clock).lowercase()
    return when (days) {
        0L -> "today, $time"
        1L -> "tomorrow, $time"
        else -> "${then.format(weekdayDayMonth)}, $time"
    }
}
