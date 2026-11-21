package io.github.jaswantk917.ghanti.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.ghanti.incall.Recorder
import io.github.jaswantk917.ghanti.screen.Outcome
import io.github.jaswantk917.ghanti.signals.DeliveryRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecentRow(
    val group: RecentGroup,
    val name: String?,
    val photo: Uri?,
    val contactId: Long?,
    val outcome: Outcome?,
    /** by call log id */
    val memos: Map<Long, Memo> = emptyMap(),
    /** who an earlier call from this number turned out to be */
    val before: String? = null,
    /** by call log id: the calls Tring Recorder recorded */
    val recordings: Map<Long, CallRecording> = emptyMap(),
) {
    /** who you said it was, newest call first */
    val label get() = group.entries.firstNotNullOfOrNull { memos[it.id]?.label }
    val title get() = name ?: label ?: group.number.ifBlank { "Private number" }
}

data class HomeState(
    val loaded: Boolean = false,
    val recents: List<RecentRow> = emptyList(),
    val contacts: List<Contact> = emptyList(),
)

/** Recents and contacts, reloaded whenever the call log or the address book changes. */
class Home(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state
    private var job: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload()
    }

    /** watching a provider needs its permission, so this runs again once they're granted */
    fun start() {
        stop()
        if (granted(Manifest.permission.READ_CALL_LOG)) {
            context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        }
        if (granted(Manifest.permission.READ_CONTACTS)) {
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        }
        // a recording lands a minute or so after its call ends, and its summary later still
        if (Recorder.installed(context)) runCatching { context.contentResolver.registerContentObserver(CallRecordings.LIST, true, observer) }
        reload()
    }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun stop() = context.contentResolver.unregisterContentObserver(observer)

    fun reload() {
        job?.cancel()
        job = scope.launch {
            _state.value = withContext(Dispatchers.IO) { load() }
        }
    }

    private fun load(): HomeState {
        val contacts = Contacts.all(context)
        val byNumber = HashMap<String, Contact>()
        contacts.forEach { c -> c.numbers.forEach { byNumber[DeliveryRules.last10(it.number)] = c } }
        val log = GhantiApp.instance.screenLog
        val memos = GhantiApp.instance.memos.all.value
        val calls = Recents.load(context)
        val recorded = if (Recorder.installed(context)) CallRecordings.match(calls, CallRecordings.load(context)) else emptyMap()
        val recents = Recents.group(calls).map { group ->
            val contact = byNumber[DeliveryRules.last10(group.number)]
            val oldest = group.entries.last().date
            RecentRow(
                group = group,
                name = contact?.name ?: group.latest.cachedName,
                photo = contact?.photo,
                contactId = contact?.id,
                outcome = if (contact == null) log.forCall(group.number, group.latest.date) else null,
                memos = group.entries.mapNotNull { e -> MemoList.match(memos, e.number, e.date)?.let { e.id to it } }.toMap(),
                before = if (contact == null) MemoList.previousLabel(memos, group.number, oldest) else null,
                recordings = group.entries.mapNotNull { e -> recorded[e.id]?.let { e.id to it } }.toMap(),
            )
        }
        return HomeState(loaded = true, recents = recents, contacts = contacts)
    }
}
