package io.github.jaswantk917.ghanti.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import io.github.jaswantk917.ghanti.data.Contact
import io.github.jaswantk917.ghanti.data.HomeState
import io.github.jaswantk917.ghanti.data.Kind
import io.github.jaswantk917.ghanti.data.RecentRow
import io.github.jaswantk917.ghanti.signals.DeliveryRules
import io.github.jaswantk917.ghanti.signals.Signal
import io.github.jaswantk917.ghanti.signals.Strength
import io.github.jaswantk917.ghanti.ui.components.Avatar
import io.github.jaswantk917.ghanti.ui.components.AvatarHint
import io.github.jaswantk917.ghanti.ui.components.MemoSheet
import io.github.jaswantk917.ghanti.ui.components.pressMorph
import io.github.jaswantk917.ghanti.ui.util.Haptic
import io.github.jaswantk917.ghanti.ui.util.clockTime
import io.github.jaswantk917.ghanti.ui.util.dayLabel
import io.github.jaswantk917.ghanti.ui.util.edgeBlur
import io.github.jaswantk917.ghanti.ui.util.pressScale
import io.github.jaswantk917.ghanti.ui.util.rememberHaptics
import io.github.jaswantk917.ghanti.ui.util.sameDay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

interface HomeActions {
    fun call(number: String)
    fun message(number: String)
    fun copy(number: String)
    fun addContact(number: String)
    fun openContact(id: Long)
    /** null: ask which number again */
    fun setUsual(contactId: Long, number: String?)
    fun saveMemo(number: String, at: Long, label: String?, note: String)
    fun delete(row: RecentRow)
    fun keypad()
    fun settings()
    fun expecting()
    fun makeDefault()
    fun allowNotifications()
    fun recentsSeen()
}

/** [screensCalls]: Android asks the "Caller ID & spam app" whether a call rings, a separate role from the phone app */
data class Setup(val isPhoneApp: Boolean, val screensCalls: Boolean, val readsNotifications: Boolean, val silencing: Boolean) {
    val ready get() = isPhoneApp && screensCalls
}

private val tabs = listOf("Recents", "Contacts")

@Composable
fun HomeScreen(state: HomeState, setup: Setup, expecting: List<Signal>, usual: Map<Long, String>, actions: HomeActions) {
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(150.dp) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val recentsList = rememberLazyListState()
    val contactsList = rememberLazyListState()
    val lists = listOf(recentsList, contactsList)
    val current = lists[pager.currentPage]
    // the title scrolls away and comes back on any scroll up; the tabs stay
    var titleHeight by remember { mutableFloatStateOf(0f) }
    var collapse by remember { mutableFloatStateOf(0f) }
    // contacts search hides the same way, unless something's typed in it
    var query by rememberSaveable { mutableStateOf("") }
    var searchHeight by remember { mutableFloatStateOf(0f) }
    var searchHide by remember { mutableFloatStateOf(0f) }
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
                if (query.isEmpty()) searchHide = (searchHide - available.y).coerceIn(0f, searchHeight)
                return Offset.Zero
            }
        }
    }
    // a collapsed header over a list sitting at its top leaves a gap the title's height:
    // the page off screen scrolls with the header, and one too short to do that opens the header on arrival
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage to collapse }.collect { (page, c) ->
            lists.forEachIndexed { i, list ->
                if (i != page && list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < c) list.scrollToItem(0, c.toInt())
            }
        }
    }
    val reveal = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            fabOpen = true
            val list = lists[page]
            if (list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < collapse) {
                animate(collapse, list.firstVisibleItemScrollOffset.toFloat(), animationSpec = reveal) { v, _ -> collapse = v }
            }
            if (page == 1 && searchHide > 0f) animate(searchHide, 0f, animationSpec = reveal) { v, _ -> searchHide = v }
        }
    }
    val expandedFab by remember(current) { derivedStateOf { fabOpen || current.firstVisibleItemIndex == 0 } }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().drop(1).collect { haptics.play(Haptic.Segment) }
    }
    LaunchedEffect(pager.currentPage, state.recents.size) { if (pager.currentPage == 0) actions.recentsSeen() }
    // back from Contacts clears a search, then returns to Recents; only Recents leaves the app
    BackHandler(enabled = pager.currentPage == 1) {
        if (query.isNotEmpty()) query = "" else scope.launch { pager.animateScrollToPage(0) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).nestedScroll(collapser)) {
        if (!state.loaded) {
            LoadingIndicator(Modifier.align(Alignment.Center))
        } else {
            HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                // on Contacts the blur reaches down past the search bar too
                modifier = Modifier.fillMaxSize().edgeBlur(
                    top = {
                        val onContacts = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                        headerHeight + with(density) { ((searchHeight - searchHide) * onContacts - collapse).toDp() }
                    },
                    bottom = navBottom + 24.dp,
                ),
            ) { page ->
                val padding = PaddingValues(top = headerHeight + 4.dp, bottom = navBottom + 112.dp)
                if (page == 0) {
                    RecentsPage(state.recents, setup, recentsList, padding, actions)
                } else {
                    val searchDp = with(density) { searchHeight.toDp() }
                    ContactsPage(state.contacts, query, usual, contactsList, PaddingValues(top = headerHeight + searchDp - 8.dp, bottom = navBottom + 112.dp), actions)
                }
            }
            // slides in with the Contacts page and tucks under the header as you scroll down
            val backdrop = MaterialTheme.colorScheme.surface
            ContactSearch(
                query, { query = it }, backdrop,
                Modifier
                    .onSizeChanged { searchHeight = it.height.toFloat() }
                    .graphicsLayer {
                        val onContacts = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                        translationX = (1f - onContacts) * size.width
                        translationY = headerHeight.toPx() - collapse - searchHide
                        // cut at the header's edge so it slides under the tabs instead of showing through them
                        clip = true
                        shape = GenericShape { box, _ -> addRect(Rect(0f, searchHide, box.width, box.height)) }
                    },
            )
        }

        Header(
            selected = pager.currentPage,
            setup = setup,
            expecting = expecting,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            actions = actions,
            collapse = { collapse },
            joinBelow = {
                val onContacts = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                onContacts * if (searchHeight > 0f) 1f - searchHide / searchHeight else 0f
            },
            onTitleHeight = { titleHeight = it.toFloat() },
            modifier = Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
        )
        val surface = MaterialTheme.colorScheme.surface
        Box(
            Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0.75f), surface.copy(alpha = 0f)))),
        )

        ExtendedFloatingActionButton(
            onClick = { haptics.play(Haptic.Tap); actions.keypad() },
            expanded = expandedFab,
            icon = { Icon(Icons.Rounded.Dialpad, null) },
            text = { Text("Keypad") },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        )
    }
}

@Composable
private fun Header(
    selected: Int,
    setup: Setup,
    expecting: List<Signal>,
    onSelect: (Int) -> Unit,
    actions: HomeActions,
    collapse: () -> Float,
    /** 1 while the contacts search sits below: the fade carries on into it instead of clearing */
    joinBelow: () -> Float,
    onTitleHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val surface = MaterialTheme.colorScheme.surface
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = -collapse() }
            .drawBehind {
                drawRect(Brush.verticalGradient(0f to surface.copy(alpha = 0.85f), 0.65f to surface.copy(alpha = 0.65f), 1f to surface.copy(alpha = 0.65f * joinBelow())))
            }
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
                Text("Ghanti", style = MaterialTheme.typography.displaySmallEmphasized)
                StatusLine(setup, expecting, onClick = {
                    haptics.play(Haptic.Tap)
                    if (setup.ready) actions.expecting() else actions.makeDefault()
                })
            }
            val press = remember { MutableInteractionSource() }
            FilledTonalIconButton(
                onClick = { haptics.play(Haptic.Tap); actions.settings() },
                shape = pressMorph(MaterialShapes.Clover4Leaf, MaterialShapes.Cookie4Sided, press),
                interactionSource = press,
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Rounded.Settings, "Settings") }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, label ->
                ToggleButton(checked = i == selected, onCheckedChange = { onSelect(i) }) {
                    Text(label, style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
    }
}

/** one line that says what Ghanti will do with the next stranger's call; tapping it explains why */
@Composable
private fun StatusLine(setup: Setup, expecting: List<Signal>, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    fun names(list: List<Signal>) = list.sortedByDescending { it.at }.map { if (it.brand == DeliveryRules.DELIVERY) "A delivery" else it.brand }.distinct().joinToString(", ")
    val live = expecting.filter { it.strength == Strength.Live }
    val (icon, text, tint) = when {
        !setup.isPhoneApp -> Triple(Icons.Rounded.Call, "Tap to make Ghanti your phone app", scheme.primary)
        !setup.screensCalls -> Triple(Icons.Rounded.NotificationsOff, "Tap to let Ghanti silence strangers", scheme.primary)
        !setup.silencing -> Triple(Icons.Rounded.NotificationsActive, "Everyone rings", scheme.onSurfaceVariant)
        live.isNotEmpty() -> Triple(Icons.Rounded.DeliveryDining, "${names(live)} on the way · strangers ring", scheme.tertiary)
        expecting.isNotEmpty() -> Triple(Icons.Rounded.DeliveryDining, "${names(expecting)} due today · strangers ring", scheme.tertiary)
        else -> Triple(Icons.Rounded.NotificationsOff, "Strangers ring silently", scheme.onSurfaceVariant)
    }
    AnimatedContent(text, label = "status") { shown ->
        Row(
            Modifier.clip(CircleShape).combinedClickable(onClick = onClick).padding(vertical = 2.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(shown, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecentsPage(
    recents: List<RecentRow>,
    setup: Setup,
    list: androidx.compose.foundation.lazy.LazyListState,
    padding: PaddingValues,
    actions: HomeActions,
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var memoFor by remember { mutableStateOf<RecentRow?>(null) }
    LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        if (!setup.isPhoneApp) {
            item(key = "setup-phone") {
                SetupCard(
                    "Make Ghanti your phone app",
                    "Strangers stop ringing out loud, unless a delivery is on its way.",
                    "Switch", actions::makeDefault, Modifier.animateItem(),
                )
            }
        } else if (!setup.screensCalls) {
            item(key = "setup-screening") {
                SetupCard(
                    "Let Ghanti silence strangers",
                    "Android asks its \"Caller ID & spam app\" which calls ring. Make that Ghanti too.",
                    "Allow", actions::makeDefault, Modifier.animateItem(),
                )
            }
        } else if (!setup.readsNotifications) {
            item(key = "setup-listener") {
                SetupCard(
                    "Let delivery calls through",
                    "Ghanti looks at notifications for words like \"on the way\", so the rider's call rings. Nothing leaves this phone.",
                    "Allow", actions::allowNotifications, Modifier.animateItem(),
                )
            }
        }
        if (recents.isEmpty()) {
            item(key = "empty") { Empty("No calls yet", Modifier.animateItem()) }
        }
        recents.forEachIndexed { i, row ->
            if (i == 0 || !sameDay(recents[i - 1].group.latest.date, row.group.latest.date)) {
                item(key = "day-${row.group.key}") { DayHeader(dayLabel(row.group.latest.date), Modifier.animateItem()) }
            }
            item(key = row.group.key) {
                RecentItem(
                    row = row,
                    open = open == row.group.key,
                    onToggle = { open = if (open == row.group.key) null else row.group.key },
                    onMemo = { memoFor = row },
                    actions = actions,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
    memoFor?.let { row ->
        val latest = row.group.latest
        MemoSheet(
            subtitle = listOf(row.name ?: row.group.number.ifBlank { "Private number" }, dayLabel(latest.date), clockTime(latest.date)).joinToString(" · "),
            stranger = row.name == null && row.group.number.isNotBlank(),
            suggestions = suggestionsFor(row),
            memo = row.memos[latest.id],
            onSave = { label, note -> actions.saveMemo(row.group.number, latest.date, label, note) },
            onDismiss = { memoFor = null },
        )
    }
}

/** what this stranger might have been: the guess at the time, and who they were before */
private fun suggestionsFor(row: RecentRow) =
    (row.outcome?.guess?.brands.orEmpty().map(DeliveryRules::tagFor) + listOfNotNull(row.before)).distinct()

private fun hintFor(tag: String) = when {
    DeliveryRules.isRide(tag) -> AvatarHint.Ride
    tag == "Delivery" || tag.endsWith(" delivery") -> AvatarHint.Delivery
    else -> AvatarHint.None
}

@Composable
private fun RecentItem(row: RecentRow, open: Boolean, onToggle: () -> Unit, onMemo: () -> Unit, actions: HomeActions, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val latest = row.group.latest
    val silenced = row.outcome?.rang == false && latest.kind == Kind.Missed
    val missed = latest.kind == Kind.Missed && !silenced
    val interaction = remember { MutableInteractionSource() }
    val guess = row.outcome?.guess
    val label = row.label
    val hint = when {
        label != null -> null
        guess != null -> guess.title
        row.before != null -> "${row.before} last time"
        else -> null
    }
    val hasNote = row.memos.values.any { it.note.isNotBlank() }
    val face = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (open) scheme.surfaceContainer else Color.Transparent),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressScale(interaction, 0.98f)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { haptics.play(Haptic.Tap); onToggle() },
                    onLongClick = { if (row.group.number.isNotBlank()) { haptics.play(Haptic.Copy); actions.copy(row.group.number) } },
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // the face opens the contact, or saves a stranger as one
            Avatar(
                name = row.name, photo = row.photo, seed = row.group.number, size = 48.dp,
                hint = label?.let(::hintFor) ?: guess?.let { if (it.ride) AvatarHint.Ride else AvatarHint.Delivery } ?: AvatarHint.None,
                modifier = Modifier
                    .pressScale(face, 0.88f)
                    .clip(CircleShape)
                    .combinedClickable(
                        interactionSource = face,
                        indication = null,
                        enabled = row.contactId != null || row.group.number.isNotBlank(),
                        onClick = {
                            haptics.play(Haptic.Tap)
                            row.contactId?.let(actions::openContact) ?: actions.addContact(row.group.number)
                        },
                    ),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (missed) scheme.error else scheme.onSurface,
                        fontWeight = if (missed && latest.isNew) FontWeight.Bold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.group.entries.size > 1) {
                        Text("  ${row.group.entries.size}", style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, label) = kindOf(latest.kind, silenced)
                    Icon(icon, null, tint = if (missed) scheme.error else scheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        listOfNotNull(hint ?: label, clockTime(latest.date)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hint != null) scheme.tertiary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (hasNote) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.Notes, "Has a note", tint = scheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    }
                }
            }
            if (row.group.number.isNotBlank()) {
                val press = remember { MutableInteractionSource() }
                FilledTonalIconButton(
                    onClick = { haptics.play(Haptic.Send); actions.call(row.group.number) },
                    shape = pressMorph(MaterialShapes.Circle, MaterialShapes.Cookie6Sided, press),
                    interactionSource = press,
                ) { Icon(Icons.Rounded.Call, "Call") }
            }
        }
        AnimatedVisibility(open, enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(bottom = 12.dp)) {
                Column(Modifier.padding(start = 70.dp, end = 12.dp)) {
                    // titled by who it was, so the number moves down here
                    if (row.name == null && label != null && row.group.number.isNotBlank()) {
                        Text(row.group.number, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    val stranger = row.name == null && row.group.number.isNotBlank()
                    val candidates = guess?.brands.orEmpty().map(DeliveryRules::tagFor)
                    if (stranger && label == null && candidates.isNotEmpty()) {
                        Confirm(candidates) { tag ->
                            actions.saveMemo(row.group.number, latest.date, tag, row.memos[latest.id]?.note.orEmpty())
                        }
                    }
                    row.group.entries.forEach { e ->
                        Text(
                            listOfNotNull(clockTime(e.date), kindOf(e.kind, false).second, e.durationSec.takeIf { it > 0 }?.let(::talkTime)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                        row.memos[e.id]?.note?.takeIf { it.isNotBlank() }?.let { note ->
                            Text(
                                note,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .padding(top = 2.dp, bottom = 6.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(scheme.surfaceContainerHighest)
                                    .combinedClickable(onClick = { haptics.play(Haptic.Tap); onMemo() })
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (row.group.number.isNotBlank()) Action(Icons.Rounded.Sms, "Message") { actions.message(row.group.number) }
                    Action(Icons.Rounded.EditNote, "Note") { onMemo() }
                    if (row.name == null && row.group.number.isNotBlank()) Action(Icons.Rounded.PersonAdd, "Save") { actions.addContact(row.group.number) }
                    if (row.group.number.isNotBlank()) Action(Icons.Rounded.ContentCopy, "Copy") { actions.copy(row.group.number) }
                    Action(Icons.Rounded.Delete, "Remove") { haptics.play(Haptic.Delete); actions.delete(row) }
                }
            }
        }
    }
}

/** "Was it Ownly delivery?": one tap remembers who called */
@Composable
private fun Confirm(candidates: List<String>, onPick: (String) -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            if (candidates.size == 1) "Was it ${candidates.single()}?" else "Who was it?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            candidates.forEach { tag ->
                SuggestionChip(
                    onClick = { haptics.play(Haptic.ToggleOn); onPick(tag) },
                    label = { Text(if (candidates.size == 1) "Yes, remember it" else tag) },
                    icon = { Icon(Icons.Rounded.Check, null, Modifier.size(SuggestionChipDefaults.IconSize)) },
                )
            }
        }
    }
}

private fun kindOf(kind: Kind, silenced: Boolean): Pair<ImageVector, String> = when {
    silenced -> Icons.Rounded.NotificationsOff to "Silenced"
    kind == Kind.Missed -> Icons.AutoMirrored.Rounded.CallMissed to "Missed"
    kind == Kind.Declined -> Icons.AutoMirrored.Rounded.CallMissed to "Declined"
    kind == Kind.Blocked -> Icons.Rounded.Block to "Blocked"
    kind == Kind.Outgoing -> Icons.AutoMirrored.Rounded.CallMade to "Outgoing"
    else -> Icons.AutoMirrored.Rounded.CallReceived to "Incoming"
}

private fun talkTime(sec: Long) = if (sec < 60) "${sec}s" else "${sec / 60} min"

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .widthIn(min = 64.dp)
            .pressScale(interaction, 0.92f)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(interactionSource = interaction, indication = null, onClick = { haptics.play(Haptic.Tap); onClick() })
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 24.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun SetupCard(title: String, line: String, button: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(28.dp))
            .background(scheme.primaryContainer).padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = scheme.onPrimaryContainer)
        Spacer(Modifier.height(4.dp))
        Text(line, style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
        Spacer(Modifier.height(14.dp))
        Button(onClick = { haptics.play(Haptic.Tap); onClick() }) { Text(button) }
    }
}

@Composable
private fun Empty(line: String, modifier: Modifier = Modifier) {
    Text(
        line,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(top = 96.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun ContactsPage(
    contacts: List<Contact>,
    query: String,
    usual: Map<Long, String>,
    list: androidx.compose.foundation.lazy.LazyListState,
    padding: PaddingValues,
    actions: HomeActions,
) {
    val haptics = rememberHaptics()
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf<Contact?>(null) }
    var holding by remember { mutableStateOf<Contact?>(null) }
    val shown = remember(contacts, query) {
        if (query.isBlank()) contacts
        else {
            val q = query.trim().lowercase()
            val digits = q.filter(Char::isDigit)
            contacts.filter { c ->
                c.name.lowercase().split(' ').any { it.startsWith(q) } || c.name.lowercase().contains(q) ||
                    (digits.length >= 3 && c.numbers.any { it.number.filter(Char::isDigit).contains(digits) })
            }
        }
    }
    val favorites = remember(contacts) { contacts.filter { it.starred } }
    val showFavorites = query.isBlank() && favorites.isNotEmpty()
    // the letter each list item sits under, in list order, for the scroll thumb's bubble
    val letters = remember(shown, showFavorites, query) {
        buildList {
            if (showFavorites) add('★')
            if (shown.isEmpty()) add(null)
            shown.forEachIndexed { i, c ->
                val letter = letterOf(c)
                if (query.isBlank() && (i == 0 || letter != letterOf(shown[i - 1]))) add(letter)
                add(letter)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            if (showFavorites) {
                item(key = "favorites") {
                    Favorites(
                        favorites, usual,
                        onTap = { c ->
                            val number = c.usualNumber(usual[c.id])
                            if (number != null) { haptics.play(Haptic.Send); actions.call(number.number) } else { haptics.play(Haptic.Tap); picking = c }
                        },
                        onHold = { c -> haptics.play(Haptic.Hold); holding = c },
                    )
                }
            }
            if (shown.isEmpty()) item(key = "empty") { Empty(if (query.isBlank()) "No contacts" else "No one called \"$query\"") }
            shown.forEachIndexed { i, contact ->
                val letter = letterOf(contact)
                if (query.isBlank() && (i == 0 || letter != letterOf(shown[i - 1]))) {
                    item(key = "letter-$letter-${contact.id}") { DayHeader(letter.toString()) }
                }
                item(key = contact.id) {
                    ContactItem(contact, open == contact.id, onToggle = { open = if (open == contact.id) null else contact.id }, actions = actions, modifier = Modifier.animateItem())
                }
            }
        }
        if (query.isBlank() && shown.size > 30) {
            FastScroller(list, letters, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
        }
    }

    picking?.let { c ->
        PickNumberSheet(
            c,
            onCall = { n, always -> if (always) actions.setUsual(c.id, n.number); actions.call(n.number) },
            onDismiss = { picking = null },
        )
    }
    holding?.let { c ->
        FavoriteSheet(
            c, usual[c.id],
            onCall = actions::call,
            onMessage = actions::message,
            onForget = { actions.setUsual(c.id, null) },
            onOpen = { actions.openContact(c.id) },
            onDismiss = { holding = null },
        )
    }
}

private fun letterOf(c: Contact) = c.name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'

/**
 * A thumb that shows while the list moves. Dragging it flies through the alphabet with the letter
 * in a bubble beside your thumb.
 */
@Composable
private fun FastScroller(list: androidx.compose.foundation.lazy.LazyListState, letters: List<Char?>, top: Dp, bottom: Dp) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(list.isScrollInProgress, dragging) {
        if (list.isScrollInProgress || dragging) shown = true else { delay(1_500); shown = false }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "thumb")
    val width by animateDpAsState(if (dragging) 10.dp else 5.dp, label = "thumbWidth")
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = top + 8.dp, bottom = bottom)) {
        val thumb = with(density) { 56.dp.toPx() }
        val track = (constraints.maxHeight - thumb).coerceAtLeast(1f)
        var dragFraction by remember { mutableFloatStateOf(0f) }
        val listFraction by remember {
            derivedStateOf {
                val info = list.layoutInfo
                val scrollable = info.totalItemsCount - info.visibleItemsInfo.size
                if (scrollable <= 0) 0f else (list.firstVisibleItemIndex.toFloat() / scrollable).coerceIn(0f, 1f)
            }
        }
        val fraction = if (dragging) dragFraction else listFraction
        val index = (fraction * (letters.size - 1)).roundToInt().coerceIn(0, (letters.size - 1).coerceAtLeast(0))
        val letter = letters.getOrNull(index)
        LaunchedEffect(letter) { if (dragging) haptics.play(Haptic.Segment) }
        AnimatedVisibility(
            visible = dragging && letter != null,
            enter = scaleIn(transformOrigin = TransformOrigin(1f, 0.5f)) + fadeIn(),
            exit = scaleOut(transformOrigin = TransformOrigin(1f, 0.5f)) + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(-56.dp.roundToPx(), (fraction * track).roundToInt()) },
        ) {
            Box(
                Modifier.size(56.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(letter.toString(), style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (fraction * track).roundToInt()) }
                .size(28.dp, 56.dp)
                .graphicsLayer { this.alpha = alpha }
                .pointerInput(shown) {
                    if (!shown) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { dragFraction = listFraction; dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, dy ->
                        change.consume()
                        dragFraction = (dragFraction + dy / track).coerceIn(0f, 1f)
                        val target = (dragFraction * (letters.size - 1)).roundToInt()
                        scope.launch { list.scrollToItem(target) }
                    }
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(Modifier.padding(end = 4.dp).width(width).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

/** starred people as big faces: a tap calls (asking which number if they have a few), holding shows everything */
@Composable
private fun Favorites(favorites: List<Contact>, usual: Map<Long, String>, onTap: (Contact) -> Unit, onHold: (Contact) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(favorites, key = { it.id }) { c ->
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .width(72.dp)
                    .pressScale(interaction, 0.92f)
                    .combinedClickable(interactionSource = interaction, indication = null, onClick = { onTap(c) }, onLongClick = { onHold(c) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(c.name, c.photo, c.id.toString(), size = 64.dp)
                Spacer(Modifier.height(6.dp))
                Text(c.name.substringBefore(' '), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.numbers.size > 1) {
                    // which number a tap will ring, so it's never a surprise
                    val line = c.usualNumber(usual[c.id])?.label ?: "${c.numbers.size} numbers"
                    AnimatedContent(line, label = "usual") { shown ->
                        Text(shown, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** stays out of the way as you scroll down the contacts, back the moment you scroll up */
@Composable
private fun ContactSearch(query: String, onQuery: (String) -> Unit, backdrop: Color, modifier: Modifier = Modifier) {
    // picks up the header's fade at the top and lets it go over a short strip below the bar
    Column(
        modifier.fillMaxWidth().drawBehind {
            val strip = (size.height - FADE_STRIP.toPx()) / size.height
            drawRect(Brush.verticalGradient(0f to backdrop.copy(alpha = 0.65f), strip to backdrop.copy(alpha = 0.65f), 1f to backdrop.copy(alpha = 0f)))
        },
    ) {
        TextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Search contacts") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                AnimatedVisibility(query.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(FADE_STRIP))
    }
}

private val FADE_STRIP = 12.dp

@Composable
private fun ContactItem(contact: Contact, open: Boolean, onToggle: () -> Unit, actions: HomeActions, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(24.dp))
            .background(if (open) scheme.surfaceContainer else Color.Transparent),
    ) {
        Row(
            Modifier.fillMaxWidth().pressScale(interaction, 0.98f)
                .combinedClickable(interactionSource = interaction, indication = null, onClick = { haptics.play(Haptic.Tap); onToggle() })
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val face = remember { MutableInteractionSource() }
            Avatar(
                contact.name, contact.photo, contact.id.toString(), size = 44.dp,
                modifier = Modifier.pressScale(face, 0.88f).clip(CircleShape)
                    .combinedClickable(interactionSource = face, indication = null, onClick = { haptics.play(Haptic.Tap); actions.openContact(contact.id) }),
            )
            Spacer(Modifier.width(14.dp))
            Text(contact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        AnimatedVisibility(open, enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 66.dp, end = 8.dp, bottom = 8.dp)) {
                contact.numbers.forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .combinedClickable(onClick = {}, onLongClick = { haptics.play(Haptic.Copy); actions.copy(n.number) })
                                .padding(vertical = 4.dp),
                        ) {
                            Text(n.number, style = MaterialTheme.typography.bodyLarge)
                            Text(n.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { haptics.play(Haptic.Tap); actions.message(n.number) }) { Icon(Icons.Rounded.Sms, "Message") }
                        val press = remember { MutableInteractionSource() }
                        FilledTonalIconButton(
                            onClick = { haptics.play(Haptic.Send); actions.call(n.number) },
                            shape = pressMorph(MaterialShapes.Circle, MaterialShapes.Cookie6Sided, press),
                            interactionSource = press,
                        ) { Icon(Icons.Rounded.Call, "Call") }
                    }
                }
            }
        }
    }
}
