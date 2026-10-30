package app.vimusic.providers.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.brotli.BrotliInterceptor
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import kotlin.random.Random

/** GET requests shared by the providers, with one cancellable retry policy. */
class ProviderHttpClient(
    baseUrl: String,
    val json: Json = Json { ignoreUnknownKeys = true },
    private val headers: Map<String, String> = emptyMap(),
    private val client: OkHttpClient = defaultClient,
    private val retryDelayMillis: Long = 1_000,
    private val transport: ProviderHttpTransport? = null,
) {
    private val baseUrl = baseUrl.toHttpUrl()

    init {
        require(retryDelayMillis >= 0)
    }

    suspend inline fun <reified T> getJson(
        path: String,
        noinline query: HttpUrl.Builder.() -> Unit = {},
    ): T = json.decodeFromString(get(path, query = query))

    suspend fun get(
        path: String,
        charset: Charset = Charsets.UTF_8,
        query: HttpUrl.Builder.() -> Unit = {},
    ): String {
        val url = requireNotNull(baseUrl.resolve(path)) { "Invalid provider URL" }
            .newBuilder().apply(query).build()
        val request = Request.Builder().url(url)
            .header("Connection", "close")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "ViMusic (https://github.com/haturatu/ViMusic)")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        var retries = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val response = try {
                execute(request)
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                if (retries == MAX_RETRIES) throw error
                null
            }
            if (response != null && (response.status !in 500..599 || retries == MAX_RETRIES)) {
                if (response.status !in 200..299) throw ProviderHttpException(response.status)
                return response.bodyAsText(charset)
            }
            retries++
            waitForRetry(response?.headers?.get("Retry-After"))
        }
    }

    private suspend fun execute(request: Request): ProviderHttpResponse {
        val selectedTransport = transport ?: defaultTransport
        val timeout = client.callTimeoutMillis.toLong()
        return if (timeout == 0L) {
            selectedTransport.execute(client, request)
        } else {
            // The budget covers HTTP/3 and its HTTP/2 fallback together.
            withTimeoutOrNull(timeout) { selectedTransport.execute(client, request) }
                ?: throw SocketTimeoutException("Provider request timed out")
        }
    }

    private suspend fun waitForRetry(retryAfter: String? = null) {
        val serverDelay = retryAfter?.toLongOrNull()
            ?.coerceIn(0, Long.MAX_VALUE / 1_000)?.times(1_000) ?: 0
        val jitter = if (retryDelayMillis == 0L) 0 else Random.nextLong(retryDelayMillis)
        delay(maxOf(retryDelayMillis + jitter, serverDelay))
    }

    companion object {
        private const val MAX_RETRIES = 3

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(BrotliInterceptor)
            .retryOnConnectionFailure(false)
            .build()

        /** The Android HTTP/3 flavor installs its native transport at application startup. */
        @Volatile
        var defaultTransport: ProviderHttpTransport = OkHttpProviderTransport
    }
}

class ProviderHttpException(val status: Int) : IOException("Provider returned HTTP $status")
