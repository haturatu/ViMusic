package app.vimusic.android.extractor

import android.content.Context
import android.util.Log
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.downloader.Downloader
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

object NewPipeExtractorClient {
    @Volatile private var initialized = false
    private val initializationLock = Any()
    private val lock = ReentrantLock()
    private val downloaderLock = Any()
    private val inFlightAudioResolutionsLock = Any()
    private val inFlightAudioResolutions = mutableMapOf<String, FutureTask<NewPipeAudioResult>>()
    private val appContextRef = AtomicReference<Context?>()
    @Volatile private var sharedSystemDownloader: Downloader? = null
    private var lastSuccessfulDnsIndex: Int? = null

    fun ensureInitialized(context: Context) {
        appContextRef.set(context.applicationContext)
        ensureInitialized()
    }

    fun ensureInitialized() {
        if (initialized) return
        synchronized(initializationLock) {
            if (initialized) return
            configureDownloader(NewPipeDnsTarget.System)
            initialized = true
        }
    }

    @Throws(IOException::class, ExtractionException::class)
    fun resolveAudioStream(videoId: String): NewPipeAudioResult {
        ensureInitialized()
        return awaitAudioResolution(
            videoId = videoId,
            task = audioResolutionTask(videoId),
            timeoutSeconds = PLAYBACK_RESOLVE_TIMEOUT_SECONDS,
        )
    }

    /** Opportunistic preload that never waits behind an active playback resolve. */
    fun preloadAudioStream(videoId: String): NewPipeAudioResult? {
        ensureInitialized()
        val task = synchronized(inFlightAudioResolutionsLock) {
            inFlightAudioResolutions[videoId]
                ?: if (lock.isLocked) null else audioResolutionTaskLocked(videoId)
        } ?: return null

        return runCatching {
            awaitAudioResolution(
                videoId = videoId,
                task = task,
                timeoutSeconds = PRELOAD_RESOLVE_TIMEOUT_SECONDS,
            )
        }.getOrNull()
    }

    /**
     * Shares an in-flight extraction by video ID. A waiting caller timing out or being interrupted
     * must not cancel the shared NewPipe task: playback or a pre-cache request may still need it.
     * The task removes itself only after it has actually completed.
     */
    private fun audioResolutionTask(videoId: String): FutureTask<NewPipeAudioResult> =
        synchronized(inFlightAudioResolutionsLock) {
            audioResolutionTaskLocked(videoId)
        }

    private fun audioResolutionTaskLocked(videoId: String): FutureTask<NewPipeAudioResult> =
        inFlightAudioResolutions[videoId] ?: FutureTask {
            try {
                lock.lockInterruptibly()
                try {
                    resolveAudioStreamWithFallback(videoId)
                } finally {
                    lock.unlock()
                }
            } finally {
                synchronized(inFlightAudioResolutionsLock) {
                    inFlightAudioResolutions.remove(videoId)
                }
            }
        }.also { task ->
            inFlightAudioResolutions[videoId] = task
            RESOLVER_EXECUTOR.execute(task)
        }

    @Throws(IOException::class, ExtractionException::class)
    private fun awaitAudioResolution(
        videoId: String,
        task: FutureTask<NewPipeAudioResult>,
        timeoutSeconds: Long,
    ): NewPipeAudioResult = try {
        task.get(timeoutSeconds, TimeUnit.SECONDS)
    } catch (error: TimeoutException) {
        throw IOException("NewPipe stream resolution timed out for $videoId", error)
    } catch (error: ExecutionException) {
        val cause = error.cause ?: error
        when (cause) {
            is IOException -> throw cause
            is ExtractionException -> throw cause
            else -> throw IOException("NewPipe stream resolution failed for $videoId", cause)
        }
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IOException("NewPipe stream resolution interrupted for $videoId", error)
    }

    private fun resolveAudioStreamWithFallback(videoId: String): NewPipeAudioResult {
        var firstError: Throwable? = null
        var lastError: Throwable? = null

        dnsFallbackTargets().forEach { dnsTarget ->
            runCatching {
                Log.d(TAG, "Resolving audio stream videoId=$videoId dnsTarget=${dnsTarget.label}")
                val result = resolveAudioStream(videoId, dnsTarget)
                if (dnsTarget is NewPipeDnsTarget.Resolved) {
                    lastSuccessfulDnsIndex = dnsTarget.index
                }
                return result
            }.onFailure { error ->
                if (error.isInterruption()) {
                    Thread.currentThread().interrupt()
                    throw error
                }
                Log.w(TAG, "Audio stream resolve failed videoId=$videoId dnsTarget=${dnsTarget.label}", error)
                if (firstError == null) firstError = error
                lastError = error
            }
        }

        lastError?.let { error ->
            firstError?.takeIf { it !== error }?.let(error::addSuppressed)
            throw error
        }

        throw ExtractionException("No DNS fallback targets configured")
    }

    private fun Throwable.isInterruption(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is InterruptedException || current is InterruptedIOException) return true
            current = current.cause
        }
        return false
    }

    @Throws(IOException::class, ExtractionException::class)
    private fun resolveAudioStream(videoId: String, dnsTarget: NewPipeDnsTarget): NewPipeAudioResult {
        configureDownloader(dnsTarget)

        val url = "https://www.youtube.com/watch?v=$videoId"
        val service = ServiceList.YouTube as YoutubeService
        val streamInfo = StreamInfo.getInfo(service, url)
        logStreamInfo(videoId, dnsTarget, streamInfo)
        val audioStreams = getPlayableAudioStreams(streamInfo)
        val audioStream = selectDefaultAudioStream(audioStreams)
        val videoStream = if (audioStream == null) {
            selectDefaultMuxedVideoStream(getPlayableMuxedVideoStreams(streamInfo))
        } else {
            null
        }

        if (audioStream == null && videoStream == null) {
            throw ExtractionException(
                "No playable audio stream found; " +
                    "audio=${streamInfo.audioStreams?.size ?: 0} " +
                    "video=${streamInfo.videoStreams?.size ?: 0} " +
                    "videoOnly=${streamInfo.videoOnlyStreams?.size ?: 0} " +
                    "errors=${streamInfo.errors?.joinToString { it.message.orEmpty() }.orEmpty()}"
            )
        }

        if (audioStream != null) {
            Log.i(
                TAG,
                "Audio stream resolved videoId=$videoId dnsTarget=${dnsTarget.label} " +
                    "itag=${audioStream.itag} bitrate=${audioStream.averageBitrate}"
            )
        } else if (videoStream != null) {
            Log.i(
                TAG,
                "Muxed video stream resolved videoId=$videoId dnsTarget=${dnsTarget.label} " +
                    "itag=${videoStream.itag} bitrate=${videoStream.bitrate}"
            )
        }

        return NewPipeAudioResult(streamInfo, audioStreams, audioStream, videoStream)
    }

    private fun configureDownloader(dnsTarget: NewPipeDnsTarget) {
        NewPipe.init(
            createDownloader(dnsTarget),
            Localization.DEFAULT,
            ContentCountry.DEFAULT
        )
    }

    private fun createDownloader(dnsTarget: NewPipeDnsTarget): Downloader {
        val context = appContextRef.get()
        val fallback = NewPipeDownloader(NewPipeDownloader.client(dnsTarget))

        // kathttp3 resolves the URL hostname itself, so it is used for NewPipe's normal resolver
        // path. Fixed-address retry uses the OkHttp DNS override fallback.
        return if (dnsTarget == NewPipeDnsTarget.System && android.os.Build.VERSION.SDK_INT >= 26) {
            synchronized(downloaderLock) {
                sharedSystemDownloader ?: KatHttp3Downloader(fallback, context)
                    .also { sharedSystemDownloader = it }
            }
        } else {
            fallback
        }
    }

    private fun getPlayableAudioStreams(streamInfo: StreamInfo): List<AudioStream> =
        streamInfo.audioStreams
            ?.filter { stream ->
                stream.isUrl &&
                    stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    stream.isPlayable()
            }
            ?.preferNonIosAudioStreams()
            .orEmpty()

    private fun getPlayableMuxedVideoStreams(streamInfo: StreamInfo): List<VideoStream> =
        streamInfo.videoStreams
            ?.filter { stream ->
                !stream.isVideoOnly && stream.isUrl && stream.isPlayable()
            }
            ?.preferNonIosVideoStreams()
            .orEmpty()

    private fun logStreamInfo(videoId: String, dnsTarget: NewPipeDnsTarget, streamInfo: StreamInfo) {
        Log.d(
            TAG,
            "StreamInfo videoId=$videoId dnsTarget=${dnsTarget.label} " +
                "type=${streamInfo.streamType} " +
                "audio=${streamInfo.audioStreams?.size ?: 0} " +
                "video=${streamInfo.videoStreams?.size ?: 0} " +
                "videoOnly=${streamInfo.videoOnlyStreams?.size ?: 0} " +
                "dash=${streamInfo.dashMpdUrl?.isNotBlank() == true} " +
                "hls=${streamInfo.hlsUrl?.isNotBlank() == true} " +
                "errors=${streamInfo.errors?.size ?: 0}"
        )
        streamInfo.audioStreams.orEmpty().forEach { stream ->
            Log.d(TAG, "  audio ${stream.describe()}")
        }
        streamInfo.videoStreams.orEmpty().forEach { stream ->
            Log.d(TAG, "  video ${stream.describe()}")
        }
        streamInfo.errors.orEmpty().forEach { error ->
            Log.w(TAG, "  streamInfo error videoId=$videoId", error)
        }
    }

    private fun AudioStream.describe(): String =
        "itag=$itag format=$format delivery=$deliveryMethod isUrl=$isUrl bitrate=$averageBitrate " +
            "content=${content.take(80)}"

    private fun VideoStream.describe(): String =
        "itag=$itag format=$format delivery=$deliveryMethod isUrl=$isUrl videoOnly=$isVideoOnly " +
            "content=${content.take(80)}"

    private fun Stream.isPlayable(): Boolean {
        if (deliveryMethod == DeliveryMethod.TORRENT) return false
        if (deliveryMethod == DeliveryMethod.HLS && format == MediaFormat.OPUS) return false
        return true
    }

    private fun List<AudioStream>.preferNonIosAudioStreams(): List<AudioStream> {
        val nonIosStreams = filterNot { stream ->
            stream.isUrl && YoutubeParsingHelper.isIosStreamingUrl(stream.content)
        }

        return nonIosStreams.ifEmpty { this }
    }

    private fun List<VideoStream>.preferNonIosVideoStreams(): List<VideoStream> {
        val nonIosStreams = filterNot { stream ->
            stream.isUrl && YoutubeParsingHelper.isIosStreamingUrl(stream.content)
        }

        return nonIosStreams.ifEmpty { this }
    }

    private fun selectDefaultAudioStream(streams: List<AudioStream>?): AudioStream? {
        if (streams.isNullOrEmpty()) return null

        return streams.maxWithOrNull(audioStreamComparator)
    }

    private val audioStreamComparator = compareBy<AudioStream> { stream ->
        stream.format.audioQualityRank()
    }.thenBy { stream ->
        stream.averageBitrate
    }

    private fun selectDefaultMuxedVideoStream(streams: List<VideoStream>): VideoStream? =
        streams.minWithOrNull(
            compareBy<VideoStream> { stream ->
                stream.bitrate.takeIf { it > 0 } ?: Int.MAX_VALUE
            }.thenBy { stream ->
                stream.itag
            }
        )

    private fun MediaFormat?.audioQualityRank() = when (this) {
        MediaFormat.MP3 -> 0
        MediaFormat.WEBMA -> 1
        MediaFormat.M4A -> 2
        else -> -1
    }

    private fun dnsFallbackTargets(): List<NewPipeDnsTarget> {
        val startIndex = lastSuccessfulDnsIndex
            ?.plus(1)
            ?.mod(DNS_RESOLVED_ADDRESS_ATTEMPTS)
            ?: 0

        val resolvedTargets = (0 until DNS_RESOLVED_ADDRESS_ATTEMPTS).map { offset ->
            NewPipeDnsTarget.Resolved((startIndex + offset) % DNS_RESOLVED_ADDRESS_ATTEMPTS)
        }

        return listOf(NewPipeDnsTarget.System) + resolvedTargets
    }

    private const val TAG = "NewPipeExtractorClient"
    private const val DNS_RESOLVED_ADDRESS_ATTEMPTS = 4
    private const val PLAYBACK_RESOLVE_TIMEOUT_SECONDS = 45L
    private const val PRELOAD_RESOLVE_TIMEOUT_SECONDS = 5L
    private val RESOLVER_EXECUTOR = Executors.newCachedThreadPool()
}

data class NewPipeAudioResult(
    val streamInfo: StreamInfo,
    val audioStreams: List<AudioStream>,
    val audioStream: AudioStream?,
    val videoStream: VideoStream?
)
