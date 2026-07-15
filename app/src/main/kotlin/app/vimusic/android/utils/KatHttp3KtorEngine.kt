package app.vimusic.android.utils

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import app.vimusic.providers.utils.ProviderHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.kathttp3.createKatHttp3Client

private const val KAT_HTTP3_TAG = "KatHttp3Ktor"

/**
 * Installs the kathttp3 HTTP/3 transport for provider Ktor clients on every supported API 26+
 * device. Returns true when installed.
 */
fun installKatHttp3KtorClientIfSupported(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    return runCatching {
        ProviderHttpClient.install { block: HttpClientConfig<*>.() -> Unit ->
            createKatHttp3Client(block = block)
        }
        true
    }.getOrElse { error ->
        Log.w(KAT_HTTP3_TAG, "kathttp3 transport unavailable; retaining default HTTP stack", error)
        false
    }
}

/** Creates an independent kathttp3-backed Ktor client for callers outside ProviderHttpClient. */
@RequiresApi(Build.VERSION_CODES.O)
fun newKatHttp3KtorClient(
    block: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = createKatHttp3Client(block = block)
