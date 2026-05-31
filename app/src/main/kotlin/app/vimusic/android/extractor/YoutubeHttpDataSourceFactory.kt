package app.vimusic.android.extractor

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.atomic.AtomicLong

class YoutubeHttpDataSourceFactory(
    private val rangeParameterEnabled: Boolean,
    private val rnParameterEnabled: Boolean
) : DataSource.Factory {
    private val requestNumber = AtomicLong()
    private val upstream = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(DEFAULT_CONNECT_TIMEOUT_MS)
        .setReadTimeoutMs(DEFAULT_READ_TIMEOUT_MS)
        .setUserAgent(NewPipeDownloader.USER_AGENT)

    override fun createDataSource(): DataSource = Source(upstream.createDataSource())

    private inner class Source(
        private val upstream: DataSource
    ) : DataSource by upstream {
        override fun open(dataSpec: DataSpec): Long = upstream.open(dataSpec.forYoutube())
    }

    private fun DataSpec.forYoutube(): DataSpec {
        val originalUrl = uri.toString()
        val isVideoPlaybackUrl = uri.path?.startsWith("/videoplayback") == true
        var requestUrl = originalUrl

        if (isVideoPlaybackUrl && rnParameterEnabled && !requestUrl.contains(RN_PARAMETER)) {
            requestUrl += "$RN_PARAMETER${requestNumber.getAndIncrement()}"
        }

        val builder = buildUpon()
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(POST_BODY)

        if (rangeParameterEnabled && isVideoPlaybackUrl) {
            buildRangeParameter(position, length)?.let { requestUrl += it }
            builder
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .setHttpRequestHeaders(youtubeHeaders(requestUrl, httpRequestHeaders, dropRange = true))
        } else {
            builder.setHttpRequestHeaders(youtubeHeaders(requestUrl, httpRequestHeaders, dropRange = false))
        }

        return builder
            .setUri(Uri.parse(requestUrl))
            .build()
    }

    private fun youtubeHeaders(
        requestUrl: String,
        originalHeaders: Map<String, String>,
        dropRange: Boolean
    ): Map<String, String> = buildMap {
        originalHeaders.forEach { (key, value) ->
            if (!dropRange || !key.equals("Range", ignoreCase = true)) {
                put(key, value)
            }
        }

        if (
            YoutubeParsingHelper.isWebStreamingUrl(requestUrl) ||
            YoutubeParsingHelper.isWebEmbeddedPlayerStreamingUrl(requestUrl)
        ) {
            put("Origin", YOUTUBE_BASE_URL)
            put("Referer", YOUTUBE_BASE_URL)
            put("Sec-Fetch-Dest", "empty")
            put("Sec-Fetch-Mode", "cors")
            put("Sec-Fetch-Site", "cross-site")
        }

        put("TE", "trailers")
        put("User-Agent", userAgentFor(requestUrl))
        put("Accept-Encoding", "identity")
    }

    private fun userAgentFor(requestUrl: String) = when {
        YoutubeParsingHelper.isAndroidStreamingUrl(requestUrl) ->
            YoutubeParsingHelper.getAndroidUserAgent(null)
        YoutubeParsingHelper.isIosStreamingUrl(requestUrl) ->
            YoutubeParsingHelper.getIosUserAgent(null)
        else -> NewPipeDownloader.USER_AGENT
    }

    private fun buildRangeParameter(position: Long, length: Long): String? {
        if (position == 0L && length == C.LENGTH_UNSET.toLong()) return null

        return buildString {
            append(RANGE_PARAMETER)
            append(position)
            append("-")
            if (length != C.LENGTH_UNSET.toLong()) {
                append(position + length - 1)
            }
        }
    }

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000
        private const val DEFAULT_READ_TIMEOUT_MS = 8_000
        private const val RN_PARAMETER = "&rn="
        private const val RANGE_PARAMETER = "&range="
        private const val YOUTUBE_BASE_URL = "https://www.youtube.com"
        private val POST_BODY = byteArrayOf(0x78, 0)
    }
}
