@file:Suppress("TooGenericExceptionCaught") // Downloader fallback must handle every kathttp3 failure type.

package app.vimusic.android.extractor

import android.util.Log
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
            enable0Rtt = true,
            qlogEnabled = false,
            insecureCert = false,
            interceptors = listOf(
                PolicyRetryInterceptor(
                    KatHttp3RetryPolicy(
                        maxAttempts = 5,
                        initialBackoffMillis = 300,
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
            val response = executeHttp3(katRequest).decodeContent()
            if (response.status !in 200..299) {
                Log.w(TAG, "HTTP/3 returned ${response.status}; falling back to the standard downloader: ${request.url()}")
                return fallback.execute(request)
            }
            Log.d(TAG, "HTTP/3 ${response.status} ${request.url()}")
            response.toNewPipeResponse(request.url())
        } catch (error: ReCaptchaException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "HTTP/3 request failed; falling back to the standard downloader: ${request.url()}", error)
            fallback.execute(request)
        }
    }

    private fun executeHttp3(request: KatHttp3Request): KatHttp3Response {
        val timeouts = if (request.method == "POST") {
            POST_ATTEMPT_TIMEOUT_MILLIS
        } else {
            longArrayOf(HTTP3_WATCHDOG_MILLIS)
        }
        var lastError: Exception? = null

        timeouts.forEachIndexed { index, timeoutMillis ->
            try {
                return runBlocking {
                    // This watchdog also cancels a native request if the
                    // policy retry path itself stops producing callbacks.
                    withTimeout(timeoutMillis) { client.execute(request) }
                }
            } catch (error: Exception) {
                lastError = error
                if (index + 1 < timeouts.size) {
                    Log.w(
                        TAG,
                        "HTTP/3 POST attempt ${index + 1}/${timeouts.size} failed after " +
                            "${timeoutMillis}ms; retrying on a fresh request: ${request.url}",
                        error,
                    )
                    Thread.sleep(POST_RETRY_BACKOFF_MILLIS)
                }
            }
        }

        throw checkNotNull(lastError)
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
        if (status == 429) throw ReCaptchaException("reCaptcha Challenge requested", requestUrl)
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
        const val HTTP3_WATCHDOG_MILLIS = 15_000L
        const val POST_RETRY_BACKOFF_MILLIS = 250L
        val POST_ATTEMPT_TIMEOUT_MILLIS = longArrayOf(4_000L, 6_000L)

    }
}
