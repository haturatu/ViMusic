package io.ktor.client.engine.android

import android.net.http.HttpEngine
import android.net.http.HttpException
import android.net.http.UploadDataProvider
import android.net.http.UploadDataSink
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import android.util.Log
import app.vimusic.ktor.httpengine.KtorCoreBridge
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.ResponseAdapterAttributeKey
import io.ktor.http.HeadersImpl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.io.write
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Ktor transport that uses HttpEngine UrlRequest directly, including QUIC/HTTP/3 negotiation. */
@OptIn(InternalAPI::class)
public class HttpEngineAndroidClientEngine(
    override val config: HttpEngineAndroidEngineConfig,
) : HttpClientEngineBase("ktor-httpengine-android") {
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability)

    override suspend fun execute(data: HttpRequestData): HttpResponseData = withContext(Dispatchers.IO) {
        val callContext = ktorCallContext()
        val outgoingContent = data.body
        val transaction = HttpEngineTransaction()
        val uploadBytes = outgoingContent.bytes(callContext)
        val builder = config.httpEngine.newUrlRequestBuilder(data.url.toString(), executor, transaction).apply {
            setHttpMethod(data.method.value)
            data.headers.forEach { name, values ->
                if (!name.equals(HttpHeaders.ContentLength, ignoreCase = true)) values.forEach { addHeader(name, it) }
            }
            outgoingContent.headers.forEach { name, values ->
                if (!name.equals(HttpHeaders.ContentLength, ignoreCase = true)) values.forEach { addHeader(name, it) }
            }
            if (uploadBytes != null) setUploadDataProvider(ByteArrayUploadDataProvider(uploadBytes), executor)
        }
        val request = builder.build()
        val response = transaction.awaitResponse(request)
        val body = data.attributes.getOrNull(ResponseAdapterAttributeKey)
            ?.adapt(data, response.status, response.headers, response.channel, outgoingContent, callContext)
            ?: response.channel
        HttpResponseData(response.status, GMTDate(), response.headers, response.protocol, body, callContext)
    }

    private companion object { val executor: Executor = Executors.newCachedThreadPool() }
}

private class HttpEngineTransaction : UrlRequest.Callback {
    private val channel = ByteChannel(autoFlush = true)
    private val responseDelivered = AtomicBoolean(false)
    private lateinit var continuation: kotlinx.coroutines.CancellableContinuation<StartedResponse>

    suspend fun awaitResponse(urlRequest: UrlRequest): StartedResponse = suspendCancellableCoroutine { continuation ->
        this.continuation = continuation
        continuation.invokeOnCancellation {
            channel.cancel(it)
            urlRequest.cancel()
        }
        urlRequest.start()
    }

    override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
        Log.d(TAG, "Following redirect ${info.url} -> $newLocationUrl")
        request.followRedirect()
    }

    override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
        val negotiated = info.negotiatedProtocol.ifBlank { "HTTP/1.1" }
        Log.i(TAG, "${info.url} negotiated $negotiated")
        val started = StartedResponse(
            info.httpStatusText.takeIf(String::isNotBlank)?.let { HttpStatusCode(info.httpStatusCode, it) }
                ?: HttpStatusCode.fromValue(info.httpStatusCode),
            HeadersImpl(info.headers.asMap),
            negotiated.toKtorProtocol(),
            channel,
        )
        if (responseDelivered.compareAndSet(false, true)) continuation.resume(started)
        request.read(ByteBuffer.allocateDirect(BUFFER_SIZE))
    }

    @OptIn(InternalAPI::class)
    override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
        byteBuffer.flip()
        if (byteBuffer.hasRemaining()) {
            val bytes = ByteArray(byteBuffer.remaining())
            byteBuffer.get(bytes)
            channel.writeBuffer.write(bytes)
            channel.flushWriteBuffer()
        }
        byteBuffer.clear()
        request.read(byteBuffer)
    }

    override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) { channel.close() }

    override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: HttpException) {
        if (responseDelivered.compareAndSet(false, true)) continuation.resumeWithException(error)
        channel.cancel(error)
    }

    override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
        val error = java.util.concurrent.CancellationException("HttpEngine request cancelled")
        if (responseDelivered.compareAndSet(false, true)) continuation.resumeWithException(error)
        channel.cancel(error)
    }

    private companion object {
        const val TAG = "HttpEngineKtor"
        const val BUFFER_SIZE = 32 * 1024
    }
}

private data class StartedResponse(
    val status: HttpStatusCode,
    val headers: HeadersImpl,
    val protocol: HttpProtocolVersion,
    val channel: ByteReadChannel,
)

private fun String.toKtorProtocol(): HttpProtocolVersion = when {
    equals("h3", ignoreCase = true) || startsWith("h3-", ignoreCase = true) -> HttpProtocolVersion("HTTP", 3, 0)
    equals("h2", ignoreCase = true) -> HttpProtocolVersion.HTTP_2_0
    else -> HttpProtocolVersion.HTTP_1_1
}

private suspend fun OutgoingContent.bytes(context: CoroutineContext): ByteArray? = when (this) {
    is OutgoingContent.NoContent -> null
    is OutgoingContent.ByteArrayContent -> bytes()
    is OutgoingContent.ReadChannelContent -> ByteArrayOutputStream().use { output ->
        readFrom().copyTo(output)
        output.toByteArray()
    }
    is OutgoingContent.ContentWrapper -> delegate().bytes(context)
    else -> error("Unsupported HttpEngine request body: ${this::class.simpleName}")
}

private class ByteArrayUploadDataProvider(private val bytes: ByteArray) : UploadDataProvider() {
    private var position = 0
    override fun getLength(): Long = bytes.size.toLong()
    override fun read(sink: UploadDataSink, buffer: ByteBuffer) {
        val count = minOf(buffer.remaining(), bytes.size - position)
        if (count > 0) { buffer.put(bytes, position, count); position += count }
        sink.onReadSucceeded(false)
    }
    override fun rewind(sink: UploadDataSink) { position = 0; sink.onRewindSucceeded() }
}

private suspend fun ktorCallContext(): CoroutineContext = suspendCoroutineUninterceptedOrReturn { continuation ->
    KtorCoreBridge.callContext(continuation)
}

/** Creates a Ktor client whose requests use Android's HTTP/3-capable [HttpEngine] directly. */
public fun createHttpEngineAndroidClient(
    httpEngine: HttpEngine,
    block: HttpClientConfig<*>.() -> Unit = {},
): HttpClient {
    val config = HttpEngineAndroidEngineConfig().apply { this.httpEngine = httpEngine }
    return HttpClient(HttpEngineAndroidClientEngine(config), block)
}
