package io.github.jaswantk917.ghanti.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FullPhotosTest {
    @Test
    fun asksOnlyForContactsNotAskedBefore() {
        assertEquals(listOf(3L, 5L), FullPhotos.pick(listOf(1L, 3L, 5L), setOf("1"), 10))
    }

    @Test
    fun asksForAFewAtATime() {
        assertEquals(20, FullPhotos.pick((1L..105L).toList(), emptySet(), FullPhotos.BATCH).size)
    }

    @Test
    fun aContactListedTwiceIsAskedForOnce() {
        assertEquals(listOf(7L), FullPhotos.pick(listOf(7L, 7L), emptySet(), 10))
    }

    @Test
    fun onceEveryoneIsAskedThereIsNothingLeft() {
        assertEquals(emptyList<Long>(), FullPhotos.pick(listOf(1L, 2L), setOf("1", "2"), 10))
    }
}
