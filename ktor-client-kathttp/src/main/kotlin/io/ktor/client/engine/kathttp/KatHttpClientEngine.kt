package io.ktor.client.engine.kathttp

import android.util.Log
import dev.kathttp.KatHttpClient
import dev.kathttp.KatHttpHeader
import dev.kathttp.KatHttpRequest
import dev.kathttp.KatHttpStreamEvent
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Ktor transport backed by kathttp, a standalone ngtcp2/nghttp3/BoringSSL HTTP/3 client. */
@OptIn(InternalAPI::class)
public class KatHttpClientEngine(
    override val config: KatHttpEngineConfig,
) : HttpClientEngineBase("ktor-kathttp") {
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability)

    private val ownsClient = config.client == null
    private val client: KatHttpClient = config.client ?: KatHttpClient(config.clientConfig)

    override suspend fun execute(data: HttpRequestData): HttpResponseData = withContext(Dispatchers.IO) {
        val callContext = coroutineContext
        val url = data.url.toString()
        require(url.startsWith("https://")) { "kathttp only supports https:// URLs (got $url)" }

        val outgoingContent = data.body
        val request = KatHttpRequest(
            method = data.method.value.uppercase(),
            url = url,
            headers = data.buildKatHeaders(),
            body = outgoingContent.bytes(),
        )

        val call = client.executeStreaming(request)
        val started = CompletableDeferred<StartedResponse>()
        val channel = ByteChannel(autoFlush = true)

        val job = launch(callContext) {
            try {
                call.events.collect { event ->
                    when (event) {
                        is KatHttpStreamEvent.Headers -> if (!started.isCompleted) {
                            Log.i(TAG, "$url negotiated h3 status ${event.status}")
                            started.complete(
                                StartedResponse(
                                    status = HttpStatusCode.fromValue(event.status),
                                    headers = event.headers.toKtorHeaders(),
                                ),
                            )
                        }

                        is KatHttpStreamEvent.Body -> channel.writeFully(event.bytes)
                    }
                }
                channel.flushAndClose()
                if (!started.isCompleted) {
                    started.completeExceptionally(IllegalStateException("kathttp completed without a response for $url"))
                }
            } catch (cause: Throwable) {
                if (!started.isCompleted) started.completeExceptionally(cause)
                channel.cancel(cause)
            }
        }
        job.invokeOnCompletion { cause -> if (cause != null) call.cancel() }

        val response = started.await()
        val headers = response.headers.build()
        val body = data.attributes.getOrNull(ResponseAdapterAttributeKey)
            ?.adapt(data, response.status, headers, channel, outgoingContent, callContext)
            ?: channel
        HttpResponseData(
            statusCode = response.status,
            requestTime = GMTDate(),
            headers = headers,
            version = HTTP_3,
            body = body,
            callContext = callContext,
        )
    }

    override fun close() {
        super.close()
        if (ownsClient) runCatching { client.close() }
    }

    private companion object {
        const val TAG = "KatHttpKtor"
        val HTTP_3 = HttpProtocolVersion("HTTP", 3, 0)
    }
}

private class StartedResponse(
    val status: HttpStatusCode,
    val headers: HeadersBuilder,
)

private fun HttpRequestData.buildKatHeaders(): List<KatHttpHeader> {
    val result = ArrayList<KatHttpHeader>()
    fun add(name: String, value: String) {
        if (name.equals(HttpHeaders.ContentLength, ignoreCase = true)) return
        val normalized = name.lowercase()
        if (normalized.any { it <= ' ' || it == ':' }) return
        if (value.any { it == '\r' || it == '\n' }) return
        result += KatHttpHeader(normalized, value)
    }
    headers.forEach { name, values -> values.forEach { add(name, it) } }
    body.headers.forEach { name, values -> values.forEach { add(name, it) } }
    return result
}

private fun List<KatHttpHeader>.toKtorHeaders(): HeadersBuilder {
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
    else -> error("Unsupported kathttp request body: ${this::class.simpleName}")
}

/** Creates a Ktor client whose requests use the kathttp HTTP/3 transport. */
public fun createKatHttpClient(
    config: KatHttpEngineConfig.() -> Unit = {},
    block: HttpClientConfig<*>.() -> Unit = {},
): HttpClient {
    val engineConfig = KatHttpEngineConfig().apply(config)
    return HttpClient(KatHttpClientEngine(engineConfig), block)
}
