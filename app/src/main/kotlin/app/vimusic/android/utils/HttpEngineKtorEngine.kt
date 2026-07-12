package app.vimusic.android.utils

import android.net.http.HttpEngine
import android.net.http.UploadDataProvider
import android.net.http.UploadDataSink
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import app.vimusic.providers.utils.ProviderHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private class HttpEngineKtorConfig : HttpClientEngineConfig()

/** Minimal Ktor transport using Android's platform HTTP stack with QUIC enabled. */
@OptIn(InternalAPI::class)
private class HttpEngineKtorEngine(
    override val config: HttpEngineKtorConfig,
    private val httpEngine: HttpEngine,
) : HttpClientEngineBase("HttpEngine") {
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability)

    override suspend fun execute(data: HttpRequestData): HttpResponseData =
        suspendCancellableCoroutine { continuation ->
            val request = httpEngine.newUrlRequestBuilder(data.url.toString(), HTTP_ENGINE_EXECUTOR, Callback(continuation))
                .setHttpMethod(data.method.value)
            data.headers.forEach { name, values -> values.forEach { request.addHeader(name, it) } }
            data.body.headers.forEach { name, values ->
                name?.takeIf { data.headers[it].isNullOrEmpty() }
                    ?.let { headerName -> values.forEach { request.addHeader(headerName, it) } }
            }
            if (data.headers[HttpHeaders.ContentType].isNullOrEmpty()) {
                data.body.contentType?.let { request.addHeader(HttpHeaders.ContentType, it.toString()) }
            }
            (data.body as? OutgoingContent.ByteArrayContent)?.bytes()?.let {
                request.setUploadDataProvider(ByteArrayUploadDataProvider(it), HTTP_ENGINE_EXECUTOR)
            }
            val urlRequest = request.build()
            continuation.invokeOnCancellation { urlRequest.cancel() }
            urlRequest.start()
        }

    private class Callback(
        private val continuation: kotlinx.coroutines.CancellableContinuation<HttpResponseData>,
    ) : UrlRequest.Callback {
        private val chunks = ArrayList<ByteArray>()
        private var size = 0

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            request.read(ByteBuffer.allocateDirect(BUFFER_SIZE))
        }

        override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, buffer: ByteBuffer) {
            buffer.flip()
            ByteArray(buffer.remaining()).also {
                buffer.get(it)
                chunks += it
                size += it.size
            }
            buffer.clear()
            request.read(buffer)
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
            val bytes = ByteArray(size)
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(bytes, offset)
                offset += chunk.size
            }
            val headers = Headers.build {
                // HttpEngine may transparently decompress a response while retaining the original
                // Content-Length header. Passing that stale length makes Ktor reject the body.
                info.headers.asList
                    .filterNot { (name, _) -> name.equals(HttpHeaders.ContentLength, ignoreCase = true) }
                    .forEach { (name, value) -> append(name, value) }
            }
            continuation.resume(
                HttpResponseData(
                    HttpStatusCode(info.httpStatusCode, info.httpStatusText),
                    GMTDate(),
                    headers,
                    HttpProtocolVersion.HTTP_1_1,
                    ByteReadChannel(bytes),
                    continuation.context,
                )
            )
        }

        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: android.net.http.HttpException) {
            continuation.resumeWithException(error)
        }

        override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
            request.followRedirect()
        }

        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
            continuation.cancel()
        }
    }
}

private class ByteArrayUploadDataProvider(private val bytes: ByteArray) : UploadDataProvider() {
    private var offset = 0

    override fun getLength() = bytes.size.toLong()

    override fun read(sink: UploadDataSink, buffer: ByteBuffer) {
        val count = minOf(buffer.remaining(), bytes.size - offset)
        if (count > 0) {
            buffer.put(bytes, offset, count)
            offset += count
        }
        // HttpEngine knows the exact length, so this is a non-chunked upload. Its callback must
        // never mark a final chunk; completion is inferred from [getLength].
        sink.onReadSucceeded(false)
    }

    override fun rewind(sink: UploadDataSink) {
        offset = 0
        sink.onRewindSucceeded()
    }
}

/** Installs the HTTP/3-capable engine before any provider creates its lazy Ktor client. */
fun installHttpEngineKtorClient(httpEngine: HttpEngine) {
    ProviderHttpClient.install { block: HttpClientConfig<*>.() -> Unit ->
        HttpClient(HttpEngineKtorEngine(HttpEngineKtorConfig(), httpEngine), block)
    }
}

private const val BUFFER_SIZE = 32 * 1024
private val HTTP_ENGINE_EXECUTOR = Executors.newCachedThreadPool { runnable ->
    Thread(runnable, "HttpEngineKtor").apply { isDaemon = true }
}
