package app.vimusic.android.utils

import android.net.http.HttpEngine
import app.vimusic.providers.utils.ProviderHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.android.createHttpEngineAndroidClient

/** Installs the direct UrlRequest HTTP/3 transport for provider Ktor clients. */
fun installHttpEngineKtorClient(httpEngine: HttpEngine) {
    ProviderHttpClient.install { block: HttpClientConfig<*>.() -> Unit ->
        createHttpEngineAndroidClient(httpEngine, block)
    }
}

/** Creates an independent direct HttpEngine Ktor client for callers outside ProviderHttpClient. */
fun newHttpEngineKtorClient(
    httpEngine: HttpEngine,
    block: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = createHttpEngineAndroidClient(httpEngine, block)
