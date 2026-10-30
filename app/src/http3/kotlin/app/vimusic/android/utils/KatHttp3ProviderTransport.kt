@file:Suppress("TooGenericExceptionCaught") // Classify native failures before falling back to OkHttp.

package app.vimusic.android.utils

import android.content.Context
import app.vimusic.providers.utils.OkHttpProviderTransport
import app.vimusic.providers.utils.ProviderHttpResponse
import app.vimusic.providers.utils.ProviderHttpTransport
import dev.kathttp3.KatHttp3Client
import dev.kathttp3.KatHttp3ClientConfig
import dev.kathttp3.KatHttp3Request
import dev.kathttp3.decodeContent
import kotlinx.coroutines.CancellationException
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

/** Uses the existing Alt-Svc policy; only GET requests may enter the native path. */
internal class KatHttp3ProviderTransport(private val context: Context) : ProviderHttpTransport {
    private val clients = mutableMapOf<Triple<Int, Int, Int>, KatHttp3Client>()

    override suspend fun execute(client: OkHttpClient, request: Request): ProviderHttpResponse {
        val url = request.url.toString()
        if (request.method != "GET" || !Http3OriginPolicy.shouldAttemptHttp3(url)) {
            return executeOkHttp(client, request)
        }
        val response = try {
            nativeClient(client).execute(
                KatHttp3Request(
                    method = request.method,
                    url = url,
                    headers = sanitizeHttp3Headers(request.headers, defaultAcceptEncoding = "identity"),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!error.isHttp3TransportFailure()) throw error
            Http3OriginPolicy.recordHttp3Failure(url, error)
            return executeOkHttp(client, request)
        }
        Http3OriginPolicy.recordHttp3Response(url, response.status, response.headers.asPairs())
        // OkHttp handles redirect rules and the final URL. A misdirected alternative is
        // retried against the origin after the policy has invalidated its Alt-Svc entry.
        if (response.status in REDIRECT_STATUSES || response.status == 421) {
            return executeOkHttp(client, request)
        }
        val decoded = response.decodeContent()
        return ProviderHttpResponse(
            status = decoded.status,
            headers = Headers.Builder().apply {
                decoded.headers.forEach { add(it.name, it.value) }
            }.build(),
            body = decoded.body,
            url = request.url,
        )
    }

    private suspend fun executeOkHttp(client: OkHttpClient, request: Request): ProviderHttpResponse =
        OkHttpProviderTransport.execute(client, request).also { response ->
            Http3OriginPolicy.recordOriginResponse(response.url.toString(), response.headers)
        }

    private fun nativeClient(client: OkHttpClient): KatHttp3Client = synchronized(clients) {
        val key = Triple(client.connectTimeoutMillis, client.readTimeoutMillis, client.callTimeoutMillis)
        clients.getOrPut(key) {
            KatHttp3Client(
                config = KatHttp3ClientConfig(
                    connectTimeoutMillis = client.connectTimeoutMillis.toLong(),
                    handshakeTimeoutMillis = client.connectTimeoutMillis.toLong(),
                    responseHeadersTimeoutMillis = client.readTimeoutMillis.toLong(),
                    readTimeoutMillis = client.readTimeoutMillis.toLong(),
                    callTimeoutMillis = client.callTimeoutMillis.toLong().takeIf { it > 0 } ?: 30_000L,
                    queueTimeoutMillis = 500,
                    followRedirects = false,
                    insecureCert = false,
                ),
                applicationContext = context,
            )
        }
    }

    private companion object {
        val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}
