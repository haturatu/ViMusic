package app.vimusic.android.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import app.vimusic.compose.persist.findActivityNullable

@Composable
fun FullScreenState(
    shown: Boolean,
    type: Int = WindowInsetsCompat.Type.systemBars()
) {
    val view = LocalView.current

    DisposableEffect(view, shown, type) {
        val window = view.context.findActivityNullable()?.window
            ?: return@DisposableEffect onDispose { }
        val insetsController = WindowCompat.getInsetsController(window, view)

        if (shown) insetsController.show(type) else insetsController.hide(type)

        onDispose {
            insetsController.show(type)
        }
    }
}
