package io.github.jaswantk917.sheet

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A bottom sheet's column that scrolls only when its content clearly doesn't fit. With a few
 * pixels of scroll, every drag splits between the content and the sheet: a hitch, a stretch,
 * a fling that settles oddly. Up to [slack] of overflow (the bottom padding) is clipped instead.
 */
@Composable
fun SheetColumn(modifier: Modifier = Modifier, slack: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val slackPx = with(LocalDensity.current) { slack.roundToPx() }
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Column(modifier.verticalScroll(scroll, enabled = scroll.maxValue > slackPx), content = content)
    }
}
