package io.github.jaswantk917.showcase

import android.content.res.Configuration
import android.graphics.Bitmap
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import io.github.jaswantk917.chitthi.classify.SenderId
import io.github.jaswantk917.chitthi.classify.Classifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import io.github.jaswantk917.chitthi.data.FreshOtp
import io.github.jaswantk917.chitthi.data.InboxState
import io.github.jaswantk917.chitthi.model.Box as MessageBox
import io.github.jaswantk917.chitthi.model.Category
import io.github.jaswantk917.chitthi.model.Conversation
import io.github.jaswantk917.chitthi.model.Message
import io.github.jaswantk917.chitthi.model.SenderInfo
import io.github.jaswantk917.chitthi.model.Tag
import io.github.jaswantk917.chitthi.model.Verdict
import io.github.jaswantk917.chitthi.ui.inbox.InboxActions
import io.github.jaswantk917.chitthi.ui.inbox.InboxScreen
import io.github.jaswantk917.chitthi.ui.theme.ChitthiTheme
import io.github.jaswantk917.ghanti.data.CallEntry
import io.github.jaswantk917.ghanti.data.CallRecording
import io.github.jaswantk917.ghanti.data.Contact
import io.github.jaswantk917.ghanti.data.HomeState
import io.github.jaswantk917.ghanti.data.Kind
import io.github.jaswantk917.ghanti.data.PhoneNumber
import io.github.jaswantk917.ghanti.data.RecentRow
import io.github.jaswantk917.ghanti.data.Recents
import io.github.jaswantk917.ghanti.incall.CallUi
import io.github.jaswantk917.ghanti.incall.Phase
import io.github.jaswantk917.ghanti.screen.Outcome
import io.github.jaswantk917.ghanti.signals.Guess
import io.github.jaswantk917.ghanti.signals.Signal
import io.github.jaswantk917.ghanti.signals.Strength
import io.github.jaswantk917.ghanti.ui.home.HomeActions
import io.github.jaswantk917.ghanti.ui.home.HomeScreen
import io.github.jaswantk917.ghanti.ui.home.Setup
import io.github.jaswantk917.ghanti.ui.incall.InCallScreen
import io.github.jaswantk917.ghanti.ui.theme.GhantiTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.jaswantk917.chitthi.R
import io.github.jaswantk917.ghanti.ui.theme.RoundedFlex
import io.github.jaswantk917.ghanti.ui.theme.SansFlex

/**
 * Store and README screenshots: the real screens, with made-up people named like film characters
 * and numbers that aren't anyone's. 1080 x 2160, dark, text size 1.0; they land in files/showcase.
 * "play-" ones show only what Tring does on its own; "github-" ones add the recorder.
 */
class ShowcaseTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val minute = 60_000L
    // calls sit on a quarter-hour grid, so Tring++'s showcase run a minute later shows the same clock times
    private val clock = now / (15 * minute) * (15 * minute)
    private val hour = 60 * minute
    private val day = 24 * hour
    private var nextId = 1L

    private fun shot(dark: Boolean = true, width: Int = WIDTH, height: Int = HEIGHT, body: @Composable () -> Unit) {
        rule.runOnUiThread {
            // the phone may lock mid-run; the test screen shows over it and keeps the screen on while it's up
            rule.activity.setShowWhenLocked(true)
            rule.activity.setTurnScreenOn(true)
            rule.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            rule.activity.enableEdgeToEdge()
        }
        rule.setContent {
            val density = LocalDensity.current
            val base = LocalConfiguration.current
            val night = remember(base) {
                Configuration(base).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            }
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f), LocalConfiguration provides night) {
                val size = with(LocalDensity.current) { Modifier.requiredSize(width.toDp(), height.toDp()) }
                Box(size.testTag(SHOT)) { body() }
            }
        }
        settle()
    }

    private fun settle() {
        rule.mainClock.advanceTimeBy(2_000)
        // photos and avatars load off the main thread
        repeat(10) { Thread.sleep(100); rule.mainClock.advanceTimeBy(100) }
    }

    private fun save(name: String) {
        val dir = File(rule.activity.getExternalFilesDir(null), "showcase").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onNodeWithTag(SHOT).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---- Chitthi ----

    private fun sms(address: String, body: String, ago: Long, incoming: Boolean = true, read: Boolean = true) =
        Message("s${nextId}", nextId++, false, nextId, address, body, now - ago, if (incoming) MessageBox.Inbox else MessageBox.Sent, read)

    private fun chat(sender: SenderInfo, category: Category, latest: Message, verdict: Verdict = Verdict(category), unread: Int = 0, pinned: Boolean = false) =
        Conversation(sender.key, category, sender, latest, verdict, unread, pinned)

    // tagged by the app itself, as the inbox would
    private fun classified(message: Message) = Classifier.classify(SenderId.parse(message.address), message.body, isContact = false)

    private fun important(sender: SenderInfo, message: Message) = chat(sender, Category.Important, message, classified(message))

    private fun person(key: String, name: String) = SenderInfo(key, name, null, canReply = true, replyAddresses = listOf(key), isContact = true)
    private fun business(key: String, name: String, subtitle: String) = SenderInfo(key, name, subtitle, canReply = false)

    @Test
    fun chitthi() {
        val bank = business("JM-KUBERB", "Kuber Bank", "JM-KUBERB")
        val otp = sms("JM-KUBERB", "482913 is your OTP to log in to Kuber Bank. It expires in 5 minutes. Never share it with anyone.", 2 * minute, read = false)
        val important = listOf(
            chat(bank, Category.Important, otp, classified(otp), unread = 1),
            important(business("VM-IRAILW", "Rail booking", "VM-IRAILW"), sms("VM-IRAILW", "PNR 4521 897632: Simran K, coach S4, berth 32. Train leaves at 9:15 pm from platform 4.", 40 * minute)),
            important(business("AD-BIJLIB", "Bijli Board", "AD-BIJLIB"), sms("AD-BIJLIB", "Your bill of Rs 1,240 is due on 28 Nov. Pay online to avoid a late fee.", 3 * hour)),
            important(business("JM-KUBERD", "Kuber Bank", "JM-KUBERD"), sms("JM-KUBERD", "Rs 650 debited from A/c XX4410 at Chai Tapri on 24 Nov.", 6 * hour)),
        )
        val personal = listOf(
            chat(person("+919000000101", "Simran"), Category.Personal, sms("+919000000101", "Train 9:15 ki hai, platform 4. Late mat hona 🚂", 12 * minute, read = false), unread = 2, pinned = true),
            chat(person("+919000000102", "Raj"), Category.Personal, sms("+919000000102", "Main station ke bahar hoon, gate 2 pe", 25 * minute, incoming = false)),
            chat(person("+919000000103", "Babu Bhaiya"), Category.Personal, sms("+919000000103", "Kal dukaan 7 baje khulegi, time pe aana", 2 * hour)),
            chat(person("+919000000104", "Anjali"), Category.Personal, sms("+919000000104", "Basketball match Sunday ko hai, aa rahe ho?", 5 * hour)),
            chat(person("+919000000105", "Poo"), Category.Personal, sms("+919000000105", "PHOTO bhejo na, abhi ke abhi 💅", day)),
        )
        val state = InboxState(
            loaded = true, hasPermission = true, isDefault = true,
            conversations = mapOf(Category.Important to important, Category.Personal to personal, Category.Promotions to emptyList(), Category.Unknown to emptyList()),
            freshOtp = FreshOtp("482913", bank, otp),
        )
        shot(dark = false) { ChitthiTheme { InboxScreen(state, NoInbox, remember { SnackbarHostState() }) } }
        save("play-1-chitthi")
        // the inbox keeps something moving after the tab changes, so the clock is stepped by hand
        rule.mainClock.autoAdvance = false
        rule.onAllNodesWithText("Personal", substring = true).onFirst().performClick()
        settle()
        save("play-5-personal")
    }

    // ---- Ghanti ----

    private val simran = Contact(101, "Simran", null, true, listOf(PhoneNumber("+919000000101", "Mobile")))
    private val raj = Contact(102, "Raj", null, false, listOf(PhoneNumber("+919000000102", "Mobile")))
    private val babu = Contact(103, "Babu Bhaiya", null, true, listOf(PhoneNumber("+919000000103", "Mobile")))
    private val anjali = Contact(104, "Anjali", null, true, listOf(PhoneNumber("+919000000104", "Mobile")))
    private val poo = Contact(105, "Poo", null, false, listOf(PhoneNumber("+919000000105", "Mobile")))
    private val munna = Contact(106, "Munna", null, false, listOf(PhoneNumber("+919000000106", "Mobile")))
    private val geet = Contact(107, "Geet", null, true, listOf(PhoneNumber("+919000000107", "Mobile")))
    private val contacts = listOf(simran, raj, babu, anjali, poo, munna, geet)

    private val bistroNumber = "+91 90000 77123"
    private val unknown = "+91 90000 41234"
    private val babuCallId = 900L

    private fun call(number: String, name: String?, kind: Kind, ago: Long, seconds: Long = 0, id: Long = nextId++) =
        CallEntry(id, number, name, kind, clock - ago, seconds, false)

    private fun recents(withRecording: Boolean): List<RecentRow> {
        val entries = listOf(
            call(bistroNumber, null, Kind.Incoming, 23 * minute, 34),
            call("+919000000102", "Raj", Kind.Outgoing, 77 * minute, 240),
            call(unknown, null, Kind.Missed, 2 * hour + 6 * minute),
            call("+919000000103", "Babu Bhaiya", Kind.Incoming, 3 * hour + 38 * minute, 412, id = babuCallId),
            call("+919000000101", "Simran", Kind.Outgoing, 5 * hour + 12 * minute, 610),
            call("+919000000105", "Poo", Kind.Missed, day + 2 * hour + 44 * minute),
            call("+919000000106", "Munna", Kind.Outgoing, day + 5 * hour + 19 * minute, 95),
            call("+919000000104", "Anjali", Kind.Incoming, 2 * day + 47 * minute, 300),
        )
        val bistro = Outcome(bistroNumber, clock - 23 * minute, rang = true, Guess("Probably Bistro delivery", brands = listOf("Bistro")))
        val silenced = Outcome(unknown, clock - 2 * hour - 6 * minute, rang = false, null)
        val recording = CallRecording(
            7001, clock - 3 * hour - 38 * minute + 4_000, 412_000,
            excerpt = "… कल सुबह दुकान सात बजे खोलनी है, सेठ जी का माल आने वाला है। श्याम को भी बोल देना, और किराए के पैसे साथ लाना …",
            more = true,
        )
        return Recents.group(entries.sortedByDescending { it.date }).map { group ->
            val contact = contacts.firstOrNull { c -> c.numbers.any { it.number == group.number } }
            RecentRow(
                group, contact?.name, null, contact?.id,
                outcome = when (group.number) { bistroNumber -> bistro; unknown -> silenced; else -> null },
                recordings = if (withRecording && group.latest.id == babuCallId) mapOf(babuCallId to recording) else emptyMap(),
            )
        }
    }

    private val onItsWay = listOf(Signal("Bistro", Strength.Live, now - 9 * minute, due = now + 6 * minute))

    @Test
    fun ghanti() {
        shot { GhantiTheme { HomeScreen(HomeState(loaded = true, recents = recents(false), contacts = contacts), Setup(true, true, true, true), onItsWay, emptyMap(), NoHome) } }
        save("play-2-ghanti")
    }

    @Test
    fun aRecordedCallInRecents() {
        shot { GhantiTheme { HomeScreen(HomeState(loaded = true, recents = recents(true), contacts = contacts), Setup(true, true, true, true), onItsWay, emptyMap(), NoHome) } }
        // the Contacts page is built off screen too; the Recents row comes first
        rule.onAllNodesWithText("Babu Bhaiya").onFirst().performClick()
        settle()
        // lift the open row clear of the keypad button, as a thumb would: the title tucks away as the list goes up
        val listId = rule.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyDescendant(hasText("Today"))).fetchSemanticsNode().id
        rule.onNode(SemanticsMatcher("the Recents list") { it.id == listId }).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 560f) }
        settle()
        save("github-2-recents-recording")
    }

    // Call is final with a hidden constructor; the screen only holds it
    private fun blankCall(): Call {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Call::class.java) as Call
    }

    @Test
    fun aDeliveryRingsThrough() {
        val rider = CallUi(
            call = blankCall(), phase = Phase.Ringing, number = bistroNumber, person = null, networkName = null,
            connectedAt = 0, createdAt = now - 4_000,
            outcome = Outcome(bistroNumber, now - 4_000, rang = true, Guess("Probably Bistro delivery", brands = listOf("Bistro"))),
            canHold = false, canMerge = false, canSwap = false, isConference = false, sims = emptyList(),
        )
        val earpiece = CallAudioState(false, CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)
        // a ringing call pulses forever, so the clock is stepped by hand
        rule.mainClock.autoAdvance = false
        shot(dark = false) { GhantiTheme { InCallScreen(rider, emptyList(), earpiece, endedAt = null, noting = false, onNoting = {}, onAddCall = {}) } }
        // to a whole number of the Answer button's 1.8 s bob, so it sits level with Decline
        rule.mainClock.advanceTimeBy(600)
        save("play-3-call")
    }

    @Test
    fun aDeliveryOnTheLine() {
        val rider = CallUi(
            call = blankCall(), phase = Phase.Active, number = bistroNumber, person = null, networkName = null,
            connectedAt = now - 47_000, createdAt = now - 61_000,
            outcome = Outcome(bistroNumber, now - 61_000, rang = true, Guess("Probably Bistro delivery", brands = listOf("Bistro"))),
            canHold = true, canMerge = false, canSwap = false, isConference = false, sims = emptyList(),
        )
        val earpiece = CallAudioState(false, CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)
        rule.mainClock.autoAdvance = false
        shot { GhantiTheme { InCallScreen(rider, emptyList(), earpiece, endedAt = null, noting = false, onNoting = {}, onAddCall = {}) } }
        save("play-4-on-the-line")
    }

    /** Play's feature graphic, 1024 x 500: the app's own mascot, font and words, nothing made up for the store */
    @Test
    fun featureGraphic() {
        shot(dark = false, width = 1024, height = 500) {
            // one dp per pixel, so the layout reads in the graphic's own pixels
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFF8FAFF), Color(0xFFE3ECFF)), end = Offset(420f, 1030f)))) {
                    Image(
                        painterResource(R.drawable.tring_launcher_foreground), null,
                        Modifier.size(480.dp).align(Alignment.BottomEnd).offset(x = 60.dp, y = 0.dp),
                    )
                    Column(Modifier.align(Alignment.CenterStart).padding(start = 72.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("Tring", fontFamily = RoundedFlex, fontWeight = FontWeight(900), fontSize = 148.sp, lineHeight = 150.sp, letterSpacing = (-0.03).em, color = Color(0xFF0B2A5C))
                        Side(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground, "Your SMS, sorted.")
                        Side(R.drawable.ghanti_launcher_background, R.drawable.ghanti_launcher_foreground, "Calls that know who's calling.")
                    }
                }
            }
        }
        save("feature-graphic")
    }

    /** one side of the app: its own icon, cut to the launcher's circle, and its line from the welcome screen */
    @Composable
    private fun Side(background: Int, foreground: Int, line: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // an adaptive icon's layers are 108 units with the middle 72 on show
            Box(Modifier.size(60.dp).clip(CircleShape)) {
                Image(painterResource(background), null, Modifier.matchParentSize().scale(1.5f))
                Image(painterResource(foreground), null, Modifier.matchParentSize().scale(1.5f))
            }
            Spacer(Modifier.width(20.dp))
            Text(line, fontFamily = SansFlex, fontWeight = FontWeight(650), fontSize = 32.sp, color = Color(0xFF1B2540))
        }
    }

    private object NoHome : HomeActions {
        override fun call(number: String) {}
        override fun message(number: String) {}
        override fun copy(number: String) {}
        override fun block(number: String) {}
        override fun addContact(number: String) {}
        override fun openContact(id: Long) {}
        override fun setUsual(contactId: Long, number: String?) {}
        override fun setRecord(numbers: List<String>, on: Boolean) {}
        override fun saveMemo(number: String, at: Long, label: String?, note: String) {}
        override fun delete(row: RecentRow) {}
        override fun keypad() {}
        override fun settings() {}
        override fun expecting() {}
        override fun makeDefault() {}
        override fun allowNotifications() {}
        override fun allowFullScreen() {}
        override fun recentsSeen() {}
    }

    private object NoInbox : InboxActions {
        override fun open(conversation: Conversation) {}
        override fun contact(conversation: Conversation) {}
        override fun newChat() {}
        override fun search() {}
        override fun settings() {}
        override fun makeDefault() {}
        override fun markRead(conversation: Conversation) {}
        override fun markAllRead(category: Category) {}
        override fun setPinned(conversation: Conversation, pinned: Boolean) {}
        override fun moveTo(conversation: Conversation, category: Category) {}
        override suspend fun delete(conversation: Conversation) = false
    }

    private companion object {
        const val SHOT = "shot"
        // Play takes phone screenshots up to twice as tall as wide
        const val WIDTH = 1080
        const val HEIGHT = 2160
    }
}
