package io.github.jaswantk917.chitthi.ui.settings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.chitthi.ai.NanoStatus
import io.github.jaswantk917.chitthi.data.Settings
import io.github.jaswantk917.chitthi.ui.theme.ChitthiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Chitthi's settings at 360 dp and text size 1.3; screenshots land in the debug app's files/screens. */
class SettingsSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theSheetEndsWithTheSourceCodeLink() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                ChitthiTheme {
                    Box(Modifier.requiredWidth(360.dp)) {
                        SettingsSheet(Settings(), isDefault = true, nanoStatus = NanoStatus.Ready, onChange = {}, onMakeDefault = {}, countExpired = { 0 }, onOpenBlocked = {}, onDismiss = {})
                    }
                }
            }
        }
        repeat(4) {
            rule.onNode(isDialog()).performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.2f) }
            rule.waitForIdle()
        }
        rule.onNodeWithText("Source code").assertIsDisplayed()
        val layout = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Tring is open source, on GitHub").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertFalse(layout.first().isLineEllipsized(0))
        assertEquals(1, layout.first().lineCount)
        val dir = File(rule.activity.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "chitthi-settings-end.png").outputStream().use { rule.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
