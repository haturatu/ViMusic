package app.vimusic.android.extractor

import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
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
        val audioStream = selectBestAudioStream(streamInfo.audioStreams)
            ?: throw ExtractionException("No playable audio stream found")

        Log.i(
            TAG,
            "Audio stream resolved videoId=$videoId dnsTarget=${dnsTarget.label} " +
                "itag=${audioStream.itag} bitrate=${audioStream.averageBitrate}"
        )

        return NewPipeAudioResult(streamInfo, audioStream)
    }

    private fun configureDownloader(dnsTarget: NewPipeDnsTarget) {
        NewPipe.init(
            NewPipeDownloader(NewPipeDownloader.client(dnsTarget)),
            Localization.DEFAULT,
            ContentCountry.DEFAULT
        )
    }

    private fun selectBestAudioStream(streams: List<AudioStream>?): AudioStream? {
        if (streams.isNullOrEmpty()) return null

        val candidates = streams
            .filter { it.isUrl }
            .sortedWith(
                compareBy<AudioStream>(
                    { it.deliveryMethod != DeliveryMethod.PROGRESSIVE_HTTP },
                    { -(it.averageBitrate.coerceAtLeast(0)) }
                )
            )

        return candidates.firstOrNull()
    }

    private fun dnsFallbackTargets(): List<NewPipeDnsTarget> {
        val startIndex = lastSuccessfulDnsIndex
            ?.plus(1)
            ?.mod(DNS_RESOLVED_ADDRESS_ATTEMPTS)
            ?: 0

        val resolvedTargets = (0 until DNS_RESOLVED_ADDRESS_ATTEMPTS).map { offset ->
            NewPipeDnsTarget.Resolved((startIndex + offset) % DNS_RESOLVED_ADDRESS_ATTEMPTS)
        }

        return resolvedTargets + NewPipeDnsTarget.System
    }

    private const val TAG = "NewPipeExtractorClient"
    private const val DNS_RESOLVED_ADDRESS_ATTEMPTS = 20
}

data class NewPipeAudioResult(
    val streamInfo: StreamInfo,
    val audioStream: AudioStream
)
