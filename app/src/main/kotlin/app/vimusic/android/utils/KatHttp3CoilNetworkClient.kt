@file:Suppress("TooGenericExceptionCaught") // Preserve the original native failure across HTTP/3 fallback.

package app.vimusic.android.utils

import android.util.Log
import coil3.network.NetworkClient
import coil3.network.ConcurrentRequestStrategy
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkRequestBody
import coil3.network.NetworkResponse
import coil3.network.NetworkResponseBody
import coil3.fetch.FetchResult
import dev.kathttp3.KatHttp3Client
import dev.kathttp3.KatHttp3ClientConfig
import dev.kathttp3.KatHttp3Exception
import dev.kathttp3.KatHttp3Header
import dev.kathttp3.KatHttp3Request
import dev.kathttp3.KatHttp3RetryPolicy
import dev.kathttp3.PolicyRetryInterceptor
import dev.kathttp3.QuicTransportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.buffer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.brotli.BrotliInterceptor

/**
 * Coil network client backed directly by kathttp3's HTTP/3 API.
 *
 * The full response is obtained before invoking Coil's response block. Coil
 * reads [NetworkResponseBody] after that block has already returned, so
 * forwarding kathttp3's event stream would let a read timeout escape after
 * this client can retry or fall back. Buffering here keeps retry/fallback at
 * the actual network boundary and gives Coil a completed body to decode.
 */
class KatHttp3CoilNetworkClient(
    applicationContext: android.content.Context? = null,
) : NetworkClient {
    private companion object {
        const val LOG_TAG = "KatHttp3Coil"
    }


    private val client = KatHttp3Client(
        config = KatHttp3ClientConfig(
            // YouTube's image CDNs expose a large number of small resources
            // at one origin. Serialize each origin until kathttp3's native
            // HTTP/3 multiplexing path is fully stable under Coil churn.
            maxActiveStreamsPerOrigin = 8, // 8
            maxQueuedRequestsPerOrigin = 128, //128
            queueTimeoutMillis = 120_000,
            responseHeadersTimeoutMillis = 45_000, // 45_00
            readTimeoutMillis = 90_000, // 90_000
            callTimeoutMillis = 120_000,
            enable0Rtt = true,
            qlogEnabled = false,
            insecureCert = false,
            // This is the sole HTTP/3 retry policy. Keep its two total
            // attempts aligned with the previous Coil-specific behaviour.
            interceptors = listOf(
                PolicyRetryInterceptor(
                    KatHttp3RetryPolicy(
                        maxAttempts = 5, // 2
                        initialBackoffMillis = 300, // 250
                        maxBackoffMillis = 5_00, // 1_000
                    ),
                ),
            ),
        ),
        applicationContext = applicationContext,
    )

    private val fallbackClient = OkHttpClient.Builder()
        .addInterceptor(BrotliInterceptor)
        .build()

    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (NetworkResponse) -> T,
    ): T {
        // PolicyRetryInterceptor retries idempotent requests once for a
        // timeout, DNS failure, or QUIC transport failure. Once its attempt
        // budget is exhausted, retain HTTPS as the availability fallback.
        if (request.method.equals("GET", ignoreCase = true) ||
            request.method.equals("HEAD", ignoreCase = true)
        ) {
            try {
                return executeBufferedRequest(request, block)
            } catch (failure: Throwable) {
                if (!failure.isKatHttp3ConnectivityFailure()) throw failure
                Log.w(LOG_TAG, "HTTP/3 retry budget exhausted; using OkHttp fallback: ${request.url}", failure)
                return executeRequestWithOkHttpFallback(request, block, failure)
            }
        }
        return executeBufferedRequest(request, block)
    }

    private suspend fun <T> executeRequestWithOkHttpFallback(
        request: NetworkRequest,
        block: suspend (NetworkResponse) -> T,
        originalFailure: Throwable,
    ): T {
        val response = try {
            withContext(Dispatchers.IO) {
                val requestMillis = System.currentTimeMillis()
                val httpRequest = Request.Builder()
                    .url(request.url)
                    .apply {
                        request.headers.asMap().forEach { (name, values) ->
                            values.forEach { value -> addHeader(name, value) }
                        }
                        method(request.method.uppercase(), null)
                    }
                    .build()
                fallbackClient.newCall(httpRequest).execute().use { httpResponse ->
                    NetworkResponse(
                        code = httpResponse.code,
                        requestMillis = requestMillis,
                        responseMillis = System.currentTimeMillis(),
                        headers = NetworkHeaders.Builder().apply {
                            httpResponse.headers.forEach { (name, value) -> add(name, value) }
                        }.build(),
                        body = ByteArrayNetworkResponseBody(httpResponse.body.bytes()),
                    ).also {
                        Log.i(LOG_TAG, "OkHttp ${httpResponse.protocol}: ${httpResponse.code} ${request.url}")
                    }
                }
            }
        } catch (fallbackError: Throwable) {
            fallbackError.addSuppressed(originalFailure)
            throw fallbackError
        }
        return block(response)
    }

    private suspend fun <T> executeBufferedRequest(
        request: NetworkRequest,
        block: suspend (NetworkResponse) -> T,
    ): T {
        val requestMillis = System.currentTimeMillis()
        val response = client.execute(
            KatHttp3Request(
                method = request.method.uppercase(),
                url = request.url,
                headers = request.headers.toKatHttp3Headers(),
                body = request.body?.toByteArray(),
            ),
        )
        Log.i(LOG_TAG, "${response.protocol.uppercase()}: ${response.status} ${request.url}")
        return block(
            NetworkResponse(
                code = response.status,
                requestMillis = requestMillis,
                responseMillis = System.currentTimeMillis(),
                headers = NetworkHeaders.Builder().apply {
                    response.headers.forEach { add(it.name, it.value) }
                }.build(),
                body = ByteArrayNetworkResponseBody(response.body),
            ),
        )
    }

    private suspend fun NetworkRequestBody.toByteArray(): ByteArray = Buffer().use { buffer ->
        writeTo(buffer)
        buffer.readByteArray()
    }
}

private fun Throwable.isKatHttp3ConnectivityFailure(): Boolean =
    this is KatHttp3Exception.Timeout ||
        this is KatHttp3Exception.Dns ||
        this is QuicTransportException

/** kathttp3 currently uses one HTTP/3 connection per origin; keep its streams bounded. */
object KatHttp3CoilConcurrentRequestStrategy : ConcurrentRequestStrategy {
    private val permits = Semaphore(32)

    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    override suspend fun apply(
        key: String,
        block: suspend () -> FetchResult,
    ): FetchResult = permits.withPermit { block() }
}

private class ByteArrayNetworkResponseBody(
    private val bytes: ByteArray,
) : NetworkResponseBody {
    override suspend fun writeTo(sink: BufferedSink) {
        sink.write(bytes)
    }

    override suspend fun writeTo(fileSystem: FileSystem, path: Path) {
        fileSystem.sink(path).buffer().use { it.write(bytes) }
    }

    override fun close() = Unit
}

private fun NetworkHeaders.toKatHttp3Headers(): List<KatHttp3Header> = buildList {
    asMap().forEach { (name, values) ->
        val normalized = name.lowercase()
        if (normalized.any { it <= ' ' || it == ':' }) return@forEach
        values.forEach { value ->
            if (value.none { it == '\r' || it == '\n' }) add(KatHttp3Header(normalized, value))
        }
    }
}
