package app.vimusic.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// @formatter:off
@Suppress("MaximumLineLength")
private val steps = persistentListOf(
    arrayOf(0.8f, 0.1f, 0.9f, 0.9f, 0.7f, 0.9f, 0.8f, 0.1f, 0.3f, 0.8f, 0.6f, 0.0f, 0.3f, 0.4f, 0.9f, 0.7f, 0.9f, 0.6f, 0.9f, 0.1f, 0.3f, 0.0f, 0.5f, 0.4f, 0.7f, 0.9f),
    arrayOf(0.8f, 0.5f, 0.0f, 0.5f, 0.7f, 0.9f, 0.8f, 0.7f, 0.5f, 0.9f, 0.4f, 0.5f, 0.7f, 0.3f, 0.1f, 0.0f, 0.7f, 0.9f, 0.5f, 0.7f, 0.4f, 0.0f, 0.4f, 0.3f, 0.6f, 0.9f),
    arrayOf(0.4f, 0.5f, 0.0f, 0.4f, 0.5f, 0.0f, 0.4f, 0.5f, 0.0f, 0.5f, 0.4f, 0.3f, 0.8f, 0.7f, 0.9f, 0.5f, 0.6f, 0.4f, 0.3f, 0.9f, 0.6f, 0.7f, 0.9f, 0.6f, 0.7f, 0.3f)
)
// @formatter:on

@Composable
fun MusicBars(
    color: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 4.dp,
    cornerRadius: Dp = 16.dp,
    space: Dp = 4.dp
) {
    val animatables = remember { List(steps.size) { Animatable(0f) } }

    LaunchedEffect(Unit) {
        animatables.fastForEachIndexed { i, animatable ->
            launch {
                var step = 0
                val steps = steps[i]
                while (isActive) {
                    animatable.animateTo(steps[step])
                    step = (step + 1) % steps.size
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(barWidth * animatables.size + space * animatables.lastIndex)
    ) {
        val radius = CornerRadius(cornerRadius.toPx())
        val barWidthPx = barWidth.toPx()
        val barHeightPx = size.height
        val stride = barWidthPx + space.toPx()

        animatables.fastForEachIndexed { i, animatable ->
            val value = animatable.value

            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = i * stride,
                    y = barHeightPx * value
                ),
                size = Size(
                    width = barWidthPx,
                    height = barHeightPx * (1 - value)
                ),
                cornerRadius = radius
            )
        }
    }
}
