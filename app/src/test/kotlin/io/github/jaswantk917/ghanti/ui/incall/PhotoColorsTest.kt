package io.github.jaswantk917.ghanti.ui.incall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoColorsTest {
    private fun px(hue: Float, chroma: Float, tone: Float, count: Int) = List(count) { floatArrayOf(hue, chroma, tone) }

    private fun near(expected: Float, actual: Float, within: Float = 4f) {
        val gap = ((actual - expected + 540f) % 360f) - 180f
        assertTrue("hue $actual, expected about $expected", kotlin.math.abs(gap) <= within)
    }

    @Test
    fun aWarmPhotoGivesAWarmPage() {
        val seed = PhotoColors.seed(px(88f, 48f, 70f, 600) + px(0f, 2f, 50f, 1000))
        near(88f, seed.hue)
        assertEquals(48f, seed.chroma, 0.5f)
    }

    @Test
    fun aGreyPhotoGivesANearlyGreyPage() {
        assertEquals(PhotoColors.GREY, PhotoColors.seed(px(200f, 4f, 50f, 2000)))
    }

    @Test
    fun aFewColourfulPixelsInAGreyPhotoStillCount() {
        // a red scarf on a grey wall: a small patch, but it's the photo's colour
        val seed = PhotoColors.seed(px(25f, 60f, 45f, 120) + px(0f, 3f, 60f, 2184))
        near(25f, seed.hue)
        assertTrue(seed.chroma >= PhotoColors.MIN_CHROMA)
    }

    @Test
    fun theCommonestColourWinsOverAStrongerSmallOne() {
        val seed = PhotoColors.seed(px(260f, 40f, 50f, 900) + px(30f, 70f, 60f, 150))
        near(260f, seed.hue)
    }

    @Test
    fun huesEitherSideOfRedMeetAtRedNotCyan() {
        val seed = PhotoColors.seed(px(355f, 40f, 50f, 400) + px(5f, 40f, 50f, 400))
        near(0f, seed.hue)
    }

    @Test
    fun nearBlackAndNearWhiteDontCount() {
        // shadows read as strongly coloured in some photos; they shouldn't colour the page
        val seed = PhotoColors.seed(px(20f, 60f, 5f, 1500) + px(120f, 50f, 97f, 1500) + px(200f, 30f, 60f, 300))
        near(200f, seed.hue)
    }

    @Test
    fun aSkinToneSplitAcrossTwoBinsStillWins() {
        val seed = PhotoColors.seed(px(48f, 30f, 65f, 300) + px(52f, 30f, 65f, 300) + px(150f, 35f, 50f, 400))
        near(50f, seed.hue)
    }
}
