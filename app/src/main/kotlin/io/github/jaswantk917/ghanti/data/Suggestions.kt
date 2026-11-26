package io.github.jaswantk917.ghanti.data

import android.net.Uri
import io.github.jaswantk917.ghanti.signals.DeliveryRules
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/** Someone Recents offers to call, above its list. */
data class Suggested(val name: String?, val number: String, val photo: Uri?, val contactId: Long? = null)

/**
 * Who you're likely to call right now: someone you usually talk to at this hour, then a favourite,
 * then whoever you talked to last. A stranger who only ever called you is never offered, nor is
 * anyone Recents already shows on its first screen or you asked not to see here. Three people or
 * nobody: a strip of one or two looks unfinished.
 */
object Suggestions {
    private const val MOST = 3
    private const val AROUND_MIN = 60
    private const val USUAL_DAYS = 3
    private const val USUAL_LOOK_BACK_DAYS = 60L
    private const val RECENT_DAYS = 30L
    private const val DAY_MS = 24 * 60 * 60_000L

    private class Person(val key: String, val name: String?, val number: String, val photo: Uri?, val numbers: Set<String>, val calls: List<CallEntry>, val starred: Boolean, val contactId: Long? = null) {
        val talks = calls.filter { it.kind == Kind.Outgoing || it.kind == Kind.Incoming && it.durationSec > 0 }
    }

    /** [shown]: the numbers (last 10 digits) of the rows Recents shows without scrolling; [hidden]: numbers you said not to suggest */
    fun pick(
        contacts: List<Contact>,
        recents: List<RecentRow>,
        usual: Map<Long, String>,
        now: Long,
        shown: Set<String> = emptySet(),
        zone: ZoneId = ZoneId.systemDefault(),
        hidden: Set<String> = emptySet(),
    ): List<Suggested> {
        val calls = recents.flatMap { it.group.entries }.groupBy { DeliveryRules.last10(it.number) }
        val saved = HashSet<String>()
        val people = contacts.mapNotNull { c ->
            val numbers = c.numbers.map { DeliveryRules.last10(it.number) }.toSet()
            saved += numbers
            val number = c.usualNumber(usual[c.id])?.number
                ?: c.numbers.maxByOrNull { T9.affinity(false, calls[DeliveryRules.last10(it.number)].orEmpty(), now) }?.number
                ?: return@mapNotNull null
            val theirs = numbers.flatMap { calls[it].orEmpty() }.sortedByDescending { it.date }
            Person("c${c.id}", c.name, number, c.photo, numbers, theirs, c.starred, c.id)
        } + recents.distinctBy { DeliveryRules.last10(it.group.number) }.mapNotNull { row ->
            val last = DeliveryRules.last10(row.group.number)
            val theirs = calls[last].orEmpty()
            val dialled = theirs.any { it.kind == Kind.Outgoing }
            val flagged = row.name?.contains("spam", ignoreCase = true) == true
            if (last.length < 10 || last in saved || !dialled || flagged) null
            else Person(last, row.label ?: row.name, row.group.number, null, setOf(last), theirs, false)
        }
        val offered = people.filter { p -> p.numbers.none { it in shown || it in hidden } }

        val moment = Instant.ofEpochMilli(now).atZone(zone)
        val usuallyNow = offered
            .map { it to daysAroundNow(it, moment, now, zone) }
            .filter { it.second >= USUAL_DAYS }
            .sortedByDescending { it.second }
            .map { it.first }
        val favourites = offered.filter { it.starred }.sortedByDescending { T9.affinity(it.starred, it.calls, now) }
        val lately = offered
            .filter { p -> p.talks.firstOrNull()?.let { now - it.date <= RECENT_DAYS * DAY_MS } == true }
            .sortedByDescending { it.talks.first().date }

        val chosen = LinkedHashMap<String, Suggested>()
        fun add(person: Person?) {
            if (person != null && person.key !in chosen && chosen.size < MOST) chosen[person.key] = Suggested(person.name, person.number, person.photo, person.contactId)
        }
        // one of each kind first, so three favourites never crowd out the person you call every evening
        add(usuallyNow.firstOrNull())
        add(favourites.firstOrNull { it.key !in chosen })
        add(lately.firstOrNull { it.key !in chosen })
        (usuallyNow + favourites + lately).forEach(::add)
        return chosen.values.toList().takeIf { it.size == MOST }.orEmpty()
    }

    private fun daysAroundNow(person: Person, moment: ZonedDateTime, now: Long, zone: ZoneId) = person.talks
        .filter { now - it.date <= USUAL_LOOK_BACK_DAYS * DAY_MS }
        .map { Instant.ofEpochMilli(it.date).atZone(zone) }
        .filter { minutesApart(it, moment) <= AROUND_MIN }
        .map { it.toLocalDate() }
        .distinct()
        .size

    private fun minutesApart(a: ZonedDateTime, b: ZonedDateTime): Int {
        val gap = abs(a.hour * 60 + a.minute - b.hour * 60 - b.minute)
        return minOf(gap, 24 * 60 - gap)
    }
}
