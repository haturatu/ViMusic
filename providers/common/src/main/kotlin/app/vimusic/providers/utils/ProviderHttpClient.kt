package app.vimusic.providers.utils

import kotlinx.coroutines.Mutex
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.brotli.BrotliInterceptor
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/** GET requests shared by the providers, with one cancellable retry policy. */
class ProviderHttpClient(
    baseUrl: String,
    val json: Json = Json { ignoreUnknownKeys = true },
    private val headers: Map<String, String> = emptyMap(),
    private val client: OkHttpClient = defaultClient,
    private val retryDelayMillis: Long = 1_000,
    private val transport: ProviderHttpTransport? = null,
    private val rateLimiter: TokenBucketRateLimiter? = null,
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

            // Acquire rate limiter permit before making request
            rateLimiter?.acquire()

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

        // Certificate pinning for YouTube/Google domains
        private val certificatePinner = CertificatePinner.Builder()
            // youtubei.googleapis.com (Innertube API)
            .add("youtubei.googleapis.com", "sha256/7DkBjzHJ8RWvOJqOEzg8hJdQ7bFo5hPZqO8UO8eIaDg=")
            .add("youtubei.googleapis.com", "sha256/lCppFqbkrlJ3EcVFAkeIP0+q1Vdci2tmWUBly6r8z4c=")
            .add("youtubei.googleapis.com", "sha256/RoZ1k3BiAeqR7WwO4VF9HxNdv9X1E57kZdP4Jd0pEVg=")
            // www.youtube.com (for JS challenge fetching)
            .add("www.youtube.com", "sha256/7DkBjzHJ8RWvOJqOEzg8hJdQ7bFo5hPZqO8UO8eIaDg=")
            .add("www.youtube.com", "sha256/lCppFqbkrlJ3EcVFAkeIP0+q1Vdci2tmWUBly6r8z4c=")
            .add("www.youtube.com", "sha256/RoZ1k3BiAeqR7WwO4VF9HxNdv9X1E57kZdP4Jd0pEVg=")
            // music.youtube.com (base URL for Innertube client)
            .add("music.youtube.com", "sha256/7DkBjzHJ8RWvOJqOEzg8hJdQ7bFo5hPZqO8UO8eIaDg=")
            .add("music.youtube.com", "sha256/lCppFqbkrlJ3EcVFAkeIP0+q1Vdci2tmWUBly6r8z4c=")
            .add("music.youtube.com", "sha256/RoZ1k3BiAeqR7WwO4VF9HxNdv9X1E57kZdP4Jd0pEVg=")
            // i.ytimg.com (thumbnails)
            .add("i.ytimg.com", "sha256/7DkBjzHJ8RWvOJqOEzg8hJdQ7bFo5hPZqO8UO8eIaDg=")
            .add("i.ytimg.com", "sha256/lCppFqbkrlJ3EcVFAkeIP0+q1Vdci2tmWUBly6r8z4c=")
            .add("i.ytimg.com", "sha256/RoZ1k3BiAeqR7WwO4VF9HxNdv9X1E57kZdP4Jd0pEVg=")
            .build()

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(BrotliInterceptor)
            .retryOnConnectionFailure(false)
            .certificatePinner(certificatePinner)
            .build()

        /** The Android HTTP/3 flavor installs its native transport at application startup. */
        @Volatile
        var defaultTransport: ProviderHttpTransport = OkHttpProviderTransport
    }
}

/**
 * Token bucket rate limiter for controlling request rate to avoid triggering
 * YouTube API abuse detection.
 *
 * Allows burst of [burstSize] requests, then sustains [refillRatePerSecond] requests/second.
 */
class TokenBucketRateLimiter(
    private val refillRatePerSecond: Double = 2.0,  // Sustained 2 req/s (conservative for YouTube)
    private val burstSize: Int = 10,                  // Allow burst of 10 requests
) {
    private var tokens = burstSize.toDouble()
    private var lastRefill = System.nanoTime()
    private val mutex = Mutex()

    /**
     * Acquire a token, suspending until one is available.
     */
    suspend fun acquire() {
        mutex.withLock {
            refill()
            if (tokens >= 1.0) {
                tokens -= 1.0
                return@withLock
            }

            // Calculate wait time for next token
            val tokensNeeded = 1.0 - tokens
            val waitNanos = (tokensNeeded / refillRatePerSecond * 1_000_000_000).toLong()
            tokens = 0.0
        }

        // Wait outside the lock to avoid blocking other coroutines
        delay(waitNanos.milliseconds)

        // Re-acquire lock and consume the token
        mutex.withLock {
            refill()
            tokens -= 1.0
        }
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedSeconds = (now - lastRefill) / 1_000_000_000.0
        tokens = (tokens + elapsedSeconds * refillRatePerSecond).coerceAtMost(burstSize.toDouble())
        lastRefill = now
    }
}

class ProviderHttpException(val status: Int) : IOException("Provider returned HTTP $status")