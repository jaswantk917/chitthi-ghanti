package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test
    fun `versions compare by number, not by text`() {
        assertTrue(Updates.newer("1.10", "1.9"))
        assertTrue(Updates.newer("1.7", "1.6"))
        assertTrue(Updates.newer("2.0", "1.9.9"))
        assertFalse(Updates.newer("1.7", "1.7"))
        assertFalse(Updates.newer("1.7.0", "1.7"))
        assertFalse(Updates.newer("1.6", "1.7"))
        assertFalse(Updates.newer("nightly", "1.7"))
    }

    @Test
    fun `a GitHub release gives its version, notes and APK`() {
        val release = Updates.parse(
            """{"tag_name":"v1.8","body":"- Transcripts\n- Summaries","html_url":"https://github.com/jaswantk917/tring-recorder/releases/tag/v1.8",
               "draft":false,"prerelease":false,
               "assets":[{"name":"source.zip","browser_download_url":"https://x/source.zip"},{"name":"tring-recorder-1.8.apk","browser_download_url":"https://x/tring-recorder-1.8.apk"}]}"""
        )!!
        assertEquals("1.8", release.version)
        assertEquals("- Transcripts\n- Summaries", release.notes)
        assertEquals("https://x/tring-recorder-1.8.apk", release.apkUrl)
    }

    @Test
    fun `a release without an APK still points at its page`() {
        val release = Updates.parse("""{"tag_name":"v1.8","html_url":"https://github.com/jaswantk917/tring-recorder/releases/tag/v1.8","assets":[]}""")!!
        assertNull(release.apkUrl)
        assertEquals("https://github.com/jaswantk917/tring-recorder/releases/tag/v1.8", release.pageUrl)
    }

    @Test
    fun `drafts, pre-releases and junk are not updates`() {
        assertNull(Updates.parse("""{"tag_name":"v9.0","draft":true}"""))
        assertNull(Updates.parse("""{"tag_name":"v9.0","prerelease":true}"""))
        assertNull(Updates.parse("not json"))
    }
}
