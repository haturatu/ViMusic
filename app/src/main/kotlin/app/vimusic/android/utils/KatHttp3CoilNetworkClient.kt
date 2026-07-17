@file:Suppress("TooGenericExceptionCaught") // Preserve the original native failure across HTTP/3 fallback.

package app.vimusic.android.utils

import android.util.Log
import android.os.SystemClock
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
import dev.kathttp3.KatHttp3Request
import dev.kathttp3.KatHttp3RetryPolicy
import dev.kathttp3.PolicyRetryInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.buffer
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
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
            // at one origin. Bound concurrent streams until kathttp3's native
            // HTTP/3 multiplexing path is fully stable under Coil churn.
            maxActiveStreamsPerOrigin = 8,
            // The response body is currently materialized before Coil starts
            // decoding it. Keep queued image work bounded as well as active
            // streams so many large artwork requests cannot accumulate three
            // copies (native buffer, ByteArray, decoder) at once.
            maxQueuedRequestsPerOrigin = 48,
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
                        maxAttempts = 2,
                        initialBackoffMillis = 250,
                        maxBackoffMillis = 1_000,
                    ),
                ),
            ),
        ),
        applicationContext = applicationContext,
    )

    private val fallbackClient = OkHttpClient.Builder()
        .addInterceptor(BrotliInterceptor)
        .build()

    init {
        applicationContext?.let(YoutubeThumbnailHostResolver::initialize)
    }

    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (NetworkResponse) -> T,
    ): T {
        val resolvedRequest = request.copy(url = YoutubeThumbnailHostResolver.resolve(request.url))
        val startedAt = SystemClock.elapsedRealtime()
        if (!Http3OriginPolicy.shouldAttemptHttp3(resolvedRequest.url)) {
            return try {
                executeRequestWithOkHttpFallback(resolvedRequest, block, null, request.url)
            } catch (_: IncompatibleThumbnailHostException) {
                executeRequestWithOkHttpFallback(request, block, null)
            }
        }
        // PolicyRetryInterceptor retries idempotent requests once for a
        // timeout, DNS failure, or QUIC transport failure. Only idempotent
        // requests may then use the HTTP fallback: retrying a failed POST or
        // PUT could apply the same side effect twice. The fallback still
        // builds their bodies for the Alt-Svc-unavailable path above.
        if (resolvedRequest.method.equals("GET", ignoreCase = true) ||
            resolvedRequest.method.equals("HEAD", ignoreCase = true)
        ) {
            try {
                return executeBufferedRequest(resolvedRequest, block, request.url)
            } catch (failure: Throwable) {
                if (failure is IncompatibleThumbnailHostException) {
                    return executeBufferedRequest(request, block)
                }
                if (!failure.isHttp3TransportFailure()) throw failure
                Http3OriginPolicy.recordHttp3Failure(resolvedRequest.url, failure)
                Log.i(LOG_TAG, "HTTP/3 unavailable; using OkHttp fallback: ${resolvedRequest.url}")
                Log.d(LOG_TAG, "HTTP/3 image fallback cause", failure)
                return executeRequestWithOkHttpFallback(resolvedRequest, block, failure, request.url)
            }
        }
        return executeBufferedRequest(resolvedRequest, block, request.url)
    }

    private suspend fun <T> executeRequestWithOkHttpFallback(
        request: NetworkRequest,
        block: suspend (NetworkResponse) -> T,
        originalFailure: Throwable?,
        originalUrl: String = request.url,
    ): T {
        val fallbackStartedAt = SystemClock.elapsedRealtime()
        val response = try {
            withContext(Dispatchers.IO) {
                val requestMillis = System.currentTimeMillis()
                val method = request.method.uppercase()
                val contentType = request.headers.asMap()
                    .entries
                    .firstOrNull { (name, _) -> name.equals("content-type", ignoreCase = true) }
                    ?.value
                    ?.firstOrNull()
                    ?.toMediaTypeOrNull()
                val requestBody = when (method) {
                    "GET", "HEAD" -> null
                    else -> (request.body?.toByteArray() ?: ByteArray(0)).toRequestBody(contentType)
                }
                val httpRequest = Request.Builder()
                    .url(request.url)
                    .apply {
                        request.headers.asMap().forEach { (name, values) ->
                            values.forEach { value -> addHeader(name, value) }
                        }
                        method(method, requestBody)
                    }
                    .build()
                fallbackClient.newCall(httpRequest).execute().use { httpResponse ->
                    if (YoutubeThumbnailHostResolver.shouldRetryOriginal(
                            originalUrl,
                            request.url,
                            httpResponse.code,
                            httpResponse.header("content-type"),
                            if (httpResponse.body.contentLength() == 0L) 0 else 1,
                        )
                    ) throw IncompatibleThumbnailHostException()
                    YoutubeThumbnailHostResolver.recordResponse(
                        request.url,
                        httpResponse.code,
                        httpResponse.header("content-type"),
                        SystemClock.elapsedRealtime() - fallbackStartedAt,
                    )
                    Http3OriginPolicy.recordOriginResponse(
                        request.url,
                        httpResponse.headers.map { (name, value) -> name to value },
                    )
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
            if (fallbackError !is IncompatibleThumbnailHostException) {
                YoutubeThumbnailHostResolver.recordFailure(
                    request.url,
                    fallbackError,
                    SystemClock.elapsedRealtime() - fallbackStartedAt,
                )
            }
            originalFailure?.let(fallbackError::addSuppressed)
            throw fallbackError
        }
        return block(response)
    }

    private suspend fun <T> executeBufferedRequest(
        request: NetworkRequest,
        block: suspend (NetworkResponse) -> T,
        originalUrl: String = request.url,
    ): T {
        val requestMillis = System.currentTimeMillis()
        val startedAt = SystemClock.elapsedRealtime()
        val response = client.execute(
            KatHttp3Request(
                method = request.method.uppercase(),
                url = request.url,
                headers = sanitizeHttp3Headers(
                    headers = request.headers.asMap().flatMap { (name, values) -> values.map { name to it } },
                ),
                body = request.body?.toByteArray(),
            ),
        )
        Http3OriginPolicy.recordHttp3Response(
            request.url,
            response.status,
            response.headers.map { it.name to it.value },
        )
        YoutubeThumbnailHostResolver.recordResponse(
            request.url,
            response.status,
            response.headers.firstOrNull { it.name.equals("content-type", ignoreCase = true) }?.value,
            SystemClock.elapsedRealtime() - startedAt,
        )
        if (YoutubeThumbnailHostResolver.shouldRetryOriginal(
                originalUrl = originalUrl,
                resolvedUrl = request.url,
                status = response.status,
                contentType = response.headers.firstOrNull { it.name.equals("content-type", ignoreCase = true) }?.value,
                bodySize = response.body.size,
            )
        ) throw IncompatibleThumbnailHostException()
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

private class IncompatibleThumbnailHostException : IOException("Thumbnail host rejected this image")

/** kathttp3 currently uses one HTTP/3 connection per origin; keep its streams bounded. */
@OptIn(coil3.annotation.ExperimentalCoilApi::class)
object KatHttp3CoilConcurrentRequestStrategy : ConcurrentRequestStrategy {
    // NetworkResponseBody is backed by a complete ByteArray until Coil has
    // decoded it. Twelve concurrent requests balances scrolling throughput
    // with peak memory on high-resolution artwork.
    private val permits = Semaphore(12)

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
