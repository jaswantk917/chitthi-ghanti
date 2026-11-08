package io.github.jaswantk917.ghanti

import android.Manifest
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import io.github.jaswantk917.ghanti.data.Home
import io.github.jaswantk917.ghanti.data.RecentRow
import io.github.jaswantk917.ghanti.data.Recents
import io.github.jaswantk917.ghanti.signals.DeliveryListener
import io.github.jaswantk917.ghanti.signals.DeliveryRules
import io.github.jaswantk917.ghanti.ui.home.ExpectingSheet
import io.github.jaswantk917.ghanti.ui.home.HomeActions
import io.github.jaswantk917.ghanti.ui.home.HomeScreen
import io.github.jaswantk917.ghanti.ui.home.KeypadSheet
import io.github.jaswantk917.ghanti.ui.home.SettingsSheet
import io.github.jaswantk917.ghanti.ui.home.Setup
import io.github.jaswantk917.ghanti.ui.theme.GhantiTheme
import io.github.jaswantk917.ghanti.ui.util.LocalBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val app get() = GhantiApp.instance
    private lateinit var home: Home
    /** null: closed; otherwise what the keypad opens with */
    private var keypad by mutableStateOf<String?>(null)
    private var setup by mutableStateOf(Setup(isPhoneApp = false, screensCalls = false, readsNotifications = false, silencing = true))
    private var pendingCall: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        home = Home(this, lifecycleScope)
        handle(intent)
        setContent {
            val settings by app.prefs.settings.collectAsState()
            GhantiTheme(settings.palette, settings.amoled) {
                CompositionLocalProvider(LocalBlur provides settings.blur) { Root() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        home.start()
        refreshSetup()
    }

    override fun onStop() {
        home.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** "dial this number" from anywhere opens the keypad with it typed, never calls by itself */
    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_DIAL || intent?.action == Intent.ACTION_VIEW) {
            keypad = intent.data?.schemeSpecificPart.orEmpty()
        }
    }

    private fun refreshSetup() {
        val roles = getSystemService(RoleManager::class.java)
        setup = Setup(
            isPhoneApp = roles.isRoleHeld(RoleManager.ROLE_DIALER),
            screensCalls = roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
            readsNotifications = packageName in NotificationManagerCompat.getEnabledListenerPackages(this),
            silencing = app.prefs.settings.value.screen.silenceUnknown,
        )
    }

    @Composable
    private fun Root() {
        val state by home.state.collectAsState()
        val settings by app.prefs.settings.collectAsState()
        val signalTick by app.signals.changes.collectAsState()
        var settingsOpen by remember { mutableStateOf(false) }
        var expectingOpen by remember { mutableStateOf(false) }
        // what's on its way, rechecked every minute since hints expire with time
        val expecting by produceState(emptyList(), signalTick) {
            while (true) {
                value = withContext(Dispatchers.IO) { DeliveryRules.active(app.signals.current(), System.currentTimeMillis()) }
                delay(60_000)
            }
        }

        val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            home.start()
            pendingCall?.let { number -> pendingCall = null; if (canCall()) place(number) }
        }
        // two roles, asked one after the other: answering calls, then deciding which ring
        val screeningRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshSetup() }
        val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshSetup()
            home.reload()
            if (setup.isPhoneApp && !setup.screensCalls) {
                screeningRole.launch(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            }
        }
        LaunchedEffect(Unit) {
            val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
        }
        LaunchedEffect(settings.screen.silenceUnknown) { refreshSetup() }
        val memos by app.memos.all.collectAsState()
        LaunchedEffect(memos) { home.reload() }

        val actions = remember {
            object : HomeActions {
                override fun call(number: String) {
                    if (canCall()) place(number) else {
                        pendingCall = number
                        permissions.launch(arrayOf(Manifest.permission.CALL_PHONE))
                    }
                }
                override fun message(number: String) {
                    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
                }
                override fun copy(number: String) {
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("number", number))
                    // 13+ shows its own copied chip
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this@MainActivity, "Copied", Toast.LENGTH_SHORT).show()
                }
                // the contacts app asks: a new contact, or add it to someone you have
                override fun addContact(number: String) {
                    startActivity(
                        Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                            .putExtra(ContactsContract.Intents.Insert.PHONE, number),
                    )
                }
                override fun openContact(id: Long) {
                    startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)))
                }
                override fun setUsual(contactId: Long, number: String?) = app.prefs.setUsual(contactId, number)
                override fun saveMemo(number: String, at: Long, label: String?, note: String) = app.memos.save(number, at, label, note)
                override fun delete(row: RecentRow) {
                    lifecycleScope.launch(Dispatchers.IO) { runCatching { Recents.delete(this@MainActivity, row.group.entries.map { it.id }) } }
                }
                override fun keypad() { keypad = "" }
                override fun settings() { settingsOpen = true }
                override fun expecting() { expectingOpen = true }
                override fun makeDefault() {
                    val roles = getSystemService(RoleManager::class.java)
                    if (!setup.isPhoneApp) role.launch(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                    else screeningRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                }
                override fun allowNotifications() = openListenerSettings()
                override fun recentsSeen() {
                    if (!setup.isPhoneApp) return
                    runCatching { getSystemService(TelecomManager::class.java).cancelMissedCallsNotification() }
                    lifecycleScope.launch(Dispatchers.IO) { Recents.markSeen(this@MainActivity) }
                }
            }
        }

        val usual by app.prefs.usual.collectAsState()
        HomeScreen(state, setup, expecting, usual, actions)

        keypad?.let { initial ->
            KeypadSheet(
                initial = initial,
                contacts = state.contacts,
                recents = state.recents,
                onCall = { number -> keypad = null; actions.call(number) },
                onDismiss = { keypad = null },
            )
        }
        if (settingsOpen) {
            SettingsSheet(
                settings = settings,
                setup = setup,
                onChange = app.prefs::update,
                onMakeDefault = actions::makeDefault,
                onAllowNotifications = ::openListenerSettings,
                onDismiss = { settingsOpen = false },
            )
        }
        if (expectingOpen) {
            ExpectingSheet(expecting, setup, onAllowNotifications = ::openListenerSettings, onDismiss = { expectingOpen = false })
        }
    }

    private fun canCall() = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    private fun place(number: String) {
        try {
            getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), Bundle())
        } catch (e: SecurityException) {
            Log.w(TAG, "can't place call", e)
        }
    }

    private fun openListenerSettings() {
        val component = ComponentName(this, DeliveryListener::class.java)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        runCatching { startActivity(detail) }.onFailure { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    private companion object {
        const val TAG = "Ghanti"
        val wanted = buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.WRITE_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
