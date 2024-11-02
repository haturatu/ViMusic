package app.vimusic.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.vimusic.android.ui.modifiers.horizontalFadingEdge

@Composable
inline fun FadingRow(
    modifier: Modifier = Modifier,
    segments: Int = 12,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val alphaLeft by animateFloatAsState(
        targetValue = if (scrollState.canScrollBackward) 1f else 0f,
        label = ""
    )
    val alphaRight by animateFloatAsState(
        targetValue = if (scrollState.canScrollForward) 1f else 0f,
        label = ""
    )

    Row(
        modifier = modifier
            .horizontalFadingEdge(
                left = true,
                middle = segments - 2,
                right = false,
                alpha = alphaLeft
            )
            .horizontalFadingEdge(
                left = false,
                middle = segments - 2,
                right = true,
                alpha = alphaRight
            )
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = verticalAlignment,
        content = content
    )
}
