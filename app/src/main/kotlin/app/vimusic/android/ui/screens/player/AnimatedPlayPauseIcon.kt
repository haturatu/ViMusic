package app.vimusic.android.ui.screens.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import app.vimusic.android.R
import app.vimusic.core.ui.LocalAppearance
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionResult
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty

@Composable
fun AnimatedPlayPauseButton(
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current

    val result = rememberLottieComposition(
        spec = LottieCompositionSpec.Asset("lottie/play_pause.json")
    )
    val comp by result
    val progress by comp.animateLottieProgressAsState(
        targetState = playing,
        speed = 2f
    )

    LottieAnimationWithPlaceholder(
        lottieCompositionResult = result,
        progress = progress,
        tint = colorPalette.text,
        placeholder = if (playing) R.drawable.play else R.drawable.pause,
        modifier = modifier
    )
}

@Composable
private fun LottieAnimationWithPlaceholder(
    lottieCompositionResult: LottieCompositionResult,
    progress: Float,
    tint: Color,
    @DrawableRes placeholder: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val colorFilter = remember(tint) {
        BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
            /* color = */ tint.toArgb(),
            /* blendModeCompat = */ BlendModeCompat.SRC_ATOP
        )
    }
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = colorFilter,
            keyPath = arrayOf("**")
        )
    )

    val ready by produceState(initialValue = false) {
        lottieCompositionResult.await()
        value = true
    }

    if (ready) LottieAnimation(
        modifier = modifier,
        composition = lottieCompositionResult.value,
        progress = { progress },
        dynamicProperties = dynamicProperties
    ) else Image(
        modifier = modifier,
        painter = painterResource(placeholder),
        colorFilter = ColorFilter.tint(tint),
        contentDescription = contentDescription
    )
}

@Composable
private fun LottieComposition?.animateLottieProgressAsState(
    targetState: Boolean,
    speed: Float = 1f
): State<Float> {
    val lottieProgress = rememberLottieAnimatable()
    var first by remember { mutableStateOf(true) }

    LaunchedEffect(first) {
        if (!first) return@LaunchedEffect

        lottieProgress.snapTo(progress = if (targetState) 1f else 0f)
        first = false
    }

    LaunchedEffect(targetState) {
        val targetValue = if (targetState) 1f else 0f

        lottieProgress.animate(
            composition = this@animateLottieProgressAsState,
            speed = when {
                lottieProgress.progress < targetValue -> speed
                lottieProgress.progress > targetValue -> -speed
                else -> return@LaunchedEffect
            }
        )
    }

    return lottieProgress
}
