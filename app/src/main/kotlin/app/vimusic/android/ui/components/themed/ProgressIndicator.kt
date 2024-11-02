package app.vimusic.android.ui.components.themed

import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import app.vimusic.core.ui.LocalAppearance

@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    strokeCap: StrokeCap? = null
) {
    val (colorPalette) = LocalAppearance.current

    if (progress == null) androidx.compose.material3.CircularProgressIndicator(
        modifier = modifier,
        color = colorPalette.accent,
        strokeCap = strokeCap ?: ProgressIndicatorDefaults.CircularIndeterminateStrokeCap
    ) else androidx.compose.material3.CircularProgressIndicator(
        modifier = modifier,
        color = colorPalette.accent,
        strokeCap = strokeCap ?: ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
        progress = { progress }
    )
}

@Composable
fun LinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.LinearStrokeCap
) {
    val (colorPalette) = LocalAppearance.current

    if (progress == null) androidx.compose.material3.LinearProgressIndicator(
        modifier = modifier,
        color = colorPalette.accent,
        trackColor = colorPalette.background1,
        strokeCap = strokeCap
    ) else androidx.compose.material3.LinearProgressIndicator(
        modifier = modifier,
        color = colorPalette.accent,
        trackColor = colorPalette.background1,
        strokeCap = strokeCap,
        progress = { progress }
    )
}
