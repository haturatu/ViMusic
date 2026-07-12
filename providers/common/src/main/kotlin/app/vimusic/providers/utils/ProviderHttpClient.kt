package app.vimusic.providers.utils

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO

/**
 * Shared provider client factory. Android installs its HttpEngine-backed implementation during
 * application startup; command-line and older Android callers retain the CIO fallback.
 */
object ProviderHttpClient {
    @Volatile
    private var factory: ((HttpClientConfig<*>.() -> Unit) -> HttpClient)? = null

    fun install(factory: (HttpClientConfig<*>.() -> Unit) -> HttpClient) {
        this.factory = factory
    }

    fun create(block: HttpClientConfig<*>.() -> Unit): HttpClient =
        factory?.invoke(block) ?: HttpClient(CIO, block)
}
