@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.vimusic.android.extractor

import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.atomic.AtomicLong

class YoutubeHttpDataSourceFactory(
    private val rnParameterEnabled: Boolean,
    upstreamFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory()
) : DataSource.Factory {
    private val requestNumber = AtomicLong()
    private val upstream = upstreamFactory

    override fun createDataSource(): DataSource = Source(upstream.createDataSource())

    private inner class Source(
        private val upstream: DataSource
    ) : DataSource by upstream {
        override fun open(dataSpec: DataSpec): Long {
            val youtubeDataSpec = dataSpec.forYoutube()
            return runCatching {
                upstream.open(youtubeDataSpec)
            }.onFailure { error ->
                val response = error as? InvalidResponseCodeException
                    ?: error.cause as? InvalidResponseCodeException
                if (response != null) {
                    Log.w(
                        TAG,
                        "YouTube stream request failed status=${response.responseCode} " +
                            "method=${youtubeDataSpec.httpMethodName} " +
                            "uri=${youtubeDataSpec.uri}"
                    )
                }
            }.getOrThrow()
        }
    }

    private fun DataSpec.forYoutube(): DataSpec {
        val originalUrl = uri.toString()
        val isVideoPlaybackUrl = uri.path?.startsWith("/videoplayback") == true
        val isMobileStreamingUrl =
            YoutubeParsingHelper.isAndroidStreamingUrl(originalUrl) ||
                YoutubeParsingHelper.isIosStreamingUrl(originalUrl)
        var requestUrl = originalUrl

        if (isVideoPlaybackUrl && rnParameterEnabled && !requestUrl.contains(RN_PARAMETER)) {
            requestUrl += "$RN_PARAMETER${requestNumber.getAndIncrement()}"
        }

        val builder = buildUpon()

        // Googlevideo accepts regular HTTP Range for both GET and the Android
        // POST playback request. Keep Media3's requested position intact so
        // the upstream can require 206 + Content-Range.
        builder.setHttpRequestHeaders(youtubeHeaders(requestUrl, httpRequestHeaders))

        if (isVideoPlaybackUrl && isMobileStreamingUrl) {
            builder.setHttpMethod(DataSpec.HTTP_METHOD_POST)
        }

        return builder
            .setUri(Uri.parse(requestUrl))
            .build()
    }

    private fun youtubeHeaders(
        requestUrl: String,
        originalHeaders: Map<String, String>,
    ): Map<String, String> = buildMap {
        putAll(originalHeaders)

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

    private val DataSpec.httpMethodName: String
        get() = when (httpMethod) {
            DataSpec.HTTP_METHOD_GET -> "GET"
            DataSpec.HTTP_METHOD_POST -> "POST"
            DataSpec.HTTP_METHOD_HEAD -> "HEAD"
            else -> httpMethod.toString()
        }

    companion object {
        private const val TAG = "YoutubeHttpDataSource"
        private const val RN_PARAMETER = "&rn="
        private const val YOUTUBE_BASE_URL = "https://www.youtube.com"
    }
}
