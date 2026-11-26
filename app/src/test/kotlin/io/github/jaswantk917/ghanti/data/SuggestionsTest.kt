package io.github.jaswantk917.ghanti.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SuggestionsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = t(23, 21)
    private var nextId = 1L

    private fun t(day: Int, hour: Int) = LocalDateTime.of(2026, 11, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun call(number: String, kind: Kind, at: Long, seconds: Long = 60, name: String? = null) = CallEntry(nextId++, number, name, kind, at, seconds, false)
    private fun rows(vararg calls: CallEntry) = Recents.group(calls.sortedByDescending { it.date }, zone).map { group ->
        RecentRow(group, group.latest.cachedName, null, null, null)
    }
    private fun person(id: Long, name: String, number: String, starred: Boolean = false) =
        Contact(id, name, null, starred, listOf(PhoneNumber(number, "Mobile")))
    private fun pick(contacts: List<Contact>, recents: List<RecentRow>, usual: Map<Long, String> = emptyMap(), shown: Set<String> = emptySet(), at: Long = now, hidden: Set<String> = emptySet()) =
        Suggestions.pick(contacts, recents, usual, at, shown, zone, hidden)

    private val maa = person(1, "Maa", "9000000001")
    private val didi = person(2, "Didi", "9000000002", starred = true)
    private val ravi = person(3, "Ravi", "9000000003")
    private val asha = person(4, "Asha", "9000000004", starred = true)
    private val evenings = rows(
        call("9000000001", Kind.Outgoing, t(22, 21)),
        call("9000000001", Kind.Outgoing, t(21, 21)),
        call("9000000001", Kind.Incoming, t(20, 20)),
        call("9000000003", Kind.Outgoing, t(23, 10)),
    )

    @Test
    fun `whoever you call at this hour, then a favourite, then the last person you talked to`() {
        assertEquals(listOf("Maa", "Didi", "Ravi"), pick(listOf(maa, didi, ravi), evenings).map { it.name })
    }

    @Test
    fun `fewer than three people means no strip at all`() {
        val maaOnly = rows(call("9000000001", Kind.Outgoing, t(22, 21)), call("9000000001", Kind.Outgoing, t(21, 21)), call("9000000001", Kind.Outgoing, t(20, 21)))
        assertTrue(pick(listOf(maa, didi), maaOnly).isEmpty())
    }

    @Test
    fun `someone Recents already shows without scrolling is not offered again`() {
        assertEquals(listOf("Didi", "Ravi", "Asha"), pick(listOf(maa, didi, ravi, asha), evenings, shown = setOf("9000000001")).map { it.name })
    }

    @Test
    fun `hiding the people on screen can leave too few, and then nobody is offered`() {
        assertTrue(pick(listOf(maa, didi, ravi), evenings, shown = setOf("9000000001")).isEmpty())
    }

    @Test
    fun `a contact shown on either of their numbers is not offered`() {
        val papa = Contact(5, "Papa", null, true, listOf(PhoneNumber("9000000005", "Home"), PhoneNumber("9000000006", "Work")))
        val names = pick(listOf(papa, maa, didi, ravi, asha), evenings, shown = setOf("9000000006", "9000000001")).map { it.name }
        assertEquals(listOf("Didi", "Ravi", "Asha"), names)
    }

    @Test
    fun `everyone on the first screen leaves nothing to offer`() {
        assertTrue(pick(listOf(maa, didi, ravi), evenings, shown = setOf("9000000001", "9000000002", "9000000003")).isEmpty())
    }

    @Test
    fun `two days at this hour is not yet a habit`() {
        val recents = rows(call("9000000001", Kind.Outgoing, t(22, 21)), call("9000000001", Kind.Outgoing, t(21, 21)), call("9000000003", Kind.Outgoing, t(23, 10)))
        assertEquals(listOf("Didi", "Ravi", "Maa"), pick(listOf(maa, didi, ravi), recents).map { it.name })
    }

    @Test
    fun `the hour wraps around midnight`() {
        val owl = person(1, "Owl", "9000000001")
        val late = LocalDateTime.of(2026, 11, 23, 23, 50).atZone(zone).toInstant().toEpochMilli()
        val recents = rows(
            call("9000000001", Kind.Outgoing, t(22, 0)),
            call("9000000001", Kind.Outgoing, t(21, 0)),
            call("9000000001", Kind.Outgoing, t(20, 0)),
            call("9000000003", Kind.Outgoing, t(23, 10)),
        )
        assertEquals(listOf("Owl", "Didi", "Ravi"), pick(listOf(owl, didi, ravi), recents, at = late).map { it.name })
    }

    @Test
    fun `a stranger who only called you is never offered`() {
        val recents = rows(call("9111111111", Kind.Incoming, t(23, 10)), call("9000000003", Kind.Outgoing, t(22, 10)))
        val numbers = pick(listOf(didi, ravi, asha), recents).map { it.number }
        assertEquals(3, numbers.size)
        assertTrue("9111111111" !in numbers)
    }

    @Test
    fun `an unsaved number you dialled is offered`() {
        val recents = rows(call("9111111111", Kind.Outgoing, t(23, 10)), call("9000000003", Kind.Outgoing, t(22, 10)))
        assertEquals(listOf("9000000002", "9111111111", "9000000003"), pick(listOf(didi, ravi), recents).map { it.number })
    }

    @Test
    fun `a number the network flagged as spam is never offered, even if you called back`() {
        val recents = rows(call("9111111111", Kind.Outgoing, t(23, 10), name = "⛔ Spam Alert From Jio"), call("9000000003", Kind.Outgoing, t(22, 10)))
        assertEquals(listOf("Didi", "Ravi", "Asha"), pick(listOf(didi, ravi, asha), recents).map { it.name })
    }

    @Test
    fun `missed calls alone don't make someone recent`() {
        val kabir = person(6, "Kabir", "9000000007")
        val recents = rows(call("9000000003", Kind.Missed, t(23, 10), seconds = 0), call("9000000007", Kind.Outgoing, t(22, 10)))
        assertEquals(listOf("Didi", "Kabir", "Asha"), pick(listOf(ravi, didi, asha, kabir), recents).map { it.name })
    }

    @Test
    fun `someone with two numbers appears once, on the number you chose`() {
        val two = Contact(1, "Papa", null, true, listOf(PhoneNumber("9000000001", "Home"), PhoneNumber("9000000005", "Work")))
        val recents = rows(call("9000000001", Kind.Outgoing, t(23, 10)), call("9000000005", Kind.Outgoing, t(23, 9)), call("9000000003", Kind.Outgoing, t(22, 9)))
        val picked = pick(listOf(two, didi, ravi), recents, mapOf(1L to "9000000005"))
        assertEquals(listOf("9000000005"), picked.filter { it.name == "Papa" }.map { it.number })
        assertEquals(3, picked.size)
    }

    @Test
    fun `someone you said not to suggest gives way to the next person`() {
        assertEquals(listOf("Didi", "Ravi", "Asha"), pick(listOf(maa, didi, ravi, asha), evenings, hidden = setOf("9000000001")).map { it.name })
    }

    @Test
    fun `hiding one of a contact's numbers hides the contact`() {
        val papa = Contact(5, "Papa", null, true, listOf(PhoneNumber("9000000005", "Home"), PhoneNumber("9000000006", "Work")))
        val names = pick(listOf(papa, maa, didi, ravi, asha), evenings, hidden = setOf("9000000006", "9000000001")).map { it.name }
        assertEquals(listOf("Didi", "Ravi", "Asha"), names)
    }

    @Test
    fun `hiding leaves too few, and then nobody is offered`() {
        assertTrue(pick(listOf(maa, didi, ravi), evenings, hidden = setOf("9000000002")).isEmpty())
    }

    @Test
    fun `a saved person carries their contact, an unsaved number doesn't`() {
        val recents = rows(call("9111111111", Kind.Outgoing, t(23, 10)), call("9000000003", Kind.Outgoing, t(22, 10)))
        val picked = pick(listOf(didi, ravi), recents).associate { it.number to it.contactId }
        assertEquals(mapOf("9000000002" to 2L, "9111111111" to null, "9000000003" to 3L), picked)
    }

    @Test
    fun `never more than three`() {
        val many = (1L..6L).map { person(it, "P$it", "90000000$it".padEnd(10, '0'), starred = true) }
        assertEquals(3, pick(many, emptyList()).size)
    }

    @Test
    fun `nobody to suggest on a new phone`() {
        assertTrue(pick(emptyList(), emptyList()).isEmpty())
    }
}
