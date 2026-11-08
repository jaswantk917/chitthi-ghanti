package io.github.jaswantk917.ghanti.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.provider.CallLog.Calls
import android.util.Log
import io.github.jaswantk917.ghanti.signals.DeliveryRules
import java.time.Instant
import java.time.ZoneId

enum class Kind { Incoming, Outgoing, Missed, Declined, Blocked }

data class CallEntry(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val kind: Kind,
    val date: Long,
    val durationSec: Long,
    val isNew: Boolean,
)

/** a run of calls with one number on one day, newest first; shown as one row */
data class RecentGroup(val entries: List<CallEntry>) {
    val latest get() = entries.first()
    val number get() = latest.number
    val key get() = "g${latest.id}"
}

object Recents {
    private const val TAG = "Recents"

    fun load(context: Context, limit: Int = 500): List<CallEntry> = try {
        context.contentResolver.query(
            Calls.CONTENT_URI,
            arrayOf(Calls._ID, Calls.NUMBER, Calls.CACHED_NAME, Calls.TYPE, Calls.DATE, Calls.DURATION, Calls.NEW),
            // the call log's strict SQL rejects LIMIT tucked into the sort order
            Bundle().apply {
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(Calls.DATE))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            },
            null,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CallEntry(
                            id = c.getLong(0),
                            number = c.getString(1).orEmpty(),
                            cachedName = c.getString(2)?.takeIf { it.isNotBlank() },
                            kind = kindOf(c.getInt(3)),
                            date = c.getLong(4),
                            durationSec = c.getLong(5),
                            isNew = c.getInt(6) == 1,
                        )
                    )
                }
            }
        }.orEmpty()
    } catch (e: SecurityException) {
        Log.w(TAG, "no call log access", e)
        emptyList()
    }

    fun delete(context: Context, ids: Collection<Long>) {
        if (ids.isEmpty()) return
        context.contentResolver.delete(Calls.CONTENT_URI, "${Calls._ID} IN (${ids.joinToString(",")})", null)
    }

    /** missed calls stop counting as new once the list has been seen */
    fun markSeen(context: Context) {
        try {
            context.contentResolver.update(
                Calls.CONTENT_URI, ContentValues().apply { put(Calls.NEW, 0) },
                "${Calls.NEW} = 1 AND ${Calls.TYPE} = ${Calls.MISSED_TYPE}", null,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "can't write the call log", e)
        }
    }

    /** newest-first [entries] folded into rows: same number, same day, and the same kind of missed-or-not */
    fun group(entries: List<CallEntry>, zone: ZoneId = ZoneId.systemDefault()): List<RecentGroup> {
        val out = ArrayList<RecentGroup>()
        var run = ArrayList<CallEntry>()
        fun day(e: CallEntry) = Instant.ofEpochMilli(e.date).atZone(zone).toLocalDate()
        for (e in entries) {
            val prev = run.lastOrNull()
            val joins = prev != null &&
                DeliveryRules.last10(prev.number) == DeliveryRules.last10(e.number) &&
                prev.number.isNotBlank() &&
                day(prev) == day(e) &&
                (prev.kind == Kind.Missed) == (e.kind == Kind.Missed)
            if (!joins && run.isNotEmpty()) {
                out += RecentGroup(run)
                run = ArrayList()
            }
            run += e
        }
        if (run.isNotEmpty()) out += RecentGroup(run)
        return out
    }

    private fun kindOf(type: Int) = when (type) {
        Calls.OUTGOING_TYPE -> Kind.Outgoing
        Calls.MISSED_TYPE -> Kind.Missed
        Calls.REJECTED_TYPE -> Kind.Declined
        Calls.BLOCKED_TYPE -> Kind.Blocked
        else -> Kind.Incoming
    }
}
