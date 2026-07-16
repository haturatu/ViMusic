package app.vimusic.android.extractor

import android.net.http.HttpEngine
import android.net.http.HttpException
import android.net.http.UploadDataProvider
import android.net.http.UploadDataSink
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * [Downloader] implementation backed by Android's [HttpEngine] so NewPipe extractor
 * requests use HTTP/2 on Android 14 and newer.
 *
 * The resolved-address constructor creates a short-lived engine with a hostname mapping. This
 * preserves the URL hostname for TLS/SNI while retaining NewPipe's DNS fallback policy.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class HttpEngineDownloader private constructor(
    private val engineForUrl: (String) -> HttpEngine,
    private val shutdownAfterRequest: Boolean,
) : Downloader() {
    constructor(httpEngine: HttpEngine) : this({ httpEngine }, false)

    constructor(context: android.content.Context, dnsTarget: NewPipeDnsTarget.Resolved) : this(
        engineForUrl = { url -> resolvedAddressEngine(context.applicationContext, url, dnsTarget) },
        shutdownAfterRequest = true,
    )

    private val executor = Executors.newCachedThreadPool()

    override fun execute(request: Request): Response {
        val httpEngine = engineForUrl(request.url())
        val resultRef = AtomicReference<Response>()
        val errorRef = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)

        val urlRequest = httpEngine.newUrlRequestBuilder(
            request.url(),
            executor,
            Callback(resultRef, errorRef, latch)
        ).apply {
            setHttpMethod(request.httpMethod())

            val headers = LinkedHashMap<String, MutableList<String>>()
            headers["User-Agent"] = mutableListOf(NewPipeDownloader.USER_AGENT)
            headers["Connection"] = mutableListOf("close")
            headers["Cache-Control"] = mutableListOf("no-cache")
            request.headers().forEach { (name, values) -> headers[name] = values.toMutableList() }
            headers.forEach { (name, values) -> values.forEach { addHeader(name, it) } }

            request.dataToSend()?.let { data ->
                setUploadDataProvider(ByteArrayUploadDataProvider(data), executor)
            }
        }.build()

        try {
            urlRequest.start()

            if (!latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                runCatching { urlRequest.cancel() }
                throw IOException("HttpEngine request timed out for ${request.url()}")
            }

            errorRef.get()?.let { throw it }
            return resultRef.get()
                ?: throw IOException("HttpEngine request produced no response for ${request.url()}")
        } finally {
            if (shutdownAfterRequest) httpEngine.shutdown()
        }
    }

    private class Callback(
        private val resultRef: AtomicReference<Response>,
        private val errorRef: AtomicReference<Throwable>,
        private val latch: CountDownLatch
    ) : UrlRequest.Callback {
        private val body = ByteArrayOutputStream()

        override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
            request.followRedirect()
        }

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            request.read(ByteBuffer.allocateDirect(READ_BUFFER_BYTES))
        }

        override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
            byteBuffer.flip()
            val bytes = ByteArray(byteBuffer.remaining())
            byteBuffer.get(bytes)
            body.write(bytes)
            byteBuffer.clear()
            request.read(byteBuffer)
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
            Log.i(TAG, "${info.url} negotiated ${info.negotiatedProtocol.ifBlank { "HTTP/1.1" }}")
            val statusCode = info.httpStatusCode
            val headers = info.headers.asMap
            val responseBody = body.toString(Charsets.UTF_8)
            if (statusCode == 429) {
                errorRef.set(ReCaptchaException("reCaptcha Challenge requested", info.url))
            } else {
                resultRef.set(
                    Response(statusCode, info.httpStatusText, headers, responseBody, info.url)
                )
            }
            latch.countDown()
        }

        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: HttpException) {
            errorRef.set(error)
            latch.countDown()
        }

        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
            latch.countDown()
        }
    }

    private class ByteArrayUploadDataProvider(
        private val data: ByteArray
    ) : UploadDataProvider() {
        private var position = 0

        override fun getLength(): Long = data.size.toLong()

        override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
            val toRead = minOf(byteBuffer.remaining(), data.size - position)
            if (toRead > 0) {
                byteBuffer.put(data, position, toRead)
                position += toRead
            }
            uploadDataSink.onReadSucceeded(false)
        }

        override fun rewind(uploadDataSink: UploadDataSink) {
            position = 0
            uploadDataSink.onRewindSucceeded()
        }
    }

    private companion object {
        const val TAG = "HttpEngineDownloader"
        const val REQUEST_TIMEOUT_SECONDS = 30L
        const val READ_BUFFER_BYTES = 32 * 1024

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun resolvedAddressEngine(
            context: android.content.Context,
            requestUrl: String,
            dnsTarget: NewPipeDnsTarget.Resolved,
        ): HttpEngine {
            val host = URI(requestUrl).host ?: throw IOException("Request URL has no host: $requestUrl")
            val address = NewPipeDownloader.resolveAddress(host, dnsTarget.index)
            val rules = "MAP $host ${address.hostAddress}"
            Log.i(TAG, "Using HTTP/2 hostname mapping hostname=$host address=${address.hostAddress}")
            val builder = HttpEngine.Builder(context)
                .setEnableHttp2(true)
            val options = "{\"HostResolverRules\":{\"host_resolver_rules\":\"$rules\"}}"
            // setExperimentalOptions is supplied by newer HttpEngine extension releases but is
            // absent from the compile SDK stub. Resolve it at runtime without a Cronet dependency.
            runCatching {
                builder.javaClass
                    .getMethod("setExperimentalOptions", String::class.java)
                    .invoke(builder, options)
            }.getOrElse { error ->
                throw IOException("HttpEngine does not support hostname mapping", error)
            }
            return builder.build()
        }
    }
}
