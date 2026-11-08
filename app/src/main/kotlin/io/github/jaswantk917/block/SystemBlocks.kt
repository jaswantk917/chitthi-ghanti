package io.github.jaswantk917.block

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import android.util.Log

/**
 * Android's own block list. Only the default SMS or phone app may touch it; numbers on it are
 * stopped by the system, for calls and SMS, before any app sees them.
 */
object SystemBlocks {
    private const val TAG = "SystemBlocks"

    fun available(context: Context): Boolean =
        runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false) && list(context) != null

    /** null when this app isn't allowed to read it */
    fun list(context: Context): List<String>? = try {
        context.contentResolver.query(BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null)
            ?.use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let(::add) } }
    } catch (e: SecurityException) {
        Log.w(TAG, "not the default SMS or phone app", e)
        null
    }

    fun block(context: Context, number: String): Boolean = try {
        context.contentResolver.insert(BlockedNumbers.CONTENT_URI, ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }) != null
    } catch (e: Exception) {
        Log.w(TAG, "can't block", e)
        false
    }

    fun unblock(context: Context, number: String): Boolean = try {
        BlockedNumberContract.unblock(context, number) > 0
    } catch (e: Exception) {
        Log.w(TAG, "can't unblock", e)
        false
    }
}
