package io.ktor.client.engine.kathttp3

import android.util.Log
import dev.kathttp3.KatHttp3Client
import dev.kathttp3.KatHttp3Header
import dev.kathttp3.KatHttp3Request
import dev.kathttp3.decodeContent
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.ResponseAdapterAttributeKey
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Ktor transport backed by kathttp3, a standalone ngtcp2/nghttp3/BoringSSL HTTP/3 client. */
@OptIn(InternalAPI::class)
public class KatHttp3ClientEngine(
    override val config: KatHttp3EngineConfig,
) : HttpClientEngineBase("ktor-kathttp3") {
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability)

    private val ownsClient = config.client == null
    private val client: KatHttp3Client = config.client ?: KatHttp3Client(config.clientConfig)
    private val requestPermits = Semaphore(config.maxConcurrentRequests)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        // Ktor creates this context with a CompletableJob and later relies on that
        // job while cleaning up the response.  Do not obtain it inside
        // withContext(Dispatchers.IO): that context belongs to the temporary
        // coroutine created by withContext and is not a CompletableJob.
        val callContext = coroutineContext

        return withContext(Dispatchers.IO) {
            requestPermits.withPermit {
            val url = data.url.toString()
            require(url.startsWith("https://")) { "kathttp3 only supports https:// URLs (got $url)" }

            val outgoingContent = data.body
            val request = KatHttp3Request(
                method = data.method.value.uppercase(),
                url = url,
                headers = data.buildKatHeaders(),
                body = outgoingContent.bytes(),
            )

            // Kathttp3's Flow-based API correctly applies HTTP/3 receive
            // flow-control, but its stream window can stall on image-heavy
            // Coil screens. These clients fetch bounded image/JSON responses;
            // use the stable buffered API (16 MiB default limit) instead.
            // Ktor's ContentEncoding plugin cannot safely decode a body that
            // has crossed the native boundary with an Android Brotli decoder
            // available. Decode once here and remove content-encoding so Ktor
            // receives the same JSON bytes as the direct kathttp3 downloader.
            val response = client.execute(request).decodeContent()
            Log.i(TAG, "$url negotiated ${response.protocol} status ${response.status}")
            val channel = ByteChannel(autoFlush = true)
            channel.writeFully(response.body)
            channel.flushAndClose()
            val headers = response.headers.toKtorHeaders().build()
            val body = data.attributes.getOrNull(ResponseAdapterAttributeKey)
                ?.adapt(data, HttpStatusCode.fromValue(response.status), headers, channel, outgoingContent, callContext)
                ?: channel
            HttpResponseData(
                statusCode = HttpStatusCode.fromValue(response.status),
                requestTime = GMTDate(),
                headers = headers,
                version = HTTP_3,
                body = body,
                callContext = callContext,
            )
            }
        }
    }

    override fun close() {
        super.close()
        if (ownsClient) runCatching { client.close() }
    }

    private companion object {
        const val TAG = "KatHttp3Ktor"
        val HTTP_3 = HttpProtocolVersion("HTTP", 3, 0)
    }
}

private fun HttpRequestData.buildKatHeaders(): List<KatHttp3Header> {
    val result = ArrayList<KatHttp3Header>()
    fun add(name: String, value: String) {
        if (name.equals(HttpHeaders.ContentLength, ignoreCase = true)) return
        val normalized = name.lowercase()
        if (normalized in HTTP3_FORBIDDEN_REQUEST_HEADERS) return
        // The native response boundary is buffered. Request an uncompressed
        // body so Ktor sees the same JSON bytes as the direct HTTP/3 client
        // and never has to decode Brotli after crossing that boundary.
        if (normalized == "accept-encoding") return
        if (normalized.any { it <= ' ' || it == ':' }) return
        if (value.any { it == '\r' || it == '\n' }) return
        result += KatHttp3Header(normalized, value)
    }
    headers.forEach { name, values -> values.forEach { add(name, it) } }
    body.headers.forEach { name, values -> values.forEach { add(name, it) } }
    result += KatHttp3Header("accept-encoding", "identity")
    return result
}

/** HTTP/3 carries authority and connection management in pseudo headers/QUIC. */
private val HTTP3_FORBIDDEN_REQUEST_HEADERS = setOf(
    "connection",
    "keep-alive",
    "proxy-connection",
    "transfer-encoding",
    "upgrade",
    "host",
)

private fun List<KatHttp3Header>.toKtorHeaders(): HeadersBuilder {
    val builder = HeadersBuilder()
    forEach { builder.append(it.name, it.value) }
    return builder
}

private suspend fun OutgoingContent.bytes(): ByteArray? = when (this) {
    is OutgoingContent.NoContent -> null
    is OutgoingContent.ByteArrayContent -> bytes()
    is OutgoingContent.ReadChannelContent -> ByteArrayOutputStream().use { output ->
        readFrom().copyTo(output)
        output.toByteArray()
    }

    is OutgoingContent.ContentWrapper -> delegate().bytes()
    else -> error("Unsupported kathttp3 request body: ${this::class.simpleName}")
}

/** Creates a Ktor client whose requests use the kathttp3 HTTP/3 transport. */
public fun createKatHttp3Client(
    config: KatHttp3EngineConfig.() -> Unit = {},
    block: HttpClientConfig<*>.() -> Unit = {},
): HttpClient {
    val engineConfig = KatHttp3EngineConfig().apply(config)
    return HttpClient(KatHttp3ClientEngine(engineConfig), block)
}
