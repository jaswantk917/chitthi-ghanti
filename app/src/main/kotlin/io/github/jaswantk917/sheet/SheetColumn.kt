package io.github.jaswantk917.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A bottom sheet's column that scrolls, with its edge stretch, only when its content clearly
 * doesn't fit. With a few pixels of scroll, every drag splits between the content and the sheet:
 * a hitch, a stretch, a fling that settles oddly. The [bottom] space and the navigation bar sit
 * inside the scroll, so the last row scrolls clear of the bar, and when only that space overflows
 * it is clipped instead of scrolling.
 */
@Composable
fun SheetColumn(modifier: Modifier = Modifier, bottom: Dp = 40.dp, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val slackPx = with(density) { (bottom + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()).roundToPx() }
    Column(modifier.verticalScroll(scroll, enabled = scroll.maxValue > slackPx)) {
        content()
        Spacer(Modifier.navigationBarsPadding().height(bottom))
    }
}
