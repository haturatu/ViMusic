package app.vimusic.android.ui.components.themed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vimusic.core.ui.LocalAppearance

/**
 * A simple horizontal divider, derived from Material Design
 */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = LocalAppearance.current.colorPalette.textDisabled
) = Canvas(
    modifier = modifier
        .fillMaxWidth()
        .height(thickness)
) {
    val stroke = thickness.toPx()

    drawLine(
        color = color,
        strokeWidth = stroke,
        start = Offset(
            x = 0f,
            y = stroke / 2
        ),
        end = Offset(
            x = size.width,
            y = stroke / 2
        )
    )
}

/**
 * A simple vertical divider, derived from Material Design
 */
@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = LocalAppearance.current.colorPalette.textDisabled
) = Canvas(
    modifier = modifier
        .fillMaxHeight()
        .width(thickness)
) {
    val stroke = thickness.toPx()

    drawLine(
        color = color,
        strokeWidth = stroke,
        start = Offset(
            x = stroke / 2,
            y = 0f
        ),
        end = Offset(
            x = stroke / 2,
            y = size.height
        )
    )
}
