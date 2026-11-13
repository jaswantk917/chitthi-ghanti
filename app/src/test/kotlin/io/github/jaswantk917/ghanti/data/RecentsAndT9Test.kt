package io.github.jaswantk917.ghanti.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecentsAndT9Test {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun t(day: Int, hour: Int) = LocalDateTime.of(2026, 11, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun call(id: Long, number: String, kind: Kind, at: Long) = CallEntry(id, number, null, kind, at, 0, false)

    @Test
    fun `calls with one number on one day fold together, missed ones apart from the rest`() {
        val entries = listOf(
            call(6, "+919876500001", Kind.Missed, t(7, 14)),
            call(5, "09876500001", Kind.Missed, t(7, 13)),
            call(4, "9876500001", Kind.Outgoing, t(7, 12)),
            call(3, "9876500001", Kind.Incoming, t(7, 11)),
            call(2, "9876500001", Kind.Incoming, t(6, 11)),
            call(1, "8123456789", Kind.Outgoing, t(6, 10)),
        )
        val groups = Recents.group(entries, zone).map { g -> g.entries.map { it.id } }
        assertEquals(listOf(listOf(6L, 5L), listOf(4L, 3L), listOf(2L), listOf(1L)), groups)
    }

    @Test
    fun `private numbers never fold`() {
        val entries = listOf(call(2, "", Kind.Missed, t(7, 12)), call(1, "", Kind.Missed, t(7, 11)))
        assertEquals(2, Recents.group(entries, zone).size)
    }

    @Test
    fun `keypad letters find names by first word, any word, initials, then number`() {
        assertEquals(4, T9.score("727", "Papaya", "9876500001"))
        assertEquals(3, T9.score("586", "Mohan Kumar", "9876500002"))
        assertEquals(2, T9.score("65", "Mohan Kumar Sharma", "9876500002"))
        assertEquals(1, T9.score("65000", "Papaya", "9876500001"))
        assertEquals(0, T9.score("999", "Papaya", "9876500001"))
        assertTrue(T9.score("2", "Amma", "1") > 0)
    }

    private data class Hit(val name: String, val score: Int, val affinity: Double)

    @Test
    fun `the keypad puts the people you call most first, favourites ahead of strangers to you`() {
        val now = t(13, 10)
        val mum = T9.affinity(starred = true, calls = emptyList(), now = now)
        val rider = T9.affinity(starred = false, calls = listOf(call(1, "1", Kind.Incoming, t(13, 9))), now = now)
        val friend = T9.affinity(starred = false, calls = (1L..4L).map { call(it, "2", Kind.Outgoing, t(12, 20)) }, now = now)
        val blocked = T9.affinity(starred = false, calls = listOf(call(9, "3", Kind.Blocked, t(13, 9))), now = now)
        assertTrue(friend > mum && mum > rider && rider > blocked)
        assertEquals(0.0, blocked, 0.0)
        // two weeks on, the same call counts half
        assertEquals(0.5, T9.affinity(false, listOf(call(1, "1", Kind.Outgoing, t(1, 10))), t(15, 10)), 0.001)

        val ranked = T9.rank(
            listOf(Hit("Aarav", 4, 0.0), Hit("Bhaiya", 4, 3.0), Hit("Chacha", 3, 5.0), Hit("Number only", 1, 9.0), Hit("No match", 0, 9.0)),
            Hit::score, Hit::affinity,
        ).map { it.name }
        assertEquals(listOf("Chacha", "Bhaiya", "Aarav", "Number only"), ranked)
    }

    @Test
    fun `of one contact's numbers, the one you call suggests first`() {
        val now = t(13, 10)
        val work = Hit("Bhaiya · work", 4, T9.affinity(starred = true, calls = listOf(call(1, "1", Kind.Outgoing, t(1, 10))), now = now))
        val mobile = Hit("Bhaiya · mobile", 4, T9.affinity(starred = true, calls = (2L..4L).map { call(it, "2", Kind.Outgoing, t(12, 10)) }, now = now))
        assertEquals(listOf(mobile, work), T9.rank(listOf(work, mobile), Hit::score, Hit::affinity))
    }
}
