package io.github.jaswantk917.ghanti.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRecordingsTest {
    private val minute = 60_000L
    private fun call(id: Long, at: Long, seconds: Long, kind: Kind = Kind.Incoming) = CallEntry(id, "+919876543210", null, kind, at, seconds, false)
    private fun recording(id: Long, at: Long) = CallRecording(id, at, 1_000, null)

    @Test
    fun `a recording belongs to the call it started during`() {
        val calls = listOf(call(2, 30 * minute, 600), call(1, 0, 1_380))
        // the 23-minute call rang at 0 and was picked up 13 s later; the next started at 30 min
        val matched = CallRecordings.match(calls, listOf(recording(10, 13_000), recording(11, 30 * minute + 11_000)))
        assertEquals(mapOf(1L to 10L, 2L to 11L), matched.mapValues { it.value.id })
    }

    @Test
    fun `back-to-back calls each keep their own recording`() {
        // the next call started a minute after a long one ended
        val calls = listOf(call(2, 24 * minute, 600, Kind.Outgoing), call(1, 0, 1_380))
        val matched = CallRecordings.match(calls, listOf(recording(11, 24 * minute + 11_000), recording(10, 13_000)))
        assertEquals(mapOf(1L to 10L, 2L to 11L), matched.mapValues { it.value.id })
    }

    @Test
    fun `a missed call never gets a recording`() =
        assertTrue(CallRecordings.match(listOf(call(1, 0, 0, Kind.Missed)), listOf(recording(10, 1_000))).isEmpty())

    @Test
    fun `a recording long after the call ended matches nothing`() =
        assertTrue(CallRecordings.match(listOf(call(1, 0, 60)), listOf(recording(10, 10 * minute))).isEmpty())

    @Test
    fun `whole seconds in the media library still match`() =
        assertEquals(10L, CallRecordings.match(listOf(call(1, 5_400, 60)), listOf(recording(10, 5_000)))[1L]?.id)
}
