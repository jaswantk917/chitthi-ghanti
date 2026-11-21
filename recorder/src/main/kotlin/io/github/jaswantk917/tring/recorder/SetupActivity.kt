package io.github.jaswantk917.tring.recorder

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Three switches to flip once; after that the recorder has no screen of its own, only Tring's Record button. */
class SetupActivity : Activity() {
    private lateinit var steps: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        steps = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(24)
            setPadding(pad, dp(56), pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(steps) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        steps.removeAllViews()
        steps.addView(text("Tring Recorder", 30f, bold = true))
        steps.addView(text("Tap Record during a call in Tring, or let Tring record calls by itself. Afterwards the other person is brought up to your loudness. On the earpiece they sound thinner, because only the phone's own app can record the line itself.", 16f, top = 12))
        val mic = granted(Manifest.permission.RECORD_AUDIO)
        step("Microphone", mic, "Allow") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            step("A note when a recording is saved", granted(Manifest.permission.POST_NOTIFICATIONS), "Allow") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            }
        }
        step("Turn on Tring Recorder in Accessibility", serviceOn(), "Open") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        if (!serviceOn()) {
            steps.addView(text("Greyed out? Android blocks this for apps installed from a file until you allow it: App info → ⋮ → Allow restricted settings.", 14f, top = 8))
            steps.addView(button("Open App info") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            })
        }
        if (mic && serviceOn()) steps.addView(text("All set. Recordings go to Recordings/Tring.", 18f, bold = true, top = 24))
        // Play Protect blocks a downloaded app that asks for Accessibility (India and a few other countries),
        // so the guide has people pause its scanning to install; this is where they're reminded to undo it
        steps.addView(text("Paused Play Protect to install this? Turn it back on: Play Store → your picture → Play Protect → ⚙ → Scan apps with Play Protect.", 14f, top = 24))
        steps.addView(button("Open Play Protect") {
            runCatching { startActivity(Intent("com.google.android.gms.settings.VERIFY_APPS_SETTINGS")) }
        })
    }

    private fun step(title: String, done: Boolean, action: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(20), 0, 0)
        }
        row.addView(text(if (done) "✓  $title" else title, 18f, bold = done), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (!done) row.addView(button(action, onClick))
        steps.addView(row)
    }

    private fun serviceOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val me = ComponentName(this, CallRecorder::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) = render()

    private fun text(value: String, size: Float, bold: Boolean = false, top: Int = 0) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(top), 0, 0)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
