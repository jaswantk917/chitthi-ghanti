package io.github.jaswantk917.chitthi.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import io.github.jaswantk917.chitthi.ai.Nano
import io.github.jaswantk917.chitthi.classify.Classifier
import io.github.jaswantk917.chitthi.classify.Links
import io.github.jaswantk917.chitthi.classify.SenderId
import io.github.jaswantk917.chitthi.model.Category
import io.github.jaswantk917.chitthi.model.Conversation
import io.github.jaswantk917.chitthi.model.Message
import io.github.jaswantk917.chitthi.model.SenderInfo
import io.github.jaswantk917.chitthi.model.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

data class FreshOtp(val code: String, val sender: SenderInfo, val message: Message)

data class InboxState(
    val loaded: Boolean = false,
    val hasPermission: Boolean = false,
    val isDefault: Boolean = false,
    val conversations: Map<Category, List<Conversation>> = emptyMap(),
    val freshOtp: FreshOtp? = null,
    /** bumps on every rebuild, so open threads re-read */
    val version: Int = 0,
    val sorting: Int = 0,
)

/**
 * Every message on the phone, sorted into conversations per sender and category. The system store
 * is the source of truth; this re-reads it whenever it changes and keeps verdicts cached by message.
 */
class Inbox(
    private val app: Context,
    private val store: LocalStore,
    private val brands: Brands,
    private val prefs: Prefs,
    val nano: Nano,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = _state

    private val lock = Mutex()
    private var messages: List<Message> = emptyList()
    private val ruleVerdicts = HashMap<String, Verdict>()
    private var nanoVerdicts: MutableMap<String, Category> = HashMap()
    private var overrides: Map<String, Category> = emptyMap()
    private var pinned: Set<String> = emptySet()
    private var readMarks: Map<String, Long> = emptyMap()
    private var brandGuesses: MutableMap<String, Brand?> = HashMap()
    private var contacts: Map<String, Contact> = emptyMap()
    private var recipients: Map<Long, List<String>> = emptyMap()
    private val senderKeys = HashMap<String, String>()
    private var senders: Map<String, SenderInfo> = emptyMap()
    private var repliedTo: Set<String> = emptySet()

    /** the conversation on screen right now; arrivals there need no notification */
    @Volatile
    var visibleSender: String? = null

    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    private var observing = false

    fun start() {
        if (!observing && hasPermission()) {
            observing = true
            app.contentResolver.registerContentObserver(
                Telephony.MmsSms.CONTENT_URI, true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        refreshes.trySend(Unit)
                    }
                },
            )
            scope.launch {
                for (unused in refreshes) {
                    reload(contactsToo = false)
                    // a burst of provider writes (an incoming multipart SMS, a bulk delete) is one change
                    delay(REFRESH_GAP_MS)
                }
            }
        }
        refreshes.trySend(Unit)
    }

    fun refresh(contactsToo: Boolean = true) {
        scope.launch { reload(contactsToo) }
    }

    /** For receivers, which may run in a fresh process: re-read now and wait for it. */
    suspend fun reloadNow() = reload(contactsToo = messages.isEmpty())

    fun message(key: String): Message? = messages.firstOrNull { it.key == key }

    // Play: SMS is read only while this is the default SMS app
    private fun hasPermission() =
        SmsStore.isDefault(app) && app.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private suspend fun reload(contactsToo: Boolean) = lock.withLock {
        if (!hasPermission()) {
            _state.update { it.copy(loaded = true, hasPermission = false) }
            return@withLock
        }
        val first = messages.isEmpty()
        if (first || contactsToo) {
            contacts = Contacts.load(app)
            overrides = store.overrides()
            pinned = store.pinned()
            readMarks = store.readMarks()
            nanoVerdicts = store.nanoVerdicts().toMutableMap()
            brandGuesses = store.brandGuesses().toMutableMap()
            senderKeys.clear()
            if (contactsToo) ruleVerdicts.clear()
        }
        recipients = SmsStore.threadRecipients(app)
        val all = (SmsStore.loadSms(app) + SmsStore.loadMms(app, recipients)).sortedByDescending { it.date }
        messages = all
        val replied = all.asSequence().filter { !it.incoming }.map { senderKey(it) }.toSet()
        val newlyReplied = replied - repliedTo
        repliedTo = replied
        if (!first && newlyReplied.isNotEmpty()) {
            all.forEach { if (senderKey(it) in newlyReplied) ruleVerdicts.remove(it.key) }
        }
        all.forEachIndexed { i, m ->
            ruleVerdicts.getOrPut(m.key) { classifyByRules(m) }
            if (first && i % PROGRESS_EVERY == 0) _state.update { it.copy(sorting = i) }
        }
        rebuild()
    }

    private fun classifyByRules(m: Message): Verdict {
        val key = senderKey(m)
        val id = SenderId.parse(m.address)
        val isContact = id is SenderId.Phone && contacts.containsKey(id.national)
        val verdict = Classifier.classify(id, m.body, isContact || key.startsWith("t:"))
        // a number you write to is someone you talk to, saved or not
        return if (verdict.category == Category.Unknown && (!m.incoming || key in repliedTo)) {
            verdict.copy(category = Category.Personal)
        } else verdict
    }

    fun verdict(m: Message): Verdict {
        val rules = ruleVerdicts[m.key] ?: classifyByRules(m)
        val override = overrides[senderKey(m)]
        val nano = nanoVerdicts[m.key]
        return when {
            override != null -> rules.copy(category = override, unsure = false)
            nano != null -> rules.copy(category = nano, unsure = false)
            else -> rules
        }
    }

    fun senderKey(m: Message): String = senderKeys.getOrPut("${m.threadId}|${m.address}") {
        val people = recipients[m.threadId]
        if (people != null && people.size > 1) return@getOrPut "t:${m.threadId}"
        when (val id = SenderId.parse(m.address.ifEmpty { people?.firstOrNull().orEmpty() })) {
            is SenderId.Business -> brands.find(id.header)?.let { "b:${it.name}" } ?: id.key
            else -> id.key
        }
    }

    private fun rebuild() {
        val firstOfKey = LinkedHashMap<String, Message>()
        val samples = HashMap<String, MutableList<Message>>()
        messages.forEach { m ->
            val key = senderKey(m)
            firstOfKey.putIfAbsent(key, m)
            if (key.startsWith("h:") || key.startsWith("r:")) samples.getOrPut(key) { ArrayList() }.let { if (it.size < SAMPLES) it += m }
        }
        senders = firstOfKey.mapValues { (key, m) -> describe(key, m, samples[key].orEmpty()) }

        data class Acc(val latest: Message, val verdict: Verdict, var unread: Int)
        val groups = LinkedHashMap<Pair<String, Category>, Acc>()
        messages.forEach { m ->
            val key = senderKey(m)
            val verdict = verdict(m)
            val acc = groups.getOrPut(key to verdict.category) { Acc(m, verdict, 0) }
            if (m.incoming && !m.read && m.date > (readMarks[key] ?: 0L)) acc.unread++
        }
        val byCategory = groups.entries
            .map { (k, acc) ->
                Conversation(k.first, k.second, senders.getValue(k.first), acc.latest, acc.verdict, acc.unread, k.first in pinned)
            }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.latest.date }) }

        val now = System.currentTimeMillis()
        val fresh = messages.asSequence()
            .takeWhile { now - it.date < FRESH_OTP_MS }
            .firstOrNull { it.incoming && verdict(it).otp != null }
            ?.let { FreshOtp(verdict(it).otp!!, senders.getValue(senderKey(it)), it) }

        _state.update {
            it.copy(
                loaded = true,
                hasPermission = true,
                isDefault = SmsStore.isDefault(app),
                conversations = byCategory,
                freshOtp = fresh,
                version = it.version + 1,
            )
        }
    }

    private fun describe(key: String, m: Message, samples: List<Message>): SenderInfo {
        val kind = key.substringBefore(':')
        val rest = key.substringAfter(':')
        return when (kind) {
            "p" -> {
                val contact = contacts[rest]
                SenderInfo(
                    key = key,
                    title = contact?.name ?: formatNumber(m.address),
                    subtitle = if (contact != null) formatNumber(m.address) else null,
                    photo = contact?.photo,
                    canReply = true,
                    replyAddresses = listOf(m.address),
                    isContact = contact != null,
                )
            }
            "c" -> SenderInfo(key, rest, "Short code", canReply = true, replyAddresses = listOf(m.address))
            "b" -> {
                val id = SenderId.parse(m.address) as? SenderId.Business
                val brand = id?.let { brands.find(it.header) }
                SenderInfo(key, rest, id?.header, logoDomain = brand?.domain, canReply = false)
            }
            "h", "r" -> {
                // the name comes from Nano's guess, else the brand its links point to
                val guess = brandGuesses[rest]
                val linked = samples.flatMap { Links.domains(it.body) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                val known = linked?.let(brands::byDomain)
                val domain = guess?.domain?.takeIf { it.isNotEmpty() } ?: known?.domain ?: linked
                val header = if (kind == "r") "RCS business chat" else rest
                SenderInfo(
                    key = key,
                    title = guess?.name ?: known?.name ?: if (kind == "r") linked?.let(Brands::nameFromDomain) ?: "Business chat" else rest,
                    subtitle = header,
                    logoDomain = domain,
                    canReply = false,
                )
            }
            else -> {
                val people = recipients[m.threadId].orEmpty()
                SenderInfo(
                    key = key,
                    title = people.joinToString(", ") { a -> contacts[a.filter(Char::isDigit).takeLast(10)]?.name ?: formatNumber(a) },
                    subtitle = "${people.size} people",
                    canReply = true,
                    replyAddresses = people,
                )
            }
        }
    }

    fun sender(key: String): SenderInfo? = senders[key]

    /** For "new message" to someone with no thread yet. */
    fun senderForNumber(number: String): SenderInfo {
        val id = SenderId.parse(number)
        senders[id.key]?.let { return it }
        val contact = (id as? SenderId.Phone)?.let { contacts[it.national] }
        return SenderInfo(
            key = id.key,
            title = contact?.name ?: formatNumber(number),
            subtitle = if (contact != null) formatNumber(number) else null,
            photo = contact?.photo,
            canReply = true,
            replyAddresses = listOf(number),
            isContact = contact != null,
        )
    }

    fun messagesOf(senderKey: String): List<Message> = messages.filter { senderKey(it) == senderKey }.asReversed()

    fun search(query: String, limit: Int = SEARCH_LIMIT): List<Pair<Message, SenderInfo>> {
        if (query.isBlank()) return emptyList()
        return messages.asSequence()
            .filter { it.body.contains(query, ignoreCase = true) || senders[senderKey(it)]?.title?.contains(query, true) == true }
            .take(limit)
            .map { it to senders.getValue(senderKey(it)) }
            .toList()
    }

    fun contactList(): List<Contact> = contacts.values.sortedBy { it.name.lowercase(Locale.ROOT) }

    suspend fun markRead(senderKey: String) = withContext(Dispatchers.IO) {
        val threads = messages.filter { senderKey(it) == senderKey }.map { it.threadId }.toSet()
        SmsStore.markRead(app, threads)
        val at = System.currentTimeMillis()
        store.putReadMark(senderKey, at)
        readMarks = readMarks + (senderKey to at)
        lock.withLock { rebuild() }
    }

    suspend fun markAllRead(category: Category) = withContext(Dispatchers.IO) {
        state.value.conversations[category].orEmpty().filter { it.unread > 0 }.forEach { markRead(it.senderKey) }
    }

    suspend fun setPinned(senderKey: String, pin: Boolean) = withContext(Dispatchers.IO) {
        store.setPinned(senderKey, pin)
        pinned = if (pin) pinned + senderKey else pinned - senderKey
        lock.withLock { rebuild() }
    }

    suspend fun moveTo(senderKey: String, category: Category) = withContext(Dispatchers.IO) {
        store.putOverride(senderKey, category)
        overrides = overrides + (senderKey to category)
        lock.withLock { rebuild() }
    }

    /** false when Chitthi isn't the default SMS app and so may not delete */
    suspend fun delete(toDelete: Collection<Message>): Boolean = withContext(Dispatchers.IO) {
        SmsStore.delete(app, toDelete)
    }

    suspend fun deleteConversation(senderKey: String, category: Category): Boolean =
        delete(messages.filter { senderKey(it) == senderKey && verdict(it).category == category })

    suspend fun deleteSender(senderKey: String): Boolean = delete(messages.filter { senderKey(it) == senderKey })

    fun hasUnread(senderKey: String): Boolean {
        val mark = readMarks[senderKey] ?: 0L
        return messages.any { it.incoming && !it.read && it.date > mark && senderKey(it) == senderKey }
    }

    fun isPinned(senderKey: String) = senderKey in pinned

    /**
     * Gemini Nano's second pass while the app is open: re-sorts what the rules guessed at and
     * names headers the directory doesn't know. Newest first, a few dozen per visit.
     */
    fun refineWithNano() {
        if (!prefs.settings.value.useNano) return
        scope.launch {
            if (!nano.isReady()) return@launch
            val conversations = state.value.conversations.values.flatten().sortedByDescending { it.latest.date }
            val unnamed = conversations.map { it.senderKey }.filter { it.startsWith("h:") || it.startsWith("r:") }.distinct()
                .filter { it.substring(2) !in brandGuesses }.take(NANO_PER_VISIT)
            unnamed.forEachIndexed { i, key ->
                val header = key.substring(2)
                val samples = messages.asSequence().filter { senderKey(it) == key }.take(SAMPLES).map { it.body }.toList()
                val brand = nano.nameSender(header, samples)
                store.putBrandGuess(header, brand)
                brandGuesses[header] = brand
                if (i % NANO_REBUILD_EVERY == NANO_REBUILD_EVERY - 1) lock.withLock { rebuild() }
            }
            val unsure = conversations.asSequence()
                .flatMap { c -> messages.asSequence().filter { senderKey(it) == c.senderKey }.take(UNSURE_PER_SENDER) }
                .filter { it.key !in nanoVerdicts && ruleVerdicts[it.key]?.unsure == true && senderKey(it) !in overrides }
                .take(NANO_PER_VISIT)
                .toList()
            unsure.forEachIndexed { i, m ->
                val category = nano.categorize(senders[senderKey(m)]?.title ?: m.address, m.body) ?: return@forEachIndexed
                store.putNanoVerdict(m.key, category)
                nanoVerdicts[m.key] = category
                if (i % NANO_REBUILD_EVERY == NANO_REBUILD_EVERY - 1) lock.withLock { rebuild() }
            }
            lock.withLock { rebuild() }
        }
    }

    companion object {
        private const val REFRESH_GAP_MS = 400L
        private const val PROGRESS_EVERY = 500
        private const val FRESH_OTP_MS = 10 * 60_000L
        private const val SAMPLES = 4
        private const val SEARCH_LIMIT = 200
        private const val NANO_PER_VISIT = 40
        private const val NANO_REBUILD_EVERY = 5
        private const val UNSURE_PER_SENDER = 3

        fun formatNumber(address: String): String =
            PhoneNumberUtils.formatNumber(address, "IN") ?: address
    }
}
