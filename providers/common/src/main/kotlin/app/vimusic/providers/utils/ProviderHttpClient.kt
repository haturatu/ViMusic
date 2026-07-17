package app.vimusic.providers.utils

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.brotli.BrotliInterceptor

/**
 * Shared provider client factory.
 *
 * Providers use OkHttp for every non-HTTP/3 request. OkHttp owns gzip handling and its Brotli
 * interceptor advertises and decodes `br`, so no Ktor-specific content decoder is required.
 */
object ProviderHttpClient {
    fun create(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                addInterceptor(BrotliInterceptor)
            }
        }
        block()
    }
}
