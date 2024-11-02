package app.vimusic.android.utils

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.vimusic.core.ui.ColorPalette
import app.vimusic.core.ui.defaultLightPalette
import com.kieronquinn.monetcompat.core.MonetCompat
import kotlinx.coroutines.launch

val LocalMonetCompat = staticCompositionLocalOf { MonetCompat.getInstance() }

context(LifecycleOwner)
inline fun MonetCompat.invokeOnReady(
    state: Lifecycle.State = Lifecycle.State.CREATED,
    crossinline block: () -> Unit
) = lifecycleScope.launch {
    repeatOnLifecycle(state) {
        awaitMonetReady()
        block()
    }
}

fun MonetCompat.setDefaultPalette(palette: ColorPalette = defaultLightPalette) {
    defaultAccentColor = palette.accent.toArgb()
    defaultBackgroundColor = palette.background0.toArgb()
    defaultPrimaryColor = palette.background1.toArgb()
    defaultSecondaryColor = palette.background2.toArgb()
}
