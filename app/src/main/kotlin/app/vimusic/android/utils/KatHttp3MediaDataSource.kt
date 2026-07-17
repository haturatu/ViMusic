@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:Suppress("TooGenericExceptionCaught") // HTTP/3 callbacks surface arbitrary native failures.

package app.vimusic.android.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import dev.kathttp3.KatHttp3Call
import dev.kathttp3.KatHttp3Client
import dev.kathttp3.KatHttp3ClientConfig
import dev.kathttp3.KatHttp3Header
import dev.kathttp3.KatHttp3Request
import dev.kathttp3.KatHttp3StreamEvent
import dev.kathttp3.PlatformDnsResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Media3 source backed directly by kathttp3's ordered streaming API.
 *
 * Do not build the complete response before giving it to Media3: progressive
 * playback needs the requested range while it is arriving. `executeStreaming`
 * owns the native-to-Kotlin FIFO and its byte budget; this class only provides
 * the blocking DataSource boundary required by Media3.
 */
class KatHttp3MediaDataSource(
    private val clientFactory: () -> KatHttp3Client,
    private val clientInvalidator: ((KatHttp3Client) -> Unit)? = null,
    private val clientReleaser: (KatHttp3Client) -> Unit = {},
    private val fallbackFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory(),
) : BaseDataSource(true), HttpDataSource {
    private val sourceId = NEXT_SOURCE_ID.incrementAndGet()
    private val requestProperties = LinkedHashMap<String, String>()
    private var dataSpec: DataSpec? = null
    private var call: KatHttp3Call? = null
    private var collector: Job? = null
    private var activeAttempt: H3Attempt? = null
    @Volatile private var stream: StreamState? = null
    private var currentChunk: ByteArray? = null
    private var currentChunkOffset = 0
    private var fallback: HttpDataSource? = null
    private var responseCode = -1
    private var responseHeaders: Map<String, List<String>> = emptyMap()
    private var responseUri: Uri? = null
    private var opened = false
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private val h3Active = AtomicBoolean(false)

    /**
     * The factory deliberately returns an application-scoped client. A Media3
     * DataSource is short lived (usually one cache/range read), whereas a QUIC
     * connection must survive those reads to retain its congestion state and
     * make session resumption/0-RTT useful.
     */
    private fun client(): KatHttp3Client = clientFactory()

    override fun setRequestProperty(name: String, value: String) { requestProperties[name] = value }
    override fun clearRequestProperty(name: String) { requestProperties.remove(name) }
    override fun clearAllRequestProperties() { requestProperties.clear() }
    override fun getResponseCode(): Int = fallback?.responseCode ?: responseCode
    override fun getResponseHeaders(): Map<String, List<String>> = fallback?.responseHeaders ?: responseHeaders
    override fun getUri(): Uri? = fallback?.uri ?: responseUri

    @Suppress("CyclomaticComplexMethod") // Attempts must be adopted/cancelled atomically for Media3.
    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)
        responseUri = dataSpec.uri
        try {
            Log.d(
                TAG,
                "source=$sourceId opening HTTP/3 method=${dataSpec.httpMethodString} " +
                    "position=${dataSpec.position} length=${dataSpec.length}: ${dataSpec.uri}",
            )
            if (!Http3OriginPolicy.shouldAttemptHttp3(dataSpec.uri.toString())) {
                return openFallback(dataSpec, null)
            }
            var lastError: IOException? = null
            repeat(H3_OPEN_ATTEMPTS) { attempt ->
                // Do not publish an attempt to the DataSource until both its
                // headers and first body chunk have arrived. A watchdog-cancelled
                // attempt may otherwise resume later and overwrite the stream
                // belonging to a newer playback request.
                val h3Attempt = H3Attempt(StreamState(), clientReleaser)
                val completion = CompletableDeferred<H3Attempt>()
                h3Attempt.openJob = STREAM_SCOPE.launch {
                    try {
                        val activeClient = client()
                        h3Attempt.client = activeClient
                        val activeCall = activeClient.executeStreaming(
                            KatHttp3Request(
                                method = dataSpec.httpMethodString,
                                url = dataSpec.uri.toString(),
                                headers = requestHeaders(dataSpec),
                                body = dataSpec.httpBody,
                            ),
                        )
                        h3Attempt.call = activeCall
                        h3Attempt.collector = STREAM_SCOPE.launch {
                            try {
                                activeCall.events.collect { event ->
                                    when (event) {
                                        is KatHttp3StreamEvent.Headers -> h3Attempt.state.onHeaders(event.status, event.headers)
                                        is KatHttp3StreamEvent.Body -> h3Attempt.state.onBody(event.bytes)
                                    }
                                }
                                h3Attempt.state.onComplete()
                            } catch (error: Throwable) {
                                if (!h3Attempt.state.isClosed) {
                                    h3Attempt.state.onFailure(IOException("HTTP/3 media stream failed", error))
                                }
                            } finally {
                                // The request is terminal. The shared client remains
                                // alive for later Media3 range requests.
                                if (stream === h3Attempt.state) {
                                    releaseH3Transport("terminal")
                                }
                                h3Attempt.releaseClient()
                                if (activeAttempt === h3Attempt) activeAttempt = null
                            }
                        }
                        val headers = h3Attempt.state.awaitHeaders(HEADERS_WAIT_MILLIS)
                        h3Attempt.headers = headers
                        h3Attempt.responseError = headers?.let { validateRangeResponse(dataSpec, it) }
                        h3Attempt.hasFirstBody = headers != null && h3Attempt.responseError == null &&
                            (!requiresFirstBody(dataSpec, headers) ||
                                h3Attempt.state.awaitFirstBody(FIRST_BODY_WAIT_MILLIS))
                        completion.complete(h3Attempt)
                    } catch (error: Throwable) {
                        completion.completeExceptionally(error)
                    }
                }

                val result = try {
                    runBlocking {
                        withTimeout(H3_ATTEMPT_DEADLINE_MILLIS) { completion.await() }
                    }
                } catch (error: Exception) {
                    lastError = IOException("HTTP/3 media open watchdog timed out", error)
                    h3Attempt.cancel()
                    null
                }
                val headers = result?.headers
                if (headers != null && result.responseError == null && result.hasFirstBody) {
                    // This is now the sole live HTTP/3 attempt owned by this
                    // DataSource. Older attempts were cancelled before retry.
                    stream = h3Attempt.state
                    call = h3Attempt.call
                    collector = h3Attempt.collector
                    activeAttempt = h3Attempt
                    responseCode = headers.status
                    responseHeaders = headers.headers.groupBy(KatHttp3Header::name, KatHttp3Header::value)
                    Http3OriginPolicy.recordHttp3Response(
                        dataSpec.uri.toString(),
                        headers.status,
                        headers.headers.map { it.name to it.value },
                    )
                    bytesRemaining = requestedLength(dataSpec)
                    opened = true
                    transferStarted(dataSpec)
                    markH3TransportActive()
                    // A short response can become terminal between the first-body
                    // gate and adoption. Cover that race explicitly.
                    if (h3Attempt.state.isTerminal) {
                        releaseH3Transport("terminal-before-adoption")
                    }
                    Log.i(
                        TAG,
                            "source=$sourceId H3 media stream opened status=${headers.status} " +
                            "attempt=${attempt + 1} active=${ACTIVE_H3_SOURCES.get()}: ${dataSpec.uri}",
                    )
                    return contentLength(dataSpec)
                }

                lastError = h3Attempt.responseError ?: h3Attempt.state.failure ?: lastError ?: when {
                    headers == null -> IOException("HTTP/3 headers timed out")
                    headers.status !in 200..299 -> IOException("HTTP/3 returned ${headers.status}")
                    else -> IOException("HTTP/3 response body timed out")
                }
                h3Attempt.cancel()
                if (attempt + 1 < H3_OPEN_ATTEMPTS) {
                    if (lastError.isHttp3TransportFailure()) {
                        // Retire only a connection-level failure. Existing
                        // streams retain their lease and drain before its
                        // native client is closed; new requests use a fresh
                        // generation.
                        clientInvalidator?.let { invalidator -> h3Attempt.client?.let(invalidator) }
                        Log.w(TAG, "HTTP/3 media open attempt ${attempt + 1} failed; retrying with a fresh client", lastError)
                    } else {
                        Log.w(TAG, "HTTP/3 media open attempt ${attempt + 1} failed; retrying", lastError)
                    }
                    Thread.sleep(h3OpenRetryBackoffMillis(attempt))
                }
            }
            val failure = checkNotNull(lastError) {
                "HTTP/3 media open attempts exhausted without an error"
            }
            Http3OriginPolicy.recordHttp3Failure(dataSpec.uri.toString(), failure)
            return openFallback(
                dataSpec,
                failure,
            )
        } catch (error: Throwable) {
            stopStreaming()
            val ioError = error as? IOException ?: IOException("Unable to open HTTP/3 media stream", error)
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                ioError,
                dataSpec,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN,
            )
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (fallback != null) return fallback!!.read(buffer, offset, length)
        if (bytesRemaining == 0L) {
            stopStreaming()
            return C.RESULT_END_OF_INPUT
        }
        val spec = dataSpec ?: throw IOException("DataSource is not open")
        val state = stream ?: return C.RESULT_END_OF_INPUT
        while (true) {
            if (currentChunk != null && currentChunkOffset < currentChunk!!.size) {
                val count = readCurrentChunk(buffer, offset, length)
                if (currentChunkOffset == currentChunk!!.size) {
                    currentChunk = null
                    currentChunkOffset = 0
                }
                return count
            }
            when (val next = state.take()) {
                is StreamItem.Body -> {
                    currentChunk = next.bytes
                    currentChunkOffset = 0
                }
                StreamItem.End -> {
                    if (bytesRemaining > 0L) {
                        throw HttpDataSource.HttpDataSourceException.createForIOException(
                            IOException("HTTP/3 response ended with $bytesRemaining bytes remaining"),
                            spec,
                            HttpDataSource.HttpDataSourceException.TYPE_READ,
                        )
                    }
                    return C.RESULT_END_OF_INPUT
                }
                is StreamItem.Failure -> throw HttpDataSource.HttpDataSourceException.createForIOException(
                    next.error,
                    spec,
                    HttpDataSource.HttpDataSourceException.TYPE_READ,
                )
            }
        }
    }

    private fun readCurrentChunk(buffer: ByteArray, offset: Int, length: Int): Int {
        val chunk = currentChunk ?: return C.RESULT_END_OF_INPUT
        val requestedLength = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        val count = minOf(requestedLength, chunk.size - currentChunkOffset)
        chunk.copyInto(buffer, offset, currentChunkOffset, currentChunkOffset + count)
        currentChunkOffset += count
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= count
        bytesTransferred(count)
        return count
    }

    override fun close() {
        Log.d(
            TAG,
            "source=$sourceId close h3=${stream != null} fallback=${fallback != null}",
        )
        var closeError: Throwable? = null
        try {
            stopStreaming()
            fallback?.close()
        } catch (error: Throwable) {
            closeError = error
        } finally {
            fallback = null
            responseCode = -1
            responseHeaders = emptyMap()
            responseUri = null
            dataSpec = null
            bytesRemaining = C.LENGTH_UNSET.toLong()
            currentChunk = null
            currentChunkOffset = 0
            if (opened) {
                opened = false
                transferEnded()
            }
        }
        closeError?.let { error ->
            throw error as? IOException ?: IOException("Failed to close data source", error)
        }
    }

    private fun openFallback(dataSpec: DataSpec, error: IOException?): Long {
        if (error != null) {
            Log.i(TAG, "HTTP/3 media unavailable; using standard HTTP fallback: ${dataSpec.uri}")
            Log.d(TAG, "HTTP/3 media fallback cause", error)
        }
        stopStreaming()
        val source = fallbackFactory.createDataSource().also { fallback ->
            requestProperties.forEach(fallback::setRequestProperty)
        }
        return try {
            val length = source.open(dataSpec)
            Http3OriginPolicy.recordOriginResponse(
                dataSpec.uri.toString(),
                source.responseHeaders.flatMap { (name, values) -> values.map { name to it } },
            )
            fallback = source
            opened = true
            transferStarted(dataSpec)
            length
        } catch (failure: Throwable) {
            // Do not retain a half-open fallback source. Media3 may reuse this
            // DataSource after the open error, and an unclosed source can keep
            // a socket and its transfer listener alive.
            runCatching { source.close() }
            throw failure
        }
    }

    private fun stopStreaming() {
        // Mark the state closed before cancelling. This makes a native -10 / Flow
        // completion a normal close rather than an application-level failure.
        stream?.close()
        collector?.cancel()
        collector = null
        runCatching { call?.cancel() }
        call = null
        activeAttempt?.releaseClient()
        activeAttempt = null
        stream = null
        releaseH3Transport("close")
        currentChunk = null
        currentChunkOffset = 0
        bytesRemaining = C.LENGTH_UNSET.toLong()
    }

    private fun markH3TransportActive() {
        check(h3Active.compareAndSet(false, true)) { "HTTP/3 transport already active for source=$sourceId" }
        ACTIVE_H3_SOURCES.incrementAndGet()
    }

    private fun releaseH3Transport(reason: String) {
        if (!h3Active.compareAndSet(true, false)) return
        Log.d(
            TAG,
            "source=$sourceId released HTTP/3 transport reason=$reason " +
                "active=${ACTIVE_H3_SOURCES.decrementAndGet()}",
        )
    }

    private fun h3OpenRetryBackoffMillis(failedAttemptIndex: Int): Long =
        (H3_OPEN_INITIAL_RETRY_BACKOFF_MILLIS shl failedAttemptIndex)
            .coerceAtMost(H3_OPEN_MAX_RETRY_BACKOFF_MILLIS)

    /**
     * A non-zero Media3 position is only valid when the response begins at
     * exactly that byte. Googlevideo honours a standard Range header for both
     * GET and its Android-client POST playback requests; accepting a 200 here
     * would silently feed file byte zero to a later cache/playback position.
     */
    private fun validateRangeResponse(dataSpec: DataSpec, headers: Headers): IOException? {
        if (headers.status !in 200..299) return IOException("HTTP/3 returned ${headers.status}")
        val contentRange = if (headers.status == HTTP_PARTIAL_CONTENT) {
            headers.headers
                .firstOrNull { it.name.equals(CONTENT_RANGE_HEADER, ignoreCase = true) }
                ?.value
                ?: return IOException("HTTP/3 206 response has no Content-Range")
        } else {
            null
        }
        if (headers.status == HTTP_PARTIAL_CONTENT) {
            val responseStart = CONTENT_RANGE_PATTERN.matchEntire(checkNotNull(contentRange))
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
                ?: return IOException("Invalid HTTP/3 Content-Range: $contentRange")
            return if (responseStart == dataSpec.position) {
                null
            } else {
                IOException(
                    "HTTP/3 Content-Range starts at $responseStart, expected ${dataSpec.position}",
                )
            }
        }
        if (dataSpec.position != 0L) {
            return IOException(
                "HTTP/3 ignored Range position=${dataSpec.position}: status=${headers.status}",
            )
        }
        return null
    }

    private fun requestHeaders(dataSpec: DataSpec): List<KatHttp3Header> = sanitizeHttp3Headers(
        headers = (requestProperties + dataSpec.httpRequestHeaders).asSequence().map { it.key to it.value }.asIterable(),
        defaultAcceptEncoding = "identity",
        contentLength = dataSpec.httpBody?.size?.toLong(),
    ).toMutableList().apply {
        if ((dataSpec.position > 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) &&
            none { it.name == "range" }
        ) {
            val rangeEnd = dataSpec.length
                .takeIf { it != C.LENGTH_UNSET.toLong() }
                ?.let { dataSpec.position + it - 1 }
            add(KatHttp3Header("range", "bytes=${dataSpec.position}-${rangeEnd ?: ""}"))
        }
    }

    private fun contentLength(dataSpec: DataSpec): Long {
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) return dataSpec.length
        return responseHeaders.entries.firstOrNull { it.key.equals("content-length", ignoreCase = true) }
            ?.value?.firstOrNull()?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
    }

    private fun requestedLength(dataSpec: DataSpec): Long =
        dataSpec.length.takeIf { it != C.LENGTH_UNSET.toLong() } ?: contentLength(dataSpec)

    private fun requiresFirstBody(dataSpec: DataSpec, headers: Headers): Boolean {
        if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_HEAD || dataSpec.length == 0L) return false
        if (headers.status == HTTP_NO_CONTENT || headers.status == HTTP_NOT_MODIFIED) return false
        return headers.headers
            .firstOrNull { it.name.equals(CONTENT_LENGTH_HEADER, ignoreCase = true) }
            ?.value
            ?.toLongOrNull() != 0L
    }

    class Factory(context: Context) : HttpDataSource.Factory {
        private val applicationContext = context.applicationContext
        private var defaultRequestProperties: Map<String, String> = emptyMap()

        override fun createDataSource(): HttpDataSource = KatHttp3MediaDataSource(
            clientFactory = { KatHttp3PlaybackClient.acquire(applicationContext) },
            clientInvalidator = KatHttp3PlaybackClient::invalidate,
            clientReleaser = KatHttp3PlaybackClient::release,
        ).also { source -> defaultRequestProperties.forEach(source::setRequestProperty) }

        override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): Factory = apply {
            this.defaultRequestProperties = Collections.unmodifiableMap(LinkedHashMap(defaultRequestProperties))
        }
    }

    private class StreamState {
        private val headers = CompletableDeferred<Headers>()
        private val firstBody = CompletableDeferred<Unit>()
        // Keep exactly one delivered chunk between the Flow collector and
        // Media3. With a rendezvous channel the collector could block before
        // open() returned, coupling stream adoption to Media3's first read().
        // kathttp3 remains the byte-bounded response FIFO for all later data.
        private val items = Channel<StreamItem>(capacity = 1)
        @Volatile var failure: IOException? = null
            private set
        @Volatile var isClosed = false
            private set
        @Volatile var isTerminal = false
            private set
        private var receivedBytes = 0L

        fun onHeaders(status: Int, headers: List<KatHttp3Header>) {
            this.headers.complete(Headers(status, headers))
        }

        suspend fun onBody(bytes: ByteArray) {
            if (isClosed || bytes.isEmpty()) return
            firstBody.complete(Unit)
            // If Media3 abandons a prepared source without closing it, an
            // unlimited send suspension would keep the collector and its
            // per-source native client forever. Cancel that dormant transport;
            // Media3 can open a fresh range if it needs the source again.
            withTimeout(STREAM_DELIVERY_STALL_MILLIS) {
                items.send(StreamItem.Body(bytes))
            }
            receivedBytes += bytes.size
            if (receivedBytes == bytes.size.toLong() ||
                receivedBytes % LOG_EVERY_BYTES < bytes.size.toLong()
            ) {
                Log.d(TAG, "HTTP/3 media received $receivedBytes bytes")
            }
        }

        fun onComplete() {
            isTerminal = true
            if (!isClosed) {
                items.close()
                Log.d(TAG, "HTTP/3 media stream completed bytes=$receivedBytes")
            }
        }

        fun onFailure(error: IOException) {
            if (isClosed) return
            isTerminal = true
            failure = error
            headers.completeExceptionally(error)
            firstBody.completeExceptionally(error)
            items.close(error)
            Log.w(TAG, "HTTP/3 media stream failed after $receivedBytes bytes", error)
        }

        fun awaitHeaders(timeoutMillis: Long): Headers? = try {
            runBlocking { withTimeout(timeoutMillis) { headers.await() } }
        } catch (error: Exception) {
            failure = failure ?: IOException("HTTP/3 headers unavailable", error)
            null
        }

        fun awaitFirstBody(timeoutMillis: Long): Boolean = try {
            runBlocking { withTimeout(timeoutMillis) { firstBody.await() } }
            failure == null
        } catch (error: Exception) {
            failure = failure ?: IOException("HTTP/3 body unavailable", error)
            false
        }

        fun take(): StreamItem = runBlocking {
            val result = items.receiveCatching()
            result.getOrNull()
                ?: result.exceptionOrNull()?.let { error ->
                    StreamItem.Failure(error as? IOException ?: IOException("HTTP/3 media stream failed", error))
                }
                ?: StreamItem.End
        }

        fun close() {
            isClosed = true
            isTerminal = true
            items.close()
        }
    }

    private data class Headers(val status: Int, val headers: List<KatHttp3Header>)

    /** Resources belonging to exactly one open attempt. */
    private class H3Attempt(
        val state: StreamState,
        private val clientReleaser: (KatHttp3Client) -> Unit,
    ) {
        @Volatile var call: KatHttp3Call? = null
        @Volatile var client: KatHttp3Client? = null
        @Volatile var collector: Job? = null
        @Volatile var openJob: Job? = null
        @Volatile var headers: Headers? = null
        @Volatile var hasFirstBody: Boolean = false
        @Volatile var responseError: IOException? = null
        private val clientReleased = AtomicBoolean(false)

        fun cancel() {
            // Closing the channel releases a collector currently blocked
            // handing a chunk to a DataSource that was never adopted.
            state.close()
            collector?.cancel()
            runCatching { call?.cancel() }
            openJob?.cancel()
            releaseClient()
        }

        fun releaseClient() {
            val activeClient = client ?: return
            if (clientReleased.compareAndSet(false, true)) clientReleaser(activeClient)
        }
    }
    private sealed interface StreamItem {
        data class Body(val bytes: ByteArray) : StreamItem
        data class Failure(val error: IOException) : StreamItem
        data object End : StreamItem
    }

    private companion object {
        const val TAG = "KatHttp3Media"
        // An Alt-Svc route that is unusable must not add a multi-second retry
        // chain before Media3 can start the standard HTTP fallback.
        const val H3_OPEN_ATTEMPTS = 1
        const val H3_OPEN_INITIAL_RETRY_BACKOFF_MILLIS = 100L
        const val H3_OPEN_MAX_RETRY_BACKOFF_MILLIS = 100L
        const val H3_ATTEMPT_DEADLINE_MILLIS = 6_000L
        const val HEADERS_WAIT_MILLIS = 3_500L
        const val FIRST_BODY_WAIT_MILLIS = 2_500L
        // kathttp3 owns the primary 30s consumer-stall timeout. Keep this
        // bridge guard slightly later so native reports retain their type.
        const val STREAM_DELIVERY_STALL_MILLIS = 35_000L
        const val LOG_EVERY_BYTES = 1_048_576L
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_NO_CONTENT = 204
        const val HTTP_NOT_MODIFIED = 304
        const val CONTENT_RANGE_HEADER = "content-range"
        const val CONTENT_LENGTH_HEADER = "content-length"
        val CONTENT_RANGE_PATTERN = Regex("bytes\\s+(\\d+)-\\d+/(?:\\d+|\\*)", RegexOption.IGNORE_CASE)
        val NEXT_SOURCE_ID = AtomicLong()
        val ACTIVE_H3_SOURCES = AtomicLong()
        val STREAM_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** One native HTTP/3 client for all playback range DataSources in this process. */
object KatHttp3PlaybackClient {
    private data class ClientGeneration(
        val client: KatHttp3Client,
        val activeCalls: AtomicInteger = AtomicInteger(),
        var retired: Boolean = false,
    )

    private var current: ClientGeneration? = null
    private val generations = IdentityHashMap<KatHttp3Client, ClientGeneration>()

    /** Acquires one request lease from the current client generation. */
    fun acquire(context: Context): KatHttp3Client = synchronized(this) {
        val generation = current ?: ClientGeneration(create(context.applicationContext)).also {
            current = it
            generations[it.client] = it
        }
        generation.activeCalls.incrementAndGet()
        generation.client
    }

    /** Releases the lease acquired by [acquire]. */
    fun release(client: KatHttp3Client) {
        val closeClient = synchronized(this) {
            val generation = generations[client]
            if (generation == null) {
                null
            } else {
                check(generation.activeCalls.decrementAndGet() >= 0) {
                    "Released HTTP/3 client without an active lease"
                }
                closeRetiredIfIdleLocked(generation)
            }
        }
        closeClient?.close()
    }

    /**
     * Drops only the failed generation. The next request obtains a new client;
     * an already-rotated client is left untouched by a late failed attempt.
     */
    fun invalidate(expected: KatHttp3Client) {
        val closeClient = synchronized(this) {
            val generation = generations[expected]
            if (generation == null || generation.retired) {
                null
            } else {
                generation.retired = true
                if (current === generation) current = null
                closeRetiredIfIdleLocked(generation)
            }
        }
        closeClient?.let { client ->
            runCatching { client.close() }
                .onFailure { Log.w("KatHttp3Media", "Failed to close retired HTTP/3 client", it) }
        }
    }

    /** Call when the owning process is deliberately torn down in tests/tools. */
    fun close() {
        val closeClients = synchronized(this) {
            current = null
            generations.values.forEach { it.retired = true }
            generations.values.toList().mapNotNull(::closeRetiredIfIdleLocked)
        }
        closeClients.forEach { client -> runCatching { client.close() } }
    }

    private fun closeRetiredIfIdleLocked(generation: ClientGeneration): KatHttp3Client? {
        if (!generation.retired || generation.activeCalls.get() != 0) return null
        generations.remove(generation.client)
        return generation.client
    }

    private fun create(context: Context): KatHttp3Client = KatHttp3Client(
            config = KatHttp3ClientConfig(
                connectTimeoutMillis = 4_000,
                requestTimeoutMillis = 2 * 60 * 60 * 1_000L,
                handshakeTimeoutMillis = 4_000,
                responseHeadersTimeoutMillis = 6_000,
                readTimeoutMillis = 30_000,
                callTimeoutMillis = 2 * 60 * 60 * 1_000L,
                consumerStallTimeoutMillis = 30_000,
                maxActiveStreamsPerOrigin = 4,
                maxQueuedRequestsPerOrigin = 16,
                // The kathttp3 FIFO is the only native-to-Kotlin response queue.
                // A Googlevideo range can be several MiB before Media3 consumes it.
                maxStreamingBufferedBodyBytes = 16L * 1024 * 1024,
                maxStreamingBufferedBytesPerStream = 16L * 1024 * 1024,
                maxStreamingBufferedBytesPerConnection = 32L * 1024 * 1024,
                enable0Rtt = true,
                resolver = PlatformDnsResolver(),
            ),
            applicationContext = context,
        )
}
