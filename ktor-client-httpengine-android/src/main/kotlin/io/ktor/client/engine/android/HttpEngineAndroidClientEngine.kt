package io.ktor.client.engine.android

import android.net.http.HttpEngine
import app.vimusic.ktor.httpengine.KtorCoreBridge
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.convertLongTimeoutToIntWithInfiniteAsZero
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.ResponseAdapterAttributeKey
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Ktor Android engine with the standard call lifecycle and an HttpEngine connection factory. */
@OptIn(InternalAPI::class)
public class HttpEngineAndroidClientEngine(
    override val config: HttpEngineAndroidEngineConfig,
) : HttpClientEngineBase("ktor-httpengine-android") {
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability)

    override suspend fun execute(data: HttpRequestData): HttpResponseData = withContext(Dispatchers.IO) {
        val callContext = ktorCallContext()
        val requestTime = GMTDate()
        val outgoingContent = data.body
        val contentLength = outgoingContent.contentLength
        val connection = config.connectionFactory.open(java.net.URL(data.url.toString())).apply {
            connectTimeout = config.connectTimeout
            readTimeout = config.socketTimeout
            data.getCapabilityOrNull(HttpTimeoutCapability)?.let { timeout ->
                timeout.connectTimeoutMillis?.let { connectTimeout = convertLongTimeoutToIntWithInfiniteAsZero(it) }
                timeout.socketTimeoutMillis?.let { readTimeout = convertLongTimeoutToIntWithInfiniteAsZero(it) }
            }
            requestMethod = data.method.value
            useCaches = false
            instanceFollowRedirects = false
            data.headers.forEach { name, values ->
                if (!name.equals(HttpHeaders.ContentLength, true)) values.forEach { addRequestProperty(name, it) }
            }
            outgoingContent.headers.forEach { name, values ->
                if (!name.equals(HttpHeaders.ContentLength, true)) values.forEach { addRequestProperty(name, it) }
            }
            config.requestConfig(this)
            if (data.method.value != "GET" && data.method.value != "HEAD" && outgoingContent !is OutgoingContent.NoContent) {
                contentLength?.let(::setFixedLengthStreamingMode) ?: setChunkedStreamingMode(0)
                doOutput = true
                outgoingContent.writeTo(outputStream, callContext)
            }
        }

        val code = connection.responseCode
        val status = connection.responseMessage?.let { HttpStatusCode(code, it) } ?: HttpStatusCode.fromValue(code)
        val headers = HeadersImpl(connection.headerFields.mapKeys { it.key?.lowercase(Locale.getDefault()) ?: "" }.filterKeys(String::isNotBlank))
        val content = connection.content(code, callContext)
        val body = data.attributes.getOrNull(ResponseAdapterAttributeKey)
            ?.adapt(data, status, headers, content, outgoingContent, callContext)
            ?: content
        HttpResponseData(status, requestTime, headers, HttpProtocolVersion.HTTP_1_1, body, callContext)
    }
}

private suspend fun ktorCallContext(): CoroutineContext = suspendCoroutineUninterceptedOrReturn { continuation ->
    KtorCoreBridge.callContext(continuation)
}

/** Creates a Ktor client whose connection layer is Android's HTTP/3-capable HttpEngine. */
public fun createHttpEngineAndroidClient(
    httpEngine: HttpEngine,
    block: HttpClientConfig<*>.() -> Unit = {},
): HttpClient {
    val config = HttpEngineAndroidEngineConfig().apply {
        connectionFactory = httpEngine.asKtorConnectionFactory()
    }
    return HttpClient(HttpEngineAndroidClientEngine(config), block)
}

private suspend fun OutgoingContent.writeTo(stream: OutputStream, context: CoroutineContext): Unit = stream.use { output ->
    when (this) {
        is OutgoingContent.ByteArrayContent -> output.write(bytes())
        is OutgoingContent.ReadChannelContent -> readFrom().copyTo(output)
        is OutgoingContent.NoContent -> Unit
        is OutgoingContent.ContentWrapper -> delegate().writeTo(output, context)
        else -> error("Unsupported HttpEngine request body: ${this::class.simpleName}")
    }
}

private fun HttpURLConnection.content(status: Int, context: CoroutineContext): ByteReadChannel {
    if (status == HttpStatusCode.NoContent.value || status == HttpStatusCode.NotModified.value) return ByteReadChannel.Empty
    return try { inputStream?.buffered() } catch (_: IOException) { errorStream?.buffered() }
        ?.toByteReadChannel(context = context) ?: ByteReadChannel.Empty
}
