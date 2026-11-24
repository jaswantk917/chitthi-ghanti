package io.github.jaswantk917.tring.recorder

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

/** The connection to [ShellService] through Shizuku. Without it, calls simply ring as they always did. */
object Shell {
    enum class Status { NotRunning, NeedsPermission, Starting, NoCallAudio, Ready }

    private val _status = MutableStateFlow(Status.NotRunning)
    val status: StateFlow<Status> = _status

    @Volatile var service: IScreenShell? = null
        private set
    /** no call is taken unless its sound can be opened: a silent answer with no voice is the worst outcome */
    @Volatile var audioWorks = false
        private set
    private var app: Context? = null
    private var binding = false
    var onLost: () -> Unit = {}

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(app!!.packageName, ShellService::class.java.name))
            .daemon(false).processNameSuffix("shell").debuggable(false).version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            binding = false
            val shell = binder?.takeIf { it.pingBinder() }?.let(IScreenShell.Stub::asInterface) ?: return refresh()
            service = shell
            runCatching { binder.linkToDeath({ lost() }, 0) }
            runCatching { shell.allowBeside(app!!.packageName) }.onFailure { Log.w(TAG, "couldn't allow the call service", it) }
            audioWorks = runCatching { shell.audioReady() }.getOrDefault(false)
            Log.i(TAG, "call audio opens: $audioWorks")
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName) = lost()
    }

    private fun lost() {
        service = null
        audioWorks = false
        binding = false
        onLost()
        refresh()
    }

    fun start(context: Context) {
        if (app != null) return
        app = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { lost() }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    fun refresh() {
        val alive = runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)
        _status.value = when {
            !alive -> Status.NotRunning
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Status.NeedsPermission
            service != null -> if (audioWorks) Status.Ready else Status.NoCallAudio
            else -> {
                if (!binding) {
                    binding = true
                    runCatching { Shizuku.bindUserService(args, connection) }.onFailure {
                        binding = false
                        Log.w(TAG, "couldn't start the shell service", it)
                    }
                }
                Status.Starting
            }
        }
    }

    fun askPermission() {
        runCatching { Shizuku.requestPermission(REQUEST) }
    }

    private const val TAG = "Shell"
    private const val REQUEST = 41
}
