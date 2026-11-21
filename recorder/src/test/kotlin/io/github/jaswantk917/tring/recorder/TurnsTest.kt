package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TurnsTest {
    /** [script]: one char per 20 ms frame: Y you, T them, . nobody */
    private fun turns(script: String) = Turns.from(
        FloatArray(script.length) { if (script[it] == 'T') 1f else 0f },
        BooleanArray(script.length) { script[it] != '.' },
    )

    @Test
    fun `each person's stretch is a turn`() {
        val t = turns(".....YYYYYYYYYYYYYYYYYYYY" + ".".repeat(30) + "TTTTTTTTTTTTTTTTTTTT.....")
        assertEquals(listOf(false, true), t.map { it.them })
        assertEquals(0, t[0].startMs) // 150 ms early, but not before the start
        assertEquals(500 + 150, t[0].endMs)
        assertEquals(1100 - 150, t[1].startMs)
        assertEquals(1500 + 100, t[1].endMs) // not past the end
    }

    @Test
    fun `a short pause doesn't split a turn, a long one does`() {
        assertEquals(1, turns("YYYYYYYYYYYYYYYYYYYY" + ".".repeat(30) + "YYYYYYYYYYYYYYYYYYYY").size)
        assertEquals(2, turns("YYYYYYYYYYYYYYYYYYYY" + ".".repeat(40) + "YYYYYYYYYYYYYYYYYYYY").size)
    }

    @Test
    fun `a cough isn't a turn`() = assertEquals(listOf(false), turns("YYYYYYYYYYYYYYYYYYYY.....TTTTT.....YYYYYYYYYYYYYYYYYYYY").map { it.them }.distinct())

    @Test
    fun `padding stops halfway to the neighbour`() {
        val t = turns("YYYYYYYYYYYYYYYYYYYY.....TTTTTTTTTTTTTTTTTTTT")
        assertEquals(450, t[0].endMs)
        assertEquals(450, t[1].startMs)
    }

    @Test
    fun `turns survive a round trip through a file`() {
        val t = turns(".....YYYYYYYYYYYYYYYYYYYY.....TTTTTTTTTTTTTTTTTTTT.....")
        val file = File.createTempFile("turns", ".txt").apply { deleteOnExit() }
        Turns.write(t, file)
        assertEquals(t, Turns.read(file))
    }

    @Test
    fun `a recording is listed under who the call was with`() {
        assertEquals("Asha", Recordings.who("Asha · 21 Nov, 3.16 pm.m4a"))
        assertEquals("+91 98765 43210", Recordings.who("+91 98765 43210 · 21 Nov, 3.16 pm.m4a"))
        assertEquals("Call", Recordings.who(" · 21 Nov, 3.16 pm.m4a"))
    }
}
