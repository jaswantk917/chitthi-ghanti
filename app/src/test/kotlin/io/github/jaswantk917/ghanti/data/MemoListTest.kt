package io.github.jaswantk917.ghanti.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoListTest {
    private val rider = "+91 98765 43210"
    private val t0 = 1_800_000_000_000L
    private val min = 60_000L

    @Test fun `a memo saved during the call is found from the call log a few seconds later`() {
        val memos = MemoList.save(emptyList(), rider, t0, "Ownly delivery", "")
        assertEquals("Ownly delivery", MemoList.match(memos, "9876543210", t0 + 8_000)?.label)
    }

    @Test fun `two calls close together each keep their own memo`() {
        var memos = MemoList.save(emptyList(), rider, t0, "Ownly delivery", "")
        memos = MemoList.save(memos, rider, t0 + 90_000, null, "gate 2")
        assertEquals("Ownly delivery", MemoList.match(memos, rider, t0 + 5_000)?.label)
        assertEquals("gate 2", MemoList.match(memos, rider, t0 + 95_000)?.note)
    }

    @Test fun `a redial twenty seconds later is its own call`() {
        var memos = MemoList.save(emptyList(), rider, t0, "Ownly delivery", "")
        memos = MemoList.save(memos, rider, t0 + 20_000, null, "second try")
        assertEquals(2, memos.size)
    }

    @Test fun `saving again edits the same memo and keeps its time`() {
        var memos = MemoList.save(emptyList(), rider, t0, "Ownly delivery", "")
        memos = MemoList.save(memos, rider, t0 + 4_000, "Ownly delivery", "left at the door")
        assertEquals(1, memos.size)
        assertEquals(t0, memos.single().at)
        assertEquals("left at the door", memos.single().note)
    }

    @Test fun `clearing both fields removes the memo`() {
        val memos = MemoList.save(emptyList(), rider, t0, "Ownly delivery", "")
        assertEquals(emptyList<Memo>(), MemoList.save(memos, rider, t0, "  ", ""))
    }

    @Test fun `an earlier label is only a hint for later calls, never for the same one`() {
        val memos = MemoList.save(emptyList(), rider, t0, "Ownly delivery", "")
        assertNull(MemoList.previousLabel(memos, rider, t0 + 5_000))
        assertEquals("Ownly delivery", MemoList.previousLabel(memos, rider, t0 + 3 * 24 * 60 * min))
        assertNull(MemoList.previousLabel(memos, "9123456780", t0 + 60 * min))
    }
}
