package io.github.jaswantk917.chitthi.ui.inbox

import io.github.jaswantk917.chitthi.ui.components.pressMorph
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.chitthi.data.FreshOtp
import io.github.jaswantk917.chitthi.data.InboxState
import io.github.jaswantk917.chitthi.model.Category
import io.github.jaswantk917.chitthi.model.Conversation
import io.github.jaswantk917.chitthi.notify.Clipboard
import io.github.jaswantk917.chitthi.ui.components.Avatar
import io.github.jaswantk917.chitthi.ui.components.TagRow
import io.github.jaswantk917.chitthi.ui.util.onLongPress
import io.github.jaswantk917.chitthi.ui.util.Haptic
import io.github.jaswantk917.chitthi.ui.util.edgeBlur
import io.github.jaswantk917.chitthi.ui.util.pressScale
import io.github.jaswantk917.chitthi.ui.util.rememberHaptics
import io.github.jaswantk917.chitthi.ui.util.shortTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jaswantk917.block.Applies
import io.github.jaswantk917.block.RuleKind
import io.github.jaswantk917.block.SystemBlocks
import io.github.jaswantk917.chitthi.ChitthiApp

interface InboxActions {
    fun open(conversation: Conversation)
    /** a person's face: their contact, or saving the number as one */
    fun contact(conversation: Conversation)
    fun newChat()
    fun search()
    fun settings()
    fun makeDefault()
    fun markRead(conversation: Conversation)
    fun markAllRead(category: Category)
    fun setPinned(conversation: Conversation, pinned: Boolean)
    fun moveTo(conversation: Conversation, category: Category)
    /** false when not allowed (not the default SMS app) */
    suspend fun delete(conversation: Conversation): Boolean
}

private val categories = Category.entries

@Composable
fun InboxScreen(
    state: InboxState,
    actions: InboxActions,
    snackbar: SnackbarHostState,
    /** chats with a message waiting to be sent */
    scheduledKeys: Set<String> = emptySet(),
) {
    val pager = rememberPagerState { categories.size }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val listStates = categories.map { rememberLazyListState() }
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(160.dp) }
    // the title scrolls away and comes back on any scroll up; the chips stay
    var titleHeight by remember { mutableFloatStateOf(0f) }
    var collapse by remember { mutableFloatStateOf(0f) }
    // the FAB follows the way you've kept scrolling, not every twitch: catching a fling starts a
    // drag with no slop, so a finger's wobble scrolls back a pixel or two
    var fabOpen by remember { mutableStateOf(true) }
    val fabSlop = with(density) { 48.dp.toPx() }
    val collapser = remember {
        object : NestedScrollConnection {
            private var run = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = consumed.y
                if (dy != 0f) {
                    if (dy > 0f != run > 0f) run = 0f
                    run += dy
                    if (run > fabSlop) fabOpen = true else if (run < -fabSlop) fabOpen = false
                }
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                collapse = (collapse - available.y).coerceIn(0f, titleHeight)
                return Offset.Zero
            }
        }
    }
    var menuFor by remember { mutableStateOf<Conversation?>(null) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().drop(1).collect { haptics.play(Haptic.Segment) }
    }
    // a collapsed header over a list sitting at its top leaves a gap the title's height:
    // pages off screen scroll with the header, and a page too short to do that opens the header on arrival
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage to collapse }.collect { (current, c) ->
            listStates.forEachIndexed { i, list ->
                if (i != current && list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < c) {
                    list.scrollToItem(0, c.toInt())
                }
            }
        }
    }
    val reveal = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            fabOpen = true
            val list = listStates[page]
            if (list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < collapse) {
                animate(collapse, list.firstVisibleItemScrollOffset.toFloat(), animationSpec = reveal) { v, _ -> collapse = v }
            }
        }
    }

    val currentList = listStates[pager.currentPage]
    val expandedFab by remember(currentList) {
        derivedStateOf { fabOpen || currentList.firstVisibleItemIndex == 0 }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).nestedScroll(collapser)) {
        if (!state.loaded) {
            Sorting(state.sorting)
        } else {
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize().edgeBlur(top = { headerHeight - with(density) { collapse.toDp() } }, bottom = navBottom + 24.dp),
            ) { page ->
                val category = categories[page]
                CategoryPage(
                    category = category,
                    conversations = state.conversations[category].orEmpty(),
                    freshOtp = state.freshOtp.takeIf { category == Category.Important },
                    showDefaultBanner = !state.isDefault && page == 0,
                    listState = listStates[page],
                    contentPadding = PaddingValues(top = headerHeight + 4.dp, bottom = navBottom + 112.dp),
                    scheduledKeys = scheduledKeys,
                    actions = actions,
                    onLongPress = { menuFor = it },
                )
            }
        }

        Header(
            selected = pager.currentPage,
            unread = categories.map { c -> state.conversations[c].orEmpty().sumOf { it.unread } },
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            onMarkAllRead = actions::markAllRead,
            actions = actions,
            collapse = { collapse },
            onTitleHeight = { titleHeight = it.toFloat() },
            modifier = Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
        )
        // the list blurs right up under the clock; a soft fade keeps the clock readable
        val surface = MaterialTheme.colorScheme.surface
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Brush.verticalGradient(0f to surface.copy(alpha = 0.75f), 1f to surface.copy(alpha = 0f))),
        )

        ExtendedFloatingActionButton(
            onClick = { haptics.play(Haptic.Tap); actions.newChat() },
            expanded = expandedFab,
            icon = { Icon(Icons.Rounded.Edit, null) },
            text = { Text("New chat") },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(20.dp),
        )

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 88.dp),
        )
    }

    menuFor?.let { conversation ->
        ConversationSheet(
            conversation = conversation,
            onDismiss = { menuFor = null },
            actions = actions,
            snackbar = snackbar,
        )
    }
}

@Composable
private fun Header(
    selected: Int,
    unread: List<Int>,
    onSelect: (Int) -> Unit,
    onMarkAllRead: (Category) -> Unit,
    actions: InboxActions,
    collapse: () -> Float,
    onTitleHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val surface = MaterialTheme.colorScheme.surface
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = -collapse() }
            .background(Brush.verticalGradient(0f to surface.copy(alpha = 0.8f), 0.65f to surface.copy(alpha = 0.6f), 1f to surface.copy(alpha = 0f)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = 10.dp),
    ) {
        Row(
            Modifier
                .onSizeChanged { onTitleHeight(it.height) }
                .graphicsLayer {
                    val p = if (size.height > 0f) collapse() / size.height else 0f
                    alpha = 1f - p
                    scaleX = 1f - 0.08f * p
                    scaleY = 1f - 0.08f * p
                    transformOrigin = TransformOrigin(0f, 1f)
                }
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Chitthi", style = MaterialTheme.typography.displaySmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
                val total = unread.sum()
                AnimatedContent(total, label = "unread") { count ->
                    Text(
                        if (count == 0) "All caught up" else "$count unread",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val press1 = remember { MutableInteractionSource() }
            FilledTonalIconButton(
                onClick = { haptics.play(Haptic.Tap); actions.search() },
                shape = pressMorph(MaterialShapes.Cookie6Sided, MaterialShapes.Cookie4Sided, press1),
                interactionSource = press1,
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Rounded.Search, "Search") }
            Spacer(Modifier.width(6.dp))
            val press2 = remember { MutableInteractionSource() }
            FilledTonalIconButton(
                onClick = { haptics.play(Haptic.Tap); actions.settings() },
                shape = pressMorph(MaterialShapes.Clover4Leaf, MaterialShapes.Cookie4Sided, press2),
                interactionSource = press2,
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Rounded.Settings, "Settings") }
        }
        val chips = rememberLazyListState()
        // follow the pager, keeping one neighbour of context to the left
        LaunchedEffect(selected) { chips.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
        LazyRow(
            state = chips,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(categories) { i, category ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    ToggleButton(
                        checked = i == selected,
                        onCheckedChange = { onSelect(i) },
                        modifier = Modifier.onLongPress {
                            if (unread[i] > 0) { haptics.play(Haptic.Hold); menu = true }
                        },
                    ) {
                        Text(category.label, style = MaterialTheme.typography.labelLargeEmphasized)
                        AnimatedVisibility(
                            unread[i] > 0,
                            enter = expandHorizontally(spring(dampingRatio = 0.6f)) + scaleIn(),
                            exit = shrinkHorizontally() + scaleOut(),
                        ) {
                            Text(
                                unread[i].coerceAtMost(999).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (i == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .clip(CircleShape)
                                    .background(if (i == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
                        DropdownMenuItem(
                            text = { Text("Mark ${unread[i]} read") },
                            leadingIcon = { Icon(Icons.Rounded.DoneAll, null) },
                            onClick = {
                                haptics.play(Haptic.Success)
                                menu = false
                                onMarkAllRead(category)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPage(
    category: Category,
    conversations: List<Conversation>,
    freshOtp: FreshOtp?,
    showDefaultBanner: Boolean,
    listState: LazyListState,
    contentPadding: PaddingValues,
    scheduledKeys: Set<String>,
    actions: InboxActions,
    onLongPress: (Conversation) -> Unit,
) {
    LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        if (freshOtp != null) {
            item(key = "otp-${freshOtp.message.key}") {
                OtpHero(freshOtp, Modifier.animateItem().padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }
        if (showDefaultBanner) {
            item(key = "default-banner") {
                DefaultBanner(actions::makeDefault, Modifier.animateItem().padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }
        if (conversations.isEmpty()) {
            item(key = "empty") { Empty(category, Modifier.animateItem()) }
        }
        items(conversations, key = { it.senderKey + "|" + it.category }) { conversation ->
            val haptics = rememberHaptics()
            ConversationRow(
                conversation,
                scheduled = conversation.senderKey in scheduledKeys,
                onClick = { actions.open(conversation) },
                onLongPress = { haptics.play(Haptic.Hold); onLongPress(conversation) },
                onFace = { haptics.play(Haptic.Tap); actions.contact(conversation) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    scheduled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onFace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val unread = conversation.unread > 0
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .pressScale(interaction)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val face = remember { MutableInteractionSource() }
        val person = conversation.sender.key.startsWith("p:")
        Box(
            Modifier
                .pressScale(face)
                .clip(CircleShape)
                .combinedClickable(interactionSource = face, indication = null, enabled = person, onClick = onFace),
        ) {
            Avatar(conversation.sender, size = 52.dp)
            if (conversation.pinned) {
                Icon(
                    Icons.Rounded.PushPin, "Pinned",
                    tint = scheme.onTertiary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(scheme.tertiary).padding(3.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.sender.title,
                    style = if (unread) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                AnimatedVisibility(scheduled, enter = scaleIn(spring(dampingRatio = 0.45f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Icon(
                        Icons.Rounded.Schedule, "Scheduled message",
                        tint = scheme.primary,
                        modifier = Modifier.padding(end = 4.dp).size(14.dp),
                    )
                }
                Text(
                    shortTime(conversation.latest.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread) scheme.primary else scheme.onSurfaceVariant,
                    fontWeight = if (unread) FontWeight.Bold else null,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = when {
                    conversation.latest.body.isNotBlank() -> conversation.latest.body
                    conversation.latest.hasImage -> "Picture"
                    else -> "Message"
                }
                Text(
                    (if (!conversation.latest.incoming) "You: " else "") + preview.replace('\n', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unread) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = if (unread) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(unread, enter = scaleIn(spring(dampingRatio = 0.45f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Text(
                        conversation.unread.coerceAtMost(99).toString(),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = scheme.onPrimary,
                        modifier = Modifier.padding(start = 10.dp).clip(CircleShape).background(scheme.primary).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (conversation.latestVerdict.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                TagRow(conversation.latestVerdict.tags)
            }
        }
    }
}

@Composable
private fun OtpHero(otp: FreshOtp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var copied by remember(otp.code) { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    if (now - otp.message.date > 10 * 60_000) return
    val scheme = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(if (shown) 1f else 0.85f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "hero")
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = scale.coerceIn(0f, 1f) }
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.tertiaryContainer)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(otp.sender, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "Code from ${otp.sender.title} · ${shortTime(otp.message.date, now)}",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onTertiaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                otp.code.chunked(if (otp.code.length > 4) 3 else 2).joinToString(" "),
                style = MaterialTheme.typography.displayMediumEmphasized,
                color = scheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            val width by animateDpAsState(if (copied) 132.dp else 112.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "copyWidth")
            Button(
                onClick = {
                    Clipboard.copyOtp(context, otp.code)
                    haptics.play(Haptic.Copy)
                    copied = true
                },
                modifier = Modifier.width(width).height(52.dp),
            ) {
                AnimatedContent(copied, transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() }, label = "copy") { done ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (done) Icons.Rounded.Check else Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (done) "Copied" else "Copy")
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultBanner(onMakeDefault: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(scheme.secondaryContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Sms, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Reading only", style = MaterialTheme.typography.titleSmallEmphasized, color = scheme.onSecondaryContainer)
            Text(
                "Make Chitthi your SMS app to get notifications, delete, and clear old OTPs.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onMakeDefault) { Text("Switch") }
    }
}

@Composable
private fun Empty(category: Category, modifier: Modifier = Modifier) {
    val (shape, line) = when (category) {
        Category.Important -> MaterialShapes.Sunny to "Nothing important. Enjoy the quiet."
        Category.Personal -> MaterialShapes.Heart to "No chats yet."
        Category.Promotions -> MaterialShapes.Flower to "No promos. Bliss."
        Category.Unknown -> MaterialShapes.Ghostish to "No strangers here."
    }
    var spin by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2400); spin += 45 } }
    val rotation by animateFloatAsState(spin.toFloat(), spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessVeryLow), label = "spin")
    Column(modifier.fillMaxWidth().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(120.dp).graphicsLayer { rotationZ = rotation }.clip(shape.toShape()).background(MaterialTheme.colorScheme.secondaryContainer),
        )
        Spacer(Modifier.height(20.dp))
        Text(line, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Sorting(count: Int) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text("Sorting your messages", style = MaterialTheme.typography.titleMediumEmphasized)
        AnimatedContent(count, label = "count") {
            Text(if (it == 0) "on this phone, nothing leaves it" else "%,d so far".format(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConversationSheet(
    conversation: Conversation,
    onDismiss: () -> Unit,
    actions: InboxActions,
    snackbar: SnackbarHostState,
) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    // outlives the sheet, which leaves composition as it closes
    val scope = rememberCoroutineScope()
    ConversationActionsSheet(
        title = conversation.sender.title,
        pinned = conversation.pinned,
        current = conversation.category,
        hasUnread = conversation.unread > 0,
        onDismiss = onDismiss,
        onPin = { actions.setPinned(conversation, !conversation.pinned) },
        onMarkRead = { actions.markRead(conversation) },
        onMove = { actions.moveTo(conversation, it) },
        onDelete = {
            scope.launch {
                if (actions.delete(conversation)) {
                    haptics.play(Haptic.Delete)
                } else {
                    haptics.play(Haptic.Error)
                    snackbar.showSnackbar("Make Chitthi your SMS app to delete")
                }
            }
        },
        blockLabel = when (conversation.sender.key.take(2)) {
            "p:", "c:" -> "Block this number"
            "h:" -> "Block this sender"
            else -> null
        },
        onBlock = {
            scope.launch {
                val sender = conversation.sender
                val header = sender.key.removePrefix("h:")
                val number = sender.replyAddresses.firstOrNull()
                val done = if (sender.key.startsWith("h:")) {
                    // every carrier prefix and route of this company, e.g. AX-AMAZON-S and JD-AMAZON-P
                    ChitthiApp.instance.blocks.add(RuleKind.SenderId, "*-$header*", Applies.Sms)
                    true
                } else {
                    number != null && withContext(Dispatchers.IO) { SystemBlocks.block(context, number) }
                }
                if (done) {
                    haptics.play(Haptic.ToggleOn)
                    snackbar.showSnackbar("Blocked. Nothing new from them will reach you")
                } else {
                    haptics.play(Haptic.Error)
                    snackbar.showSnackbar("Make Chitthi your SMS app to block numbers")
                }
            }
        },
    )
}
