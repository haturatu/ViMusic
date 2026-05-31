package app.vimusic.android.extractor

import android.util.Log
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException

object NewPipeExtractorClient {
    private var initialized = false
    private val lock = Any()
    private var lastSuccessfulDnsIndex: Int? = null

    fun ensureInitialized() {
        if (initialized) return
        configureDownloader(NewPipeDnsTarget.System)
        initialized = true
    }

    @Throws(IOException::class, ExtractionException::class)
    fun resolveAudioStream(videoId: String): NewPipeAudioResult {
        ensureInitialized()

        synchronized(lock) {
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
            ?: throw ExtractionException(
                "No playable audio stream found; " +
                    "audio=${streamInfo.audioStreams?.size ?: 0} " +
                    "video=${streamInfo.videoStreams?.size ?: 0} " +
                    "videoOnly=${streamInfo.videoOnlyStreams?.size ?: 0} " +
                    "errors=${streamInfo.errors?.joinToString { it.message.orEmpty() }.orEmpty()}"
            )

        Log.i(
            TAG,
            "Audio stream resolved videoId=$videoId dnsTarget=${dnsTarget.label} " +
                "itag=${audioStream.itag} bitrate=${audioStream.averageBitrate}"
        )

        return NewPipeAudioResult(streamInfo, audioStreams, audioStream)
    }

    private fun configureDownloader(dnsTarget: NewPipeDnsTarget) {
        NewPipe.init(
            NewPipeDownloader(NewPipeDownloader.client(dnsTarget)),
            Localization.DEFAULT,
            ContentCountry.DEFAULT
        )
    }

    private fun getPlayableAudioStreams(streamInfo: StreamInfo): List<AudioStream> =
        streamInfo.audioStreams
            ?.filter { stream ->
                stream.isPlayable()
            }
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

    private fun selectDefaultAudioStream(streams: List<AudioStream>?): AudioStream? {
        if (streams.isNullOrEmpty()) return null

        return streams.maxWithOrNull(audioStreamComparator)
    }

    private val audioStreamComparator = compareBy<AudioStream> { stream ->
        stream.format.audioQualityRank()
    }.thenBy { stream ->
        stream.averageBitrate
    }

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
    private const val DNS_RESOLVED_ADDRESS_ATTEMPTS = 20
}

data class NewPipeAudioResult(
    val streamInfo: StreamInfo,
    val audioStreams: List<AudioStream>,
    val audioStream: AudioStream
)
