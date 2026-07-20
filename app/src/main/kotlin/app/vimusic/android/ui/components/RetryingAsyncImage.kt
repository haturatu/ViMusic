package app.vimusic.android.ui.components

import android.net.Uri
import app.vimusic.android.utils.isHttp3TransportFailure
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.network.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException

private val retryDelaysMs = longArrayOf(500L, 1_500L)

/**
 * Retries only transient network failures for an image that remains visible.
 *
 * Coil serves memory and disk cache hits as successes, so they never enter the error callback or
 * incur a retry delay. Cancellations are similarly intentional (for example, a LazyColumn item
 * leaving the viewport) and must not be turned into extra network work.
 */
@Composable
fun RetryingAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    error: Painter? = null,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = FilterQuality.Low,
) {
    if (model.isLocalImageSource()) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            error = error,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = filterQuality,
        )
        return
    }

    var retryKey by remember(model) { mutableIntStateOf(0) }
    var retryAttempt by remember(model) { mutableIntStateOf(0) }
    var retryGeneration by remember(model) { mutableIntStateOf(0) }

    LaunchedEffect(model, retryGeneration) {
        if (retryGeneration == 0) return@LaunchedEffect
        val delayMs = retryDelaysMs.getOrNull(retryAttempt - 1) ?: return@LaunchedEffect
        delay(delayMs)
        retryKey++
    }

    key(retryKey) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            error = error,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = filterQuality,
            onSuccess = {
                retryAttempt = 0
                retryGeneration = 0
            },
            onError = { result ->
                if (
                    result.result.throwable.isTransientImageFailure() &&
                    retryAttempt < retryDelaysMs.size
                ) {
                    retryAttempt++
                    retryGeneration++
                }
            },
        )
    }
}

private fun Any?.isLocalImageSource(): Boolean = when (this) {
    is Uri -> scheme in LOCAL_URI_SCHEMES
    is String -> LOCAL_URI_SCHEMES.any { startsWith("$it://", ignoreCase = true) }
    else -> false
}

private fun Throwable.isTransientImageFailure(): Boolean {
    if (this is CancellationException) return false
    if (isHttp3TransportFailure()) return true

    return generateSequence(this) { it.cause }.any { error ->
        when (error) {
            is CancellationException -> false
            is HttpException -> error.response.code in 500..599
            is IOException -> true
            else -> false
        }
    }
}

private val LOCAL_URI_SCHEMES = setOf("content", "file", "android.resource")
