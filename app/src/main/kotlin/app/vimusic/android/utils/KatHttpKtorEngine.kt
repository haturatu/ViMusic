package app.vimusic.android.utils

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import app.vimusic.providers.utils.ProviderHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.kathttp.createKatHttpClient

private const val KAT_HTTP_TAG = "KatHttpKtor"

/**
 * Installs the kathttp HTTP/3 transport for provider Ktor clients on API 26+ devices that lack the
 * platform [android.net.http.HttpEngine] (API 33 and below). Returns true when installed.
 */
fun installKatHttpKtorClientIfSupported(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    return runCatching {
        ProviderHttpClient.install { block: HttpClientConfig<*>.() -> Unit ->
            createKatHttpClient(block = block)
        }
        true
    }.getOrElse { error ->
        Log.w(KAT_HTTP_TAG, "kathttp transport unavailable; retaining default HTTP stack", error)
        false
    }
}

/** Creates an independent kathttp-backed Ktor client for callers outside ProviderHttpClient. */
@RequiresApi(Build.VERSION_CODES.O)
fun newKatHttpKtorClient(
    block: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = createKatHttpClient(block = block)
