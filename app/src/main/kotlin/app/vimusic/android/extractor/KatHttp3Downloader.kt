@file:Suppress("TooGenericExceptionCaught") // Downloader fallback must handle every kathttp3 failure type.

package app.vimusic.android.extractor

import android.util.Log
import app.vimusic.android.utils.isHttp3TransportFailure
import app.vimusic.android.utils.Http3OriginPolicy
import app.vimusic.android.utils.sanitizeHttp3Headers
import dev.kathttp3.KatHttp3Client
import dev.kathttp3.KatHttp3ClientConfig
import dev.kathttp3.KatHttp3Header
import dev.kathttp3.KatHttp3Request
import dev.kathttp3.KatHttp3Response
import dev.kathttp3.KatHttp3RetryPolicy
import dev.kathttp3.PolicyRetryInterceptor
import dev.kathttp3.decodeContent
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
            // Search tabs can replace a request while native cancellation is
            // still releasing the single-origin permit. H3 is opportunistic;
            // do not leave the replacement request queued for the 30s default.
            queueTimeoutMillis = 500,
            enable0Rtt = true,
            qlogEnabled = false,
            insecureCert = false,
            interceptors = listOf(
                PolicyRetryInterceptor(
                    KatHttp3RetryPolicy(
                        // The outer five-second watchdog owns the single H3
                        // attempt for extraction. Fall back immediately after
                        // it, rather than spending its budget on a retry that
                        // cannot complete before the watchdog expires.
                        maxAttempts = 1,
                        retryIdempotentMethods = true,
                        initialBackoffMillis = 250,
                        maxBackoffMillis = 1_000,
                    ),
                ),
            ),
        ),
        applicationContext = applicationContext,
    )

    override fun execute(request: Request): Response {
        if (!Http3OriginPolicy.shouldAttemptHttp3(request.url())) {
            return executeStandard(request)
        }
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
            Http3OriginPolicy.recordHttp3Response(
                request.url(),
                rawResponse.status,
                rawResponse.headers.map { it.name to it.value },
            )
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
            if (!error.isHttp3TransportFailure()) throw error
            Http3OriginPolicy.recordHttp3Failure(request.url(), error)
            Log.i(TAG, "HTTP/3 unavailable; using the standard downloader: ${request.url()}")
            Log.d(TAG, "HTTP/3 fallback cause", error)
            executeStandard(request)
        }
    }

    private fun executeStandard(request: Request): Response = fallback.execute(request).also { response ->
        Http3OriginPolicy.recordOriginResponse(
            request.url(),
            response.responseHeaders().flatMap { (name, values) -> values.map { name to it } },
        )
    }

    private fun executeHttp3(request: KatHttp3Request): KatHttp3Response {
        return runBlocking {
            // Keep extraction responsive even if native cancellation or an
            // HTTP/3 response callback stalls. TimeoutCancellationException
            // is classified as a transport failure and immediately falls
            // back to the standard downloader.
            withTimeout(HTTP3_ATTEMPT_TIMEOUT_MILLIS) {
                client.execute(request)
            }
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
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP3_ATTEMPT_TIMEOUT_MILLIS = 5_000L
    }
}
