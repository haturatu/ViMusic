@file:Suppress("TooGenericExceptionCaught") // Downloader fallback must handle every kathttp3 failure type.

package app.vimusic.android.extractor

import android.util.Log
import app.vimusic.android.utils.sanitizeHttp3Headers
import dev.kathttp3.KatHttp3Client
import dev.kathttp3.KatHttp3ClientConfig
import dev.kathttp3.KatHttp3Exception
import dev.kathttp3.KatHttp3Header
import dev.kathttp3.KatHttp3Request
import dev.kathttp3.KatHttp3Response
import dev.kathttp3.KatHttp3RetryPolicy
import dev.kathttp3.PolicyRetryInterceptor
import dev.kathttp3.QuicTransportException
import dev.kathttp3.TlsHandshakeException
import dev.kathttp3.decodeContent
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * NewPipe [Downloader] backed by kathttp3's native HTTP/3 transport.
 *
 * kathttp3 only accepts HTTPS endpoints that negotiate HTTP/3. Requests to servers without HTTP/3,
 * or transport failures, are delegated to [fallback] so extraction remains available.
 */
class KatHttp3Downloader(
    private val fallback: Downloader,
    applicationContext: android.content.Context? = null,
) : Downloader() {
    private val client = KatHttp3Client(
        config = KatHttp3ClientConfig(
            // NewPipe stream resolution is latency-sensitive. HTTP/3 is an
            // opportunistic first path; a stalled QUIC request must quickly
            // hand control back to the established HTTP fallback.
            connectTimeoutMillis = 4_000,
            handshakeTimeoutMillis = 4_000,
            responseHeadersTimeoutMillis = 8_000,
            readTimeoutMillis = 10_000,
            callTimeoutMillis = 12_000,
            enable0Rtt = true,
            qlogEnabled = false,
            insecureCert = false,
            interceptors = listOf(
                PolicyRetryInterceptor(
                    KatHttp3RetryPolicy(
                        // The policy only retries idempotent methods. POST is
                        // deliberately one HTTP/3 attempt, then one HTTP/2
                        // fallback below; it must never be multiplied by an
                        // outer retry loop.
                        maxAttempts = 2,
                        retryIdempotentMethods = true,
                        initialBackoffMillis = 125,
                        maxBackoffMillis = 500,
                    ),
                ),
            ),
        ),
        applicationContext = applicationContext,
    )

    override fun execute(request: Request): Response {
        return try {
            Log.d(TAG, "HTTP/3 request ${request.httpMethod()} ${request.url()}")
            val headers = requestHeaders(request)
            val katRequest = KatHttp3Request(
                method = request.httpMethod(),
                url = request.url(),
                headers = headers,
                body = request.dataToSend(),
            )
            val rawResponse = executeHttp3(katRequest)
            // Do not repeat a rate-limited request over HTTP/2. NewPipe needs
            // this exact exception to handle its ReCaptcha flow.
            if (rawResponse.status == HTTP_TOO_MANY_REQUESTS) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }
            val response = rawResponse.decodeContent()
            // A server response (including 401/404/5xx) proves that HTTP/3
            // reached the endpoint. Preserve it for NewPipe rather than
            // submitting the same POST through a second transport.
            if (response.status !in 200..299) Log.w(TAG, "HTTP/3 ${response.status} ${request.url()}")
            Log.d(TAG, "HTTP/3 ${response.status} ${request.url()}")
            response.toNewPipeResponse(request.url())
        } catch (error: ReCaptchaException) {
            throw error
        } catch (error: Exception) {
            if (!error.isKatHttp3ConnectivityFailure()) throw error
            Log.w(TAG, "HTTP/3 request failed; falling back to the standard downloader: ${request.url()}", error)
            fallback.execute(request)
        }
    }

    private fun executeHttp3(request: KatHttp3Request): KatHttp3Response {
        return runBlocking {
            // Cancelling this watchdog cancels the native request. The
            // idempotent-only interceptor above may make one safe retry; POST
            // receives exactly this one H3 attempt before HTTP/2 fallback.
            withTimeout(HTTP3_WATCHDOG_MILLIS) { client.execute(request) }
        }
    }

    private fun requestHeaders(request: Request): List<KatHttp3Header> {
        val headers = linkedMapOf(
            // NewPipe replaces this default for YoutubeMusicInnertube with its Android
            // client User-Agent, exactly as YoutubeHttp3Example does.
            "user-agent" to mutableListOf(NewPipeDownloader.USER_AGENT),
            // KatHTTP3 decodes gzip/deflate, but forcing identity also avoids
            // a Brotli body on older Android releases where no decoder exists.
            "accept-encoding" to mutableListOf("identity"),
        )
        request.headers().forEach { (name, values) ->
            val normalizedName = name.lowercase()
            if (normalizedName != "accept-encoding" && normalizedName != "content-length") {
                headers[normalizedName] = values.toMutableList()
            }
        }
        // Match curl/OkHttp's fixed-length JSON POST framing.  NewPipe's request
        // already contains the JSON body and all YoutubeMusicInnertube headers; make the
        // length derive from those exact bytes rather than trusting a stale or
        // caller-supplied Content-Length value.
        request.dataToSend()?.let { body ->
            headers["content-length"] = mutableListOf(body.size.toString())
        }
        return sanitizeHttp3Headers(
            headers = headers.flatMap { (name, values) -> values.map { name to it } },
            contentLength = request.dataToSend()?.size?.toLong(),
        )
    }

    private fun dev.kathttp3.KatHttp3Response.toNewPipeResponse(requestUrl: String): Response {
        return Response(
            status,
            "",
            headers.groupBy(KatHttp3Header::name, KatHttp3Header::value),
            body.decodeToString(),
            requestUrl,
        )
    }

    private companion object {
        const val TAG = "KatHttp3Downloader"
        const val HTTP3_WATCHDOG_MILLIS = 5_000L
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

private fun Throwable.isKatHttp3ConnectivityFailure(): Boolean =
    this is TimeoutCancellationException ||
        this is KatHttp3Exception.Dns ||
        this is KatHttp3Exception.Timeout ||
        this is QuicTransportException ||
        this is TlsHandshakeException
