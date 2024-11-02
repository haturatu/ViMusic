package app.vimusic.android.ui.components.themed

import androidx.annotation.FloatRange
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.shimmer
import kotlin.random.Random

@Composable
fun TextPlaceholder(
    modifier: Modifier = Modifier,
    color: Color = LocalAppearance.current.colorPalette.shimmer,
    @FloatRange(from = 0.0, to = 1.0)
    width: Float = remember { 0.25f + Random.nextFloat() * 0.5f }
) = Spacer(
    modifier = modifier
        .padding(vertical = 4.dp)
        .background(color)
        .fillMaxWidth(width)
        .height(16.dp)
)
