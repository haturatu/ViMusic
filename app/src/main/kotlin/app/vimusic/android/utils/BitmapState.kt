package app.vimusic.android.utils

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.vimusic.android.service.PlayerService

@Composable
fun PlayerService.Binder?.collectProvidedBitmapAsState(
    key: Any = Unit
): Bitmap? {
    var state by remember(this, key) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(this, key) {
        this@collectProvidedBitmapAsState?.setBitmapListener {
            state = it
        }
    }

    return state
}
