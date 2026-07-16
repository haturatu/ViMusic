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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
    private val fallbackFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory(),
) : BaseDataSource(true), HttpDataSource {
    private val sourceId = NEXT_SOURCE_ID.incrementAndGet()
    private val requestProperties = LinkedHashMap<String, String>()
    private var dataSpec: DataSpec? = null
    private var call: KatHttp3Call? = null
    private var collector: Job? = null
    @Volatile private var stream: StreamState? = null
    private var currentChunk: ByteArray? = null
    private var currentChunkOffset = 0
    private var fallback: HttpDataSource? = null
    private var responseCode = -1
    private var responseHeaders: Map<String, List<String>> = emptyMap()
    private var responseUri: Uri? = null
    private var opened = false
    private var client: KatHttp3Client? = null
    private val h3Active = AtomicBoolean(false)

    private fun client(): KatHttp3Client = client ?: clientFactory().also { client = it }

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
            var lastError: IOException? = null
            repeat(H3_OPEN_ATTEMPTS) { attempt ->
                // Do not publish an attempt to the DataSource until both its
                // headers and first body chunk have arrived. A watchdog-cancelled
                // attempt may otherwise resume later and overwrite the stream
                // belonging to a newer playback request.
                val h3Attempt = H3Attempt(StreamState())
                val completion = CompletableFuture<H3Attempt>()
                h3Attempt.openJob = STREAM_SCOPE.launch {
                    try {
                        val activeCall = client().executeStreaming(
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
                                // kathttp3 has reached a terminal response after all
                                // delivered chunks have crossed the one-slot Media3
                                // bridge. Media3 may retain this DataSource without
                                // calling close(), so do not retain its native client
                                // or count it as an active transport until then.
                                if (stream === h3Attempt.state) {
                                    releaseH3Transport("terminal")
                                    closeClient()
                                }
                            }
                        }
                        val headers = h3Attempt.state.awaitHeaders(HEADERS_WAIT_MILLIS)
                        h3Attempt.headers = headers
                        h3Attempt.hasFirstBody = headers != null &&
                            headers.status in 200..299 && h3Attempt.state.awaitFirstBody(FIRST_BODY_WAIT_MILLIS)
                        completion.complete(h3Attempt)
                    } catch (error: Throwable) {
                        completion.completeExceptionally(error)
                    }
                }

                val result = try {
                    completion.get(H3_ATTEMPT_DEADLINE_MILLIS, TimeUnit.MILLISECONDS)
                } catch (error: Exception) {
                    lastError = IOException("HTTP/3 media open watchdog timed out", error)
                    h3Attempt.cancel()
                    null
                }
                val headers = result?.headers
                if (headers != null && headers.status in 200..299 && result.hasFirstBody) {
                    // This is now the sole live HTTP/3 attempt owned by this
                    // DataSource. Older attempts were cancelled before retry.
                    stream = h3Attempt.state
                    call = h3Attempt.call
                    collector = h3Attempt.collector
                    responseCode = headers.status
                    responseHeaders = headers.headers.groupBy(KatHttp3Header::name, KatHttp3Header::value)
                    opened = true
                    transferStarted(dataSpec)
                    markH3TransportActive()
                    // A short response can become terminal between the first-body
                    // gate and adoption. Cover that race explicitly.
                    if (h3Attempt.state.isTerminal) {
                        releaseH3Transport("terminal-before-adoption")
                        closeClient()
                    }
                    Log.i(
                        TAG,
                            "source=$sourceId H3 media stream opened status=${headers.status} " +
                            "attempt=${attempt + 1} active=${ACTIVE_H3_SOURCES.get()}: ${dataSpec.uri}",
                    )
                    return contentLength(dataSpec)
                }

                lastError = h3Attempt.state.failure ?: lastError ?: when {
                    headers == null -> IOException("HTTP/3 headers timed out")
                    headers.status !in 200..299 -> IOException("HTTP/3 returned ${headers.status}")
                    else -> IOException("HTTP/3 response body timed out")
                }
                h3Attempt.cancel()
                if (attempt + 1 < H3_OPEN_ATTEMPTS) {
                    Log.w(TAG, "HTTP/3 media open attempt ${attempt + 1} failed; retrying", lastError)
                    Thread.sleep(h3OpenRetryBackoffMillis(attempt))
                }
            }
            return openFallback(
                dataSpec,
                checkNotNull(lastError) { "HTTP/3 media open attempts exhausted without an error" },
            )
        } catch (error: Throwable) {
            stopStreaming()
            closeClient()
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
        fallback?.let { return it.read(buffer, offset, length) }
        val spec = dataSpec ?: throw IOException("DataSource is not open")
        val state = stream ?: return C.RESULT_END_OF_INPUT
        while (true) {
            val chunk = currentChunk
            if (chunk != null && currentChunkOffset < chunk.size) {
                val count = minOf(length, chunk.size - currentChunkOffset)
                chunk.copyInto(buffer, offset, currentChunkOffset, currentChunkOffset + count)
                currentChunkOffset += count
                bytesTransferred(count)
                return count
            }
            currentChunk = null
            currentChunkOffset = 0
            when (val next = state.take()) {
                is StreamItem.Body -> {
                    currentChunk = next.bytes
                    continue
                }
                StreamItem.End -> return C.RESULT_END_OF_INPUT
                is StreamItem.Failure -> throw HttpDataSource.HttpDataSourceException.createForIOException(
                    next.error,
                    spec,
                    HttpDataSource.HttpDataSourceException.TYPE_READ,
                )
            }
        }
    }

    override fun close() {
        Log.d(
            TAG,
            "source=$sourceId close h3=${stream != null} fallback=${fallback != null}",
        )
        stopStreaming()
        fallback?.close()
        fallback = null
        closeClient()
        responseCode = -1
        responseHeaders = emptyMap()
        responseUri = null
        dataSpec = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun openFallback(dataSpec: DataSpec, error: IOException): Long {
        Log.w(TAG, "HTTP/3 media unavailable; using standard HTTP fallback: ${dataSpec.uri}", error)
        stopStreaming()
        closeClient()
        val source = fallbackFactory.createDataSource().also { fallback ->
            requestProperties.forEach(fallback::setRequestProperty)
        }
        fallback = source
        return source.open(dataSpec).also {
            opened = true
            transferStarted(dataSpec)
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
        stream = null
        releaseH3Transport("close")
        currentChunk = null
        currentChunkOffset = 0
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

    private fun closeClient() {
        val activeClient = client ?: return
        client = null
        runCatching { activeClient.close() }
            .onFailure { Log.w(TAG, "source=$sourceId failed to close HTTP/3 client", it) }
    }

    private fun h3OpenRetryBackoffMillis(failedAttemptIndex: Int): Long =
        (H3_OPEN_INITIAL_RETRY_BACKOFF_MILLIS shl failedAttemptIndex)
            .coerceAtMost(H3_OPEN_MAX_RETRY_BACKOFF_MILLIS)

    private fun requestHeaders(dataSpec: DataSpec): List<KatHttp3Header> = buildList {
        (requestProperties + dataSpec.httpRequestHeaders).forEach { (name, value) ->
            val normalized = name.lowercase()
            if (normalized !in FORBIDDEN_HEADERS && normalized != "content-length" &&
                normalized.none { it <= ' ' || it == ':' } && value.none { it == '\r' || it == '\n' }
            ) {
                add(KatHttp3Header(normalized, value))
            }
        }
        if (none { it.name == "accept-encoding" }) add(KatHttp3Header("accept-encoding", "identity"))
        dataSpec.httpBody?.let { add(KatHttp3Header("content-length", it.size.toString())) }
    }

    private fun contentLength(dataSpec: DataSpec): Long {
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) return dataSpec.length
        return responseHeaders.entries.firstOrNull { it.key.equals("content-length", ignoreCase = true) }
            ?.value?.firstOrNull()?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
    }

    class Factory(context: Context) : HttpDataSource.Factory {
        private val applicationContext = context.applicationContext
        private var defaultRequestProperties: Map<String, String> = emptyMap()

        override fun createDataSource(): HttpDataSource = KatHttp3MediaDataSource(
            clientFactory = { KatHttp3PlaybackClient.create(applicationContext) },
        ).also { source -> defaultRequestProperties.forEach(source::setRequestProperty) }

        override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): Factory = apply {
            this.defaultRequestProperties = Collections.unmodifiableMap(LinkedHashMap(defaultRequestProperties))
        }
    }

    private class StreamState {
        private val headers = java.util.concurrent.CompletableFuture<Headers>()
        private val firstBody = java.util.concurrent.CompletableFuture<Unit>()
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
            headers.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            failure = failure ?: IOException("HTTP/3 headers unavailable", error)
            null
        }

        fun awaitFirstBody(timeoutMillis: Long): Boolean = try {
            firstBody.get(timeoutMillis, TimeUnit.MILLISECONDS)
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
    private class H3Attempt(val state: StreamState) {
        @Volatile var call: KatHttp3Call? = null
        @Volatile var collector: Job? = null
        @Volatile var openJob: Job? = null
        @Volatile var headers: Headers? = null
        @Volatile var hasFirstBody: Boolean = false

        fun cancel() {
            // Closing the channel releases a collector currently blocked
            // handing a chunk to a DataSource that was never adopted.
            state.close()
            collector?.cancel()
            runCatching { call?.cancel() }
            openJob?.cancel()
        }
    }
    private sealed interface StreamItem {
        data class Body(val bytes: ByteArray) : StreamItem
        data class Failure(val error: IOException) : StreamItem
        data object End : StreamItem
    }

    private companion object {
        const val TAG = "KatHttp3Media"
        // A stalled QUIC request must not leave Media3 buffering for 12 seconds
        // per attempt. Healthy Googlevideo HTTP/3 responses send headers well
        // below this threshold; retry once on a fresh QUIC connection instead.
        const val H3_OPEN_ATTEMPTS = 5
        const val H3_OPEN_INITIAL_RETRY_BACKOFF_MILLIS = 50L
        const val H3_OPEN_MAX_RETRY_BACKOFF_MILLIS = 400L
        const val H3_ATTEMPT_DEADLINE_MILLIS = 12_500L
        const val HEADERS_WAIT_MILLIS = 6_000L
        const val FIRST_BODY_WAIT_MILLIS = 6_000L
        const val STREAM_DELIVERY_STALL_MILLIS = 30_000L
        const val LOG_EVERY_BYTES = 1_048_576L
        val NEXT_SOURCE_ID = AtomicLong()
        val ACTIVE_H3_SOURCES = AtomicLong()
        val STREAM_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val FORBIDDEN_HEADERS = setOf("connection", "keep-alive", "proxy-connection", "transfer-encoding", "upgrade", "host")
    }
}

private object KatHttp3PlaybackClient {
    fun create(context: Context): KatHttp3Client = KatHttp3Client(
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
