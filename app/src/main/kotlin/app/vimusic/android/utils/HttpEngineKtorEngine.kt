package app.vimusic.android.utils

import android.net.http.HttpEngine
import android.net.http.UploadDataProviders
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import app.vimusic.providers.utils.ProviderHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private class HttpEngineKtorConfig : HttpClientEngineConfig()

/** Minimal Ktor transport using Android's platform HTTP stack with QUIC enabled. */
private class HttpEngineKtorEngine(
    override val config: HttpEngineKtorConfig,
    private val httpEngine: HttpEngine,
) : HttpClientEngineBase("HttpEngine") {
    override suspend fun execute(data: HttpRequestData): HttpResponseData =
        suspendCancellableCoroutine { continuation ->
            val request = httpEngine.newUrlRequestBuilder(data.url.toString(), Callback(continuation), DIRECT_EXECUTOR)
                .setHttpMethod(data.method.value)
            data.headers.forEach { name, values -> values.forEach { request.addHeader(name, it) } }
            (data.body as? OutgoingContent.ByteArrayContent)?.bytes()?.let {
                request.setUploadDataProvider(UploadDataProviders.create(it), DIRECT_EXECUTOR)
            }
            continuation.invokeOnCancellation { request.cancel() }
            request.start()
        }

    private class Callback(
        private val continuation: kotlinx.coroutines.CancellableContinuation<HttpResponseData>,
    ) : UrlRequest.Callback() {
        private val chunks = ArrayList<ByteArray>()
        private var size = 0

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            request.read(ByteBuffer.allocate(BUFFER_SIZE))
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
                info.allHeaders.forEach { (name, values) -> values.forEach { append(name, it) } }
            }
            continuation.resume(
                HttpResponseData(
                    HttpStatusCode(info.httpStatusCode),
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
    }
}

/** Installs the HTTP/3-capable engine before any provider creates its lazy Ktor client. */
fun installHttpEngineKtorClient(httpEngine: HttpEngine) {
    ProviderHttpClient.install { block: HttpClientConfig<*>.() -> Unit ->
        HttpClient(HttpEngineKtorEngine(HttpEngineKtorConfig(), httpEngine), block)
    }
}

private const val BUFFER_SIZE = 32 * 1024
private val DIRECT_EXECUTOR = Executor(Runnable::run)
