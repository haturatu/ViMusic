package app.vimusic.android.ui.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

private fun Modifier.fadingEdge(
    start: Boolean,
    middle: Int,
    end: Boolean,
    alpha: Float,
    isHorizontal: Boolean
) = this
    .graphicsLayer(alpha = 0.99f)
    .drawWithContent {
        drawContent()
        val gradient = buildList {
            val transparentColor = Color(red = 0f, green = 0f, blue = 0f, alpha = 1f - alpha)

            add(if (start) transparentColor else Color.Black)
            repeat(middle) { add(Color.Black) }
            add(if (end) transparentColor else Color.Black)
        }
        drawRect(
            brush = if (isHorizontal) Brush.horizontalGradient(gradient)
            else Brush.verticalGradient(gradient),
            blendMode = BlendMode.DstIn
        )
    }

fun Modifier.verticalFadingEdge(
    top: Boolean = true,
    middle: Int = 3,
    bottom: Boolean = true,
    alpha: Float = 1f
) = fadingEdge(start = top, middle = middle, end = bottom, alpha = alpha, isHorizontal = false)

fun Modifier.horizontalFadingEdge(
    left: Boolean = true,
    middle: Int = 3,
    right: Boolean = true,
    alpha: Float = 1f
) = fadingEdge(start = left, middle = middle, end = right, alpha = alpha, isHorizontal = true)
