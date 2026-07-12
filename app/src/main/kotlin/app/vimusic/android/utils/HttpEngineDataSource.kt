package app.vimusic.android.utils

import android.net.Uri
import android.net.http.HttpEngine
import android.net.http.HttpException
import android.net.http.UploadDataProvider
import android.net.http.UploadDataSink
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Media3 data source built on HttpEngine UrlRequest rather than its opaque adapter.
 *
 * This keeps the normal streaming/range-request contract while exposing the protocol that was
 * actually negotiated. It is deliberately separate from Media3's final HttpEngineDataSource,
 * whose callback (and therefore UrlResponseInfo.negotiatedProtocol) is not public.
 */
class HttpEngineUrlRequestDataSource(
    private val httpEngine: HttpEngine,
    private val executor: Executor = EXECUTOR,
) : BaseDataSource(true), HttpDataSource {
    private val requestProperties = LinkedHashMap<String, String>()
    private var dataSpec: DataSpec? = null
    private var request: UrlRequest? = null
    private var responseCode = -1
    private var responseHeaders: Map<String, List<String>> = emptyMap()
    private var responseUri: Uri? = null
    private var callback: Callback? = null
    private var currentChunk: Chunk.Data? = null
    private var opened = false

    override fun setRequestProperty(name: String, value: String) { requestProperties[name] = value }
    override fun clearRequestProperty(name: String) { requestProperties.remove(name) }
    override fun clearAllRequestProperties() { requestProperties.clear() }
    override fun getResponseCode(): Int = responseCode
    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders
    override fun getUri(): Uri? = responseUri

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)
        try {
            val callback = Callback()
            this.callback = callback
            val builder = httpEngine.newUrlRequestBuilder(dataSpec.uri.toString(), executor, callback).apply {
                setHttpMethod(dataSpec.httpMethodString)
                requestProperties.forEach(::addHeader)
                dataSpec.httpRequestHeaders.forEach(::addHeader)
                if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) "" else dataSpec.position + dataSpec.length - 1
                    addHeader("Range", "bytes=${dataSpec.position}-$end")
                }
                dataSpec.httpBody?.let { setUploadDataProvider(ByteArrayUploadDataProvider(it), executor) }
            }
            request = builder.build().also(UrlRequest::start)
            if (!callback.started.await(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                request?.cancel()
                throw IOException("HttpEngine response timed out for ${dataSpec.uri}")
            }
            callback.error?.let { throw it }
            val info = callback.responseInfo ?: throw IOException("HttpEngine returned no response for ${dataSpec.uri}")
            responseCode = info.httpStatusCode
            responseHeaders = info.headers.asMap
            responseUri = Uri.parse(info.url)
            if (responseCode !in 200..299) throw IOException("Unexpected HTTP response $responseCode for ${dataSpec.uri}")
            opened = true
            transferStarted(dataSpec)
            return contentLength(info, dataSpec)
        } catch (error: IOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(error, dataSpec, HttpDataSource.HttpDataSourceException.TYPE_OPEN)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val spec = dataSpec ?: throw IOException("DataSource is not open")
        // A DataSource read is usually smaller than the UrlRequest callback buffer. Keep the
        // unread tail locally: putting it back at the end of the queue would move it behind
        // later network chunks and corrupt the byte stream (for example MP4's ftyp atom).
        val item = currentChunk ?: try { callback?.chunks?.take() } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while reading HttpEngine response", error)
        } ?: return C.RESULT_END_OF_INPUT
        when (item) {
            is Chunk.Data -> {
                val count = minOf(length, item.bytes.size - item.offset)
                item.bytes.copyInto(buffer, offset, item.offset, item.offset + count)
                item.offset += count
                currentChunk = item.takeIf { it.offset < it.bytes.size }
                bytesTransferred(count)
                return count
            }
            Chunk.End -> return C.RESULT_END_OF_INPUT
            is Chunk.Failure -> throw HttpDataSource.HttpDataSourceException.createForIOException(item.error, spec, HttpDataSource.HttpDataSourceException.TYPE_READ)
        }
    }

    override fun close() {
        request?.cancel()
        request = null
        callback?.close()
        callback = null
        currentChunk = null
        responseUri = null
        responseHeaders = emptyMap()
        responseCode = -1
        dataSpec = null
        if (opened) { opened = false; transferEnded() }
    }

    private fun contentLength(info: UrlResponseInfo, spec: DataSpec): Long {
        if (spec.length != C.LENGTH_UNSET.toLong()) return spec.length
        val length = info.headers.asMap.entries.firstOrNull { it.key.equals("Content-Length", true) }
            ?.value?.firstOrNull()?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
        return length
    }

    private inner class Callback : UrlRequest.Callback {
        val started = CountDownLatch(1)
        val chunks = LinkedBlockingQueue<Chunk>()
        var responseInfo: UrlResponseInfo? = null
        var error: IOException? = null

        override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
            Log.d(TAG, "Following redirect ${info.url} -> $newLocationUrl")
            request.followRedirect()
        }

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            responseInfo = info
            Log.i(TAG, "${info.url} negotiated ${info.negotiatedProtocol.ifBlank { "HTTP/1.1" }}")
            started.countDown()
            request.read(ByteBuffer.allocateDirect(BUFFER_SIZE))
        }

        override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, buffer: ByteBuffer) {
            buffer.flip()
            if (buffer.hasRemaining()) {
                ByteArray(buffer.remaining()).also { bytes -> buffer.get(bytes); chunks.put(Chunk.Data(bytes)) }
            }
            buffer.clear()
            request.read(buffer)
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) { chunks.offer(Chunk.End) }
        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: HttpException) {
            this.error = IOException("HttpEngine request failed", error)
            started.countDown()
            chunks.offer(Chunk.Failure(this.error!!))
        }
        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
            val cancelled = IOException("HttpEngine request cancelled")
            if (responseInfo == null) { error = cancelled; started.countDown() }
            chunks.offer(Chunk.Failure(cancelled))
        }
        fun close() { chunks.clear(); chunks.offer(Chunk.End) }
    }

    private sealed interface Chunk {
        class Data(val bytes: ByteArray, var offset: Int = 0) : Chunk
        data object End : Chunk
        class Failure(val error: IOException) : Chunk
    }

    private class ByteArrayUploadDataProvider(private val bytes: ByteArray) : UploadDataProvider() {
        private var position = 0
        override fun getLength(): Long = bytes.size.toLong()
        override fun read(sink: UploadDataSink, buffer: ByteBuffer) {
            val count = minOf(buffer.remaining(), bytes.size - position)
            if (count > 0) { buffer.put(bytes, position, count); position += count }
            sink.onReadSucceeded(false)
        }
        override fun rewind(sink: UploadDataSink) { position = 0; sink.onRewindSucceeded() }
    }

    class Factory(private val httpEngine: HttpEngine) : HttpDataSource.Factory {
        private var defaultRequestProperties: Map<String, String> = emptyMap()
        override fun createDataSource(): HttpDataSource = HttpEngineUrlRequestDataSource(httpEngine).also { source ->
            defaultRequestProperties.forEach(source::setRequestProperty)
        }
        override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): Factory = apply {
            this.defaultRequestProperties = Collections.unmodifiableMap(LinkedHashMap(defaultRequestProperties))
        }
    }

    private companion object {
        const val TAG = "HttpEngineDataSource"
        const val BUFFER_SIZE = 32 * 1024
        const val OPEN_TIMEOUT_SECONDS = 30L
        val EXECUTOR: Executor = Executors.newCachedThreadPool()
    }
}
