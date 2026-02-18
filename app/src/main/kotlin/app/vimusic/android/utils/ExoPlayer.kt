@file:OptIn(UnstableApi::class)

package app.vimusic.android.utils

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import java.io.EOFException
import java.io.IOException

class InvalidPlaybackResponseException(message: String) : IOException(message)

private class ValidatingHttpDataSourceFactory(
    private val parent: HttpDataSource.Factory
) : HttpDataSource.Factory by parent {
    override fun createDataSource(): HttpDataSource = Source(parent.createDataSource())

    private class Source(private val parent: HttpDataSource) : HttpDataSource by parent {
        override fun open(dataSpec: DataSpec): Long {
            val openedLength = parent.open(dataSpec)
            validateResponse(dataSpec, parent.responseHeaders, openedLength)
            return openedLength
        }
    }
}

private data class ByteRange(val start: Long, val end: Long) {
    val length: Long get() = end - start + 1
}

private fun parseRequestedRange(value: String?): ByteRange? {
    if (value.isNullOrBlank()) return null
    val raw = value.trim()
    if (!raw.startsWith("bytes=", ignoreCase = true)) return null
    val rangePart = raw.substringAfter('=').substringBefore(',')
    val start = rangePart.substringBefore('-').trim().toLongOrNull() ?: return null
    val endPart = rangePart.substringAfter('-', "").trim()
    val end = endPart.toLongOrNull() ?: return null
    if (end < start) return null
    return ByteRange(start = start, end = end)
}

private fun parseContentRange(value: String?): ByteRange? {
    if (value.isNullOrBlank()) return null
    val raw = value.trim()
    if (!raw.startsWith("bytes ", ignoreCase = true)) return null
    val rangePart = raw.substringAfter(' ').substringBefore('/')
    val start = rangePart.substringBefore('-').trim().toLongOrNull() ?: return null
    val end = rangePart.substringAfter('-', "").trim().toLongOrNull() ?: return null
    if (end < start) return null
    return ByteRange(start = start, end = end)
}

private fun validateResponse(
    dataSpec: DataSpec,
    headers: Map<String, List<String>>,
    openedLength: Long
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()

    @Suppress("UNCHECKED_CAST")
    fun statusCode(): Int? {
        val statusLine = (headers as Map<String?, List<String>>)[null]?.firstOrNull()
            ?: header(":status")
        return statusLine
            ?.split(' ')
            ?.lastOrNull()
            ?.toIntOrNull()
    }

    val statusCode = statusCode()
    val requestedRange = parseRequestedRange(dataSpec.httpRequestHeaders["Range"])
    val contentRange = parseContentRange(header("Content-Range"))
    val contentLength = header("Content-Length")?.toLongOrNull()

    // Full-response fallback from server is allowed; prioritize uninterrupted playback.
    if (statusCode == 200) return

    if (contentRange != null && contentLength != null && contentRange.length != contentLength) {
        throw InvalidPlaybackResponseException(
            "Invalid response: Content-Range length ${contentRange.length} != Content-Length $contentLength"
        )
    }

    if (requestedRange != null && contentRange != null) {
        if (requestedRange.start != contentRange.start || requestedRange.end != contentRange.end) {
            throw InvalidPlaybackResponseException(
                "Invalid response: requested range ${requestedRange.start}-${requestedRange.end} " +
                        "!= response range ${contentRange.start}-${contentRange.end}"
            )
        }
    }

    if (statusCode == 206 && contentRange == null) {
        throw InvalidPlaybackResponseException(
            "Invalid response: 206 Partial Content without Content-Range"
        )
    }

    if (
        requestedRange != null &&
        contentRange == null &&
        contentLength != null &&
        openedLength != contentLength
    ) {
        throw InvalidPlaybackResponseException(
            "Invalid response: ranged request without Content-Range and length mismatch"
        )
    }

    if (contentLength != null && openedLength != C.LENGTH_UNSET.toLong() && openedLength != contentLength) {
        throw InvalidPlaybackResponseException(
            "Invalid response: openedLength $openedLength != Content-Length $contentLength"
        )
    }
}

class RangeHandlerDataSourceFactory(private val parent: DataSource.Factory) : DataSource.Factory {
    class Source(private val parent: DataSource) : DataSource by parent {
        override fun open(dataSpec: DataSpec) = runCatching {
            parent.open(dataSpec)
        }.getOrElse { e ->
            if (
                e.findCause<EOFException>() != null ||
                e.findCause<InvalidResponseCodeException>()?.responseCode == 416
            ) parent.open(
                dataSpec
                    .buildUpon()
                    .setHttpRequestHeaders(
                        dataSpec.httpRequestHeaders.filter {
                            it.key.equals("range", ignoreCase = true)
                        }
                    )
                    .setLength(C.LENGTH_UNSET.toLong())
                    .build()
            )
            else throw e
        }
    }

    override fun createDataSource() = Source(parent.createDataSource())
}

class CatchingDataSourceFactory(
    private val parent: DataSource.Factory,
    private val onError: ((Throwable) -> Unit)?
) : DataSource.Factory {
    inner class Source(private val parent: DataSource) : DataSource by parent {
        override fun open(dataSpec: DataSpec) = runCatching {
            parent.open(dataSpec)
        }.getOrElse { ex ->
            ex.printStackTrace()

            if (ex is PlaybackException) throw ex
            else throw PlaybackException(
                /* message = */ "Unknown playback error",
                /* cause = */ ex,
                /* errorCode = */ PlaybackException.ERROR_CODE_UNSPECIFIED
            ).also { onError?.invoke(it) }
        }
    }

    override fun createDataSource() = Source(parent.createDataSource())
}

fun DataSource.Factory.handleRangeErrors(): DataSource.Factory = RangeHandlerDataSourceFactory(this)
fun DataSource.Factory.handleUnknownErrors(
    onError: ((Throwable) -> Unit)? = null
): DataSource.Factory = CatchingDataSourceFactory(
    parent = this,
    onError = onError
)

val Cache.asDataSource
    get() = CacheDataSource.Factory()
        .setCache(this)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

val Context.defaultDataSource
    get() = DefaultDataSource.Factory(
        this,
        ValidatingHttpDataSourceFactory(
            DefaultHttpDataSource.Factory().setConnectTimeoutMs(16000)
                .setReadTimeoutMs(8000)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0")
        )
    )
