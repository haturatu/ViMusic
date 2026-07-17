package app.vimusic.android.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.collectAsState

private val retryDelaysMs = longArrayOf(1_000L, 2_000L, 4_000L)

/**
 * Retries a currently visible failed image after short backoff delays and when
 * Android reports that a validated network has returned. Successful images
 * still come from Coil's memory/disk cache on a network-generation change.
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
    val context = LocalContext.current
    val networkGeneration by NetworkRetrySignal.generations(context).collectAsState()
    var retryKey by remember(model) { mutableIntStateOf(0) }
    var retryCount by remember(model) { mutableIntStateOf(0) }
    var errorGeneration by remember(model) { mutableIntStateOf(0) }
    var failed by remember(model) { mutableStateOf(false) }

    // A real network recovery revives requests that exhausted their local
    // backoff while the device was offline.
    LaunchedEffect(networkGeneration) {
        if (failed) {
            retryCount = 0
            retryKey++
        }
    }

    // Run only after an actual Coil error. Retrying changes the composition
    // key, creating a fresh painter/request for the same image URL.
    LaunchedEffect(errorGeneration, networkGeneration) {
        if (!failed || errorGeneration == 0) return@LaunchedEffect
        val delayMs = retryDelaysMs.getOrNull(retryCount) ?: return@LaunchedEffect
        delay(delayMs)
        if (failed) {
            retryCount++
            retryKey++
        }
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
                failed = false
                retryCount = 0
            },
            onError = {
                failed = true
                errorGeneration++
            },
        )
    }
}

private object NetworkRetrySignal {
    private val lock = Any()
    private var started = false
    private var online = false
    private val _generation = MutableStateFlow(0L)

    fun generations(context: Context): StateFlow<Long> {
        ensureStarted(context.applicationContext)
        return _generation.asStateFlow()
    }

    private fun ensureStarted(context: Context) = synchronized(lock) {
        if (started) return
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        online = connectivityManager.hasValidatedNetwork()
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = update(connectivityManager)
                override fun onLost(network: Network) = update(connectivityManager)
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                    update(connectivityManager)
            },
        )
        started = true
    }

    private fun update(connectivityManager: ConnectivityManager) = synchronized(lock) {
        val nowOnline = connectivityManager.hasValidatedNetwork()
        if (nowOnline && !online) _generation.value++
        online = nowOnline
    }

    private fun ConnectivityManager.hasValidatedNetwork(): Boolean =
        activeNetwork?.let(::getNetworkCapabilities)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
}
