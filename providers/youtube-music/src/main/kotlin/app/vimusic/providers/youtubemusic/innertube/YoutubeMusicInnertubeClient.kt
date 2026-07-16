package app.vimusic.providers.youtubemusic.innertube

import app.vimusic.providers.youtubemusic.innertube.models.Context
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.NextBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.PlayerBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.QueueBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.SearchBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.SearchSuggestionsBody
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Request
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Small, buffered client for YouTube Music's private API.
 *
 * NewPipe's Downloader already owns HTTP/3 selection and HTTP/2 fallback.
 * Keeping the completed response as a String avoids Ktor response channels,
 * content-encoding plugins and DoubleReceive entirely.
 */
internal class YoutubeMusicInnertubeClient(
    private val baseUrl: String = "https://music.youtube.com",
    private val defaultApiKey: String? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    suspend inline fun <reified T> post(
        path: String,
        body: Any,
        fieldMask: String? = null,
        parameters: Map<String, String> = emptyMap(),
        context: Context? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): T = json.decodeFromString(executePost(path, body, fieldMask, parameters, context, extraHeaders))

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        execute(
            method = "GET",
            url = url,
            headers = mapOf(
                "User-Agent" to app.vimusic.providers.youtubemusic.innertube.models.UserAgents.DESKTOP,
                "Accept-Encoding" to "identity",
            ),
            body = null,
        )
    }

    suspend fun executePost(
        path: String,
        body: Any,
        fieldMask: String? = null,
        parameters: Map<String, String> = emptyMap(),
        context: Context? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val apiKey = context?.client?.apiKey ?: defaultApiKey
        val query = linkedMapOf("prettyPrint" to "false")
        if (apiKey != null) query["key"] = apiKey
        query.putAll(parameters)
        val url = buildUrl(path, query)
        val host = java.net.URI(url).host
        val originHost = if (host == "youtubei.googleapis.com") "www.youtube.com" else host
        val origin = "https://$originHost"
        val headers = linkedMapOf(
            "Content-Type" to "application/json",
            "Accept-Encoding" to "identity",
            "Connection" to "close",
            "Cache-Control" to "no-cache",
            "Host" to originHost,
            "X-Origin" to origin,
            "Origin" to origin,
            "X-YouTube-Bootstrap-Logged-In" to "false",
        )
        headers["User-Agent"] = context?.client?.userAgent
            ?: app.vimusic.providers.youtubemusic.innertube.models.UserAgents.DESKTOP
        context?.let {
            headers["X-YouTube-Client-Name"] = it.client.clientName
            headers["X-YouTube-Client-Version"] = it.client.clientVersion
            it.client.referer?.let { referer -> headers["Referer"] = referer }
        }
        apiKey?.let { headers["X-Goog-Api-Key"] = it }
        fieldMask?.let { headers["X-Goog-FieldMask"] = it }
        headers.putAll(extraHeaders)
        execute("POST", url, headers, encodeBody(body).encodeToByteArray())
    }

    private fun buildUrl(path: String, parameters: Map<String, String>): String {
        val url = if (path.startsWith("https://")) path else "$baseUrl$path"
        val separator = if ('?' in url) '&' else '?'
        return url + separator + parameters.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    private fun execute(method: String, url: String, headers: Map<String, String>, body: ByteArray?): String {
        val request = Request.newBuilder()
            .httpMethod(method)
            .url(url)
            .headers(headers.mapValues { listOf(it.value) })
            .dataToSend(body)
            .automaticLocalizationHeader(false)
            .build()
        val response = requireNotNull(NewPipe.getDownloader()) {
            "NewPipe is not initialized; call NewPipeExtractorClient.ensureInitialized first"
        }.execute(request)
        if (response.responseCode() !in 200..299) {
            throw IOException("HTTP ${response.responseCode()} for $url")
        }
        return response.responseBody()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    /**
     * Request payloads are a closed set in this provider.  Do not serialize
     * them through `Any`: Kotlin serialization has no runtime serializer for
     * that type and would fail only after the first user request.
     */
    private fun encodeBody(body: Any): String = when (body) {
        is BrowseBody -> json.encodeToString(body)
        is ContinuationBody -> json.encodeToString(body)
        is NextBody -> json.encodeToString(body)
        is PlayerBody -> json.encodeToString(body)
        is QueueBody -> json.encodeToString(body)
        is SearchBody -> json.encodeToString(body)
        is SearchSuggestionsBody -> json.encodeToString(body)
        else -> error("Unsupported YouTube Music request body: ${body::class.qualifiedName}")
    }
}
