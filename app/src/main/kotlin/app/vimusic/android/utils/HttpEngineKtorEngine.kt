package app.vimusic.android.utils

import android.net.http.HttpEngine
import app.vimusic.providers.utils.ProviderHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.call.ResponseAdapterAttributeKey
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.convertLongTimeoutToIntWithInfiniteAsZero
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HeadersImpl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.CoroutineContext

private class HttpEngineKtorConfig : HttpClientEngineConfig()

/** Ktor-compatible Android transport that delegates connections to HTTP/3-capable [HttpEngine]. */
@OptIn(InternalAPI::class)
private class HttpEngineKtorEngine(
    override val config: HttpEngineKtorConfig,
    private val httpEngine: HttpEngine,
) : HttpClientEngineBase("HttpEngine") {
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability)

    override suspend fun execute(data: HttpRequestData): HttpResponseData = withContext(Dispatchers.IO) {
        // Do not bind the response body to HttpRequestData.executionContext. Ktor consumes the
        // returned channel after execute() completes (notably in Coil), and cancelling that job
        // here closes HttpURLConnection before the decoder has read the image.
        val callContext: CoroutineContext = this@HttpEngineKtorEngine.coroutineContext + Dispatchers.IO
        val requestTime = GMTDate()
        val content = data.body
        val contentLength = data.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: content.contentLength
        val connection = (httpEngine.openConnection(URL(data.url.toString())) as HttpURLConnection).apply {
            requestMethod = data.method.value
            useCaches = false
            instanceFollowRedirects = false
            data.headers.forEach { name, values -> values.forEach { addRequestProperty(name, it) } }
            content.headers.forEach { name, values -> values.forEach { addRequestProperty(name, it) } }
            data.getCapabilityOrNull(HttpTimeoutCapability)?.let { timeout ->
                timeout.connectTimeoutMillis?.let { connectTimeout = convertLongTimeoutToIntWithInfiniteAsZero(it) }
                timeout.socketTimeoutMillis?.let { readTimeout = convertLongTimeoutToIntWithInfiniteAsZero(it) }
            }
            if (data.method.value != "GET" && data.method.value != "HEAD" && content !is OutgoingContent.NoContent) {
                contentLength?.let(::setFixedLengthStreamingMode) ?: setChunkedStreamingMode(0)
                doOutput = true
                content.writeTo(outputStream, callContext)
            }
        }

        try {
            val code = connection.responseCode
            val message = connection.responseMessage
            val status = message?.let { HttpStatusCode(code, it) } ?: HttpStatusCode.fromValue(code)
            val headers = HeadersImpl(
                connection.headerFields
                    .mapKeys { it.key?.lowercase(Locale.getDefault()) ?: "" }
                    .filterKeys(String::isNotBlank)
            )
            val responseBody = data.attributes.getOrNull(ResponseAdapterAttributeKey)
                ?.adapt(data, status, headers, connection.content(code, callContext), content, callContext)
                ?: connection.content(code, callContext)
            HttpResponseData(
                status,
                requestTime,
                headers,
                HttpProtocolVersion.HTTP_1_1,
                responseBody,
                callContext,
            )
        } catch (error: Throwable) { throw error }
    }
}

@OptIn(DelicateCoroutinesApi::class)
private suspend fun OutgoingContent.writeTo(stream: OutputStream, callContext: CoroutineContext) = stream.use { output ->
    when (this) {
        is OutgoingContent.ByteArrayContent -> output.write(bytes())
        is OutgoingContent.ReadChannelContent -> readFrom().copyTo(output)
        is OutgoingContent.WriteChannelContent -> GlobalScope.writer(callContext) {
            writeTo(channel)
        }.channel.copyTo(output)
        is OutgoingContent.NoContent -> Unit
        is OutgoingContent.ContentWrapper -> delegate().writeTo(output, callContext)
        is OutgoingContent.ProtocolUpgrade -> error("Protocol upgrades are unsupported by HttpEngine")
    }
}

private fun HttpURLConnection.content(status: Int, context: CoroutineContext): ByteReadChannel {
    if (status == HttpStatusCode.NoContent.value || status == HttpStatusCode.NotModified.value) return ByteReadChannel.Empty
    return try {
        inputStream?.buffered()
    } catch (_: IOException) {
        errorStream?.buffered()
    }?.toByteReadChannel(context = context) ?: ByteReadChannel.Empty
}

fun installHttpEngineKtorClient(httpEngine: HttpEngine) {
    ProviderHttpClient.install { block: HttpClientConfig<*>.() -> Unit ->
        newHttpEngineKtorClient(httpEngine, block)
    }
}

/** Creates an independent Ktor client for Coil while retaining its normal streaming/cancellation contract. */
fun newHttpEngineKtorClient(
    httpEngine: HttpEngine,
    block: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(HttpEngineKtorEngine(HttpEngineKtorConfig(), httpEngine), block)
