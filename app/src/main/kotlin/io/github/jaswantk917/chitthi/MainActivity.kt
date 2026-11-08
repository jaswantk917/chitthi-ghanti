package io.github.jaswantk917.chitthi

import io.github.jaswantk917.chitthi.send.Scheduler
import io.github.jaswantk917.chitthi.model.Scheduled
import androidx.compose.runtime.produceState
import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import io.github.jaswantk917.block.BlockedActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.chitthi.data.Inbox
import io.github.jaswantk917.chitthi.model.Category
import io.github.jaswantk917.chitthi.model.Conversation
import io.github.jaswantk917.chitthi.model.Message
import io.github.jaswantk917.chitthi.model.SenderInfo
import io.github.jaswantk917.chitthi.notify.Notifier
import io.github.jaswantk917.chitthi.send.SmsSender
import io.github.jaswantk917.chitthi.ui.inbox.ConversationActionsSheet
import io.github.jaswantk917.chitthi.ui.inbox.InboxActions
import io.github.jaswantk917.chitthi.ui.inbox.InboxScreen
import io.github.jaswantk917.chitthi.ui.search.NewChatScreen
import io.github.jaswantk917.chitthi.ui.search.SearchScreen
import io.github.jaswantk917.chitthi.ui.settings.SettingsSheet
import io.github.jaswantk917.chitthi.ui.theme.ChitthiTheme
import io.github.jaswantk917.chitthi.ui.thread.ThreadActions
import io.github.jaswantk917.chitthi.ui.thread.ThreadScreen
import io.github.jaswantk917.chitthi.ui.util.Haptic
import io.github.jaswantk917.chitthi.ui.util.LocalBlur
import io.github.jaswantk917.chitthi.ui.util.rememberHaptics
import io.github.jaswantk917.chitthi.ui.welcome.WelcomeScreen
import io.github.jaswantk917.chitthi.work.OtpSweepWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Route {
    data class Thread(val key: String, val fallback: SenderInfo? = null, val text: String = "") : Route
    data object Search : Route
    data class NewChat(val text: String = "") : Route
}

private const val PAGE_STIFFNESS = 520f
private const val PARALLAX = 0.25f
private const val SCRIM = 0.32f

class MainActivity : ComponentActivity() {
    private val app get() = application as ChitthiApp
    private val stack = mutableStateListOf<Route>()
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            val settings by app.prefs.settings.collectAsState()
            ChitthiTheme(settings.palette, settings.amoled) { CompositionLocalProvider(LocalBlur provides settings.blur) { Root() } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        app.inbox.start()
        app.inbox.refresh(contactsToo = true)
        app.inbox.refineWithNano()
        syncVisible()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        app.inbox.visibleSender = null
    }

    private fun syncVisible() {
        app.inbox.visibleSender = if (resumed) (stack.lastOrNull() as? Route.Thread)?.key else null
    }

    private fun push(route: Route) {
        stack += route
        syncVisible()
    }

    private fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        syncVisible()
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Notifier.EXTRA_SENDER)?.let {
            stack.clear()
            push(Route.Thread(it))
            return
        }
        val text = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val data = intent.data
        when {
            data != null && data.scheme in setOf("sms", "smsto", "mms", "mmsto") -> {
                val number = data.schemeSpecificPart.removePrefix("//").split(',', ';').firstOrNull { it.isNotBlank() }
                stack.clear()
                if (number == null) push(Route.NewChat(text))
                else app.inbox.senderForNumber(Uri.decode(number)).let { push(Route.Thread(it.key, it, text)) }
            }
            intent.action == Intent.ACTION_SEND && text.isNotEmpty() -> {
                stack.clear()
                push(Route.NewChat(text))
            }
        }
    }

    /** a saved person opens in Contacts; a stranger's number opens "new contact, or add to someone" */
    private fun showContact(sender: SenderInfo) {
        if (!sender.key.startsWith("p:")) return
        val number = sender.replyAddresses.firstOrNull() ?: return
        val saved = if (sender.isContact) {
            runCatching {
                contentResolver.query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                    arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY), null, null, null,
                )?.use { c -> if (c.moveToFirst()) ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1)) else null }
            }.getOrNull()
        } else null
        startActivity(
            if (saved != null) Intent(Intent.ACTION_VIEW, saved)
            else Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE).putExtra(ContactsContract.Intents.Insert.PHONE, number),
        )
    }

    /** Play allows SMS access only to the default SMS app, so being it comes before everything else */
    private fun isSmsApp() = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)

    private fun isReady() = isSmsApp() && checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    @Composable
    private fun Root() {
        val haptics = rememberHaptics()
        var ready by remember { mutableStateOf(isReady()) }
        var declined by remember { mutableStateOf(false) }
        fun started() {
            app.inbox.start()
            app.inbox.refresh(contactsToo = true)
        }
        // the SMS role grants the SMS permissions itself; this asks for the rest
        val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            ready = isReady()
            if (ready) started()
        }
        val rest = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        val becomeSmsApp = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isSmsApp()) {
                haptics.play(Haptic.Success)
                declined = false
                permissions.launch(rest)
            } else {
                declined = true
            }
        }
        // switched away in Settings, or back: follow it either way
        LifecycleResumeEffect(Unit) {
            val now = isReady()
            if (now != ready) {
                ready = now
                if (now) started()
            }
            onPauseOrDispose {}
        }
        val askForRole = {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_SMS) && !roles.isRoleHeld(RoleManager.ROLE_SMS)) {
                becomeSmsApp.launch(roles.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
        }
        if (!ready) {
            WelcomeScreen(
                declined = declined,
                onStart = { if (isSmsApp()) permissions.launch(rest) else askForRole() },
            )
            return
        }
        Screens(askForRole)
    }

    @Composable
    private fun Screens(askForRole: () -> Unit) {
        val inbox = app.inbox
        val state by inbox.state.collectAsState()
        val settings by app.prefs.settings.collectAsState()
        val nanoStatus by inbox.nano.status.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var showSettings by remember { mutableStateOf(false) }

        val inboxActions = remember {
            object : InboxActions {
                override fun open(conversation: Conversation) = push(Route.Thread(conversation.senderKey, conversation.sender))
                override fun contact(conversation: Conversation) = showContact(conversation.sender)
                override fun newChat() = push(Route.NewChat())
                override fun search() = push(Route.Search)
                override fun settings() { showSettings = true }
                override fun makeDefault() = askForRole()
                override fun markRead(conversation: Conversation) {
                    scope.launch { inbox.markRead(conversation.senderKey); Notifier.cancel(context, conversation.senderKey) }
                }
                override fun markAllRead(category: Category) { scope.launch { inbox.markAllRead(category) } }
                override fun setPinned(conversation: Conversation, pinned: Boolean) { scope.launch { inbox.setPinned(conversation.senderKey, pinned) } }
                override fun moveTo(conversation: Conversation, category: Category) {
                    scope.launch {
                        inbox.moveTo(conversation.senderKey, category)
                        snackbar.showSnackbar("${conversation.sender.title} now goes to ${category.label}")
                    }
                }
                override suspend fun delete(conversation: Conversation) = inbox.deleteConversation(conversation.senderKey, conversation.category)
            }
        }

        // the inbox stays composed underneath, so its tabs and scroll survive a trip into a chat
        val covered = stack.isNotEmpty()
        val depth by animateFloatAsState(if (covered) 1f else 0f, spring(dampingRatio = 1f, stiffness = PAGE_STIFFNESS), label = "depth")
        var backProgress by remember { mutableFloatStateOf(0f) }
        var leaving by remember { mutableStateOf<Route?>(null) }

        PredictiveBackHandler(enabled = stack.isNotEmpty()) { events ->
            val top = stack.lastOrNull()
            try {
                leaving = top
                events.collect { backProgress = it.progress }
                pop()
            } catch (e: CancellationException) {
                backProgress = 0f
                leaving = null
                throw e
            }
        }
        LaunchedEffect(leaving, stack.size) {
            if (leaving != null && leaving !in stack) {
                delay(450)
                backProgress = 0f
                leaving = null
            }
        }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = -size.width * PARALLAX * depth * (1f - backProgress) }
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = SCRIM * depth * (1f - backProgress)))
                    },
            ) {
                val scheduleTick by Scheduler.changes.collectAsState()
                val scheduledKeys by produceState(emptySet<String>(), scheduleTick) {
                    value = withContext(Dispatchers.IO) { Scheduler.pending().mapTo(HashSet()) { it.senderKey } }
                }
                InboxScreen(state, inboxActions, snackbar, scheduledKeys)
            }

            var popping by remember { mutableStateOf(false) }
            var lastSize by remember { mutableStateOf(stack.size) }
            popping = stack.size < lastSize
            lastSize = stack.size

            AnimatedContent(
                targetState = stack.lastOrNull(),
                transitionSpec = {
                    // the Pixel's own page motion: a full-width slide over a parallaxed, dimmed page
                    val slide = spring<IntOffset>(dampingRatio = 1f, stiffness = PAGE_STIFFNESS)
                    if (!popping) {
                        slideInHorizontally(slide) { it } togetherWith slideOutHorizontally(slide) { -(it * PARALLAX).toInt() }
                    } else {
                        slideInHorizontally(slide) { -(it * PARALLAX).toInt() } togetherWith slideOutHorizontally(slide) { it }
                    }.apply { targetContentZIndex = if (popping) -1f else 1f } using null
                },
                // the empty state is 0×0; a wrapping container would grow a clip out of the corner
                modifier = Modifier.fillMaxSize(),
                label = "routes",
            ) { route ->
                if (route == null) return@AnimatedContent
                val p = if (route == leaving) backProgress else 0f
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1f - 0.1f * p
                            scaleY = 1f - 0.1f * p
                            translationX = p * 32.dp.toPx()
                        }
                        .clip(RoundedCornerShape(36.dp * p))
                        .background(MaterialTheme.colorScheme.surface)
                        // a hit target of its own, so taps never fall through to the inbox below
                        .pointerInput(Unit) {},
                ) {
                    when (route) {
                        is Route.Thread -> ThreadRoute(route, state.version, inbox)
                        Route.Search -> SearchScreen(
                            search = inbox::search,
                            onOpen = { sender -> push(Route.Thread(sender.key, sender)) },
                            onBack = ::pop,
                        )
                        is Route.NewChat -> NewChatScreen(
                            contacts = remember(state.version) { inbox.contactList() },
                            senderFor = inbox::senderForNumber,
                            onPick = { sender ->
                                pop()
                                push(Route.Thread(sender.key, sender, route.text))
                            },
                            onBack = ::pop,
                        )
                    }
                }
            }
        }

        if (showSettings) {
            SettingsSheet(
                settings = settings,
                isDefault = state.isDefault,
                nanoStatus = nanoStatus,
                onChange = { change ->
                    val before = app.prefs.settings.value
                    app.prefs.update(change)
                    val after = app.prefs.settings.value
                    if (before.otpDeleteAfterMinutes != after.otpDeleteAfterMinutes) {
                        OtpSweepWorker.schedule(context, after.otpDeleteAfterMinutes)
                        if (after.otpDeleteAfterMinutes > 0) {
                            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<OtpSweepWorker>().build())
                        }
                    }
                    if (!before.useNano && after.useNano) inbox.refineWithNano()
                },
                onMakeDefault = askForRole,
                countExpired = { minutes -> withContext(Dispatchers.IO) { OtpSweepWorker.expired(context, minutes).size } },
                onOpenBlocked = { startActivity(Intent(this@MainActivity, BlockedActivity::class.java)) },
                onDismiss = { showSettings = false },
            )
        }
    }

    @Composable
    private fun ThreadRoute(route: Route.Thread, version: Int, inbox: Inbox) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var sheet by remember { mutableStateOf(false) }
        val sender = remember(version, route.key) {
            inbox.sender(route.key) ?: route.fallback
                ?: route.key.takeIf { it.startsWith("p:") || it.startsWith("c:") }?.let { inbox.senderForNumber(it.substring(2)) }
        }
        val messages = remember(version, route.key) { inbox.messagesOf(route.key) }
        LaunchedEffect(version, route.key) {
            Notifier.cancel(context, route.key)
            if (inbox.hasUnread(route.key)) inbox.markRead(route.key)
        }
        if (sender == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (inbox.state.collectAsState().value.loaded) {
                    Text("This conversation is gone", style = MaterialTheme.typography.titleMedium)
                } else {
                    LoadingIndicator()
                }
            }
            return
        }
        val scheduleTick by Scheduler.changes.collectAsState()
        val scheduled by produceState(emptyList<Scheduled>(), scheduleTick, sender.key) {
            value = withContext(Dispatchers.IO) { Scheduler.pending(sender.key) }
        }
        val actions = remember(sender) {
            object : ThreadActions {
                override fun back() = pop()
                override fun send(text: String) {
                    scope.launch(Dispatchers.IO) { SmsSender.send(context, sender.replyAddresses, text) }
                }
                override fun retry(message: Message) {
                    scope.launch(Dispatchers.IO) {
                        inbox.delete(listOf(message))
                        SmsSender.send(context, listOf(message.address), message.body)
                    }
                }
                override suspend fun delete(message: Message) = inbox.delete(listOf(message))
                override fun call() {
                    sender.replyAddresses.firstOrNull()?.let { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) }
                }
                override fun more() { sheet = true }
                override fun contact() = showContact(sender)
                override suspend fun smartReplies(chat: List<Pair<Boolean, String>>): List<String> {
                    val personal = sender.key.startsWith("p:") || sender.key.startsWith("t:")
                    if (!personal || !app.prefs.settings.value.useNano || !inbox.nano.isReady()) return emptyList()
                    return inbox.nano.smartReplies(chat)
                }
                override fun schedule(text: String, at: Long) {
                    scope.launch(Dispatchers.IO) { Scheduler.schedule(context, sender.key, sender.replyAddresses, text, at) }
                }
                override fun sendNow(item: Scheduled) {
                    scope.launch(Dispatchers.IO) { Scheduler.sendNow(context, item) }
                }
                override fun reschedule(item: Scheduled, at: Long) {
                    scope.launch(Dispatchers.IO) { Scheduler.reschedule(context, item, at) }
                }
                override fun cancel(item: Scheduled) {
                    scope.launch(Dispatchers.IO) { Scheduler.cancel(context, item) }
                }
            }
        }
        ThreadScreen(sender, messages, scheduled, inbox::verdict, route.text, actions)
        if (sheet) {
            val latest = messages.lastOrNull()
            ConversationActionsSheet(
                title = sender.title,
                pinned = inbox.isPinned(sender.key),
                current = latest?.let { inbox.verdict(it).category } ?: Category.Personal,
                hasUnread = false,
                onDismiss = { sheet = false },
                onPin = { scope.launch { inbox.setPinned(sender.key, !inbox.isPinned(sender.key)) } },
                onMarkRead = {},
                onMove = { scope.launch { inbox.moveTo(sender.key, it) } },
                onDelete = { scope.launch { if (inbox.deleteSender(sender.key)) pop() } },
            )
        }
    }
}
