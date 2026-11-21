package io.github.jaswantk917.tring.recorder

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A call left unfinished (1.8 mixed up two calls and hid a 23-minute one) comes back; an empty one goes. */
class RecoverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val made = ArrayList<Uri>()

    private fun unfinished(name: String, withSound: Boolean): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name · 21 Nov, 6.54 pm.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Recordings.folder())
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        made += uri
        if (withSound) context.contentResolver.openOutputStream(uri)!!.use { out ->
            InstrumentationRegistry.getInstrumentation().context.assets.open("spoken-hindi.m4a").use { it.copyTo(out) }
        }
        return uri
    }

    @After
    fun remove() = made.forEach { runCatching { context.contentResolver.delete(it, null, null) } }

    @Test
    fun anUnfinishedCallWithSoundIsListedAgain() {
        val lost = unfinished("Recover test", withSound = true)
        Recordings.recoverPending(context, before = System.currentTimeMillis() + 2_000)
        assertTrue(Recordings.list(context).any { it.uri == lost })
    }

    @Test
    fun anEmptyOneIsDeleted() {
        val empty = unfinished("Recover test empty", withSound = false)
        Recordings.recoverPending(context, before = System.currentTimeMillis() + 2_000)
        val left = context.contentResolver.query(empty, arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use { it.count } ?: 0
        assertEquals(0, left)
    }

    @Test
    fun aCallStillRecordingIsLeftAlone() {
        val live = unfinished("Recover test live", withSound = true)
        Recordings.recoverPending(context, before = System.currentTimeMillis() - 60_000)
        assertTrue(Recordings.list(context).none { it.uri == live })
    }
}
