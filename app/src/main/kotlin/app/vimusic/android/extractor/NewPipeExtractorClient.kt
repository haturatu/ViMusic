package app.vimusic.android.extractor

import okhttp3.OkHttpClient
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

    fun ensureInitialized() {
        if (initialized) return
        NewPipe.init(
            NewPipeDownloader(OkHttpClient.Builder().build()),
            Localization.DEFAULT,
            ContentCountry.DEFAULT
        )
        initialized = true
    }

    @Throws(IOException::class, ExtractionException::class)
    fun resolveAudioStream(videoId: String): NewPipeAudioResult {
        ensureInitialized()

        val url = "https://www.youtube.com/watch?v=$videoId"
        val service = ServiceList.YouTube as YoutubeService
        val streamInfo = StreamInfo.getInfo(service, url)
        val audioStream = selectBestAudioStream(streamInfo.audioStreams)
            ?: throw ExtractionException("No playable audio stream found")

        return NewPipeAudioResult(streamInfo, audioStream)
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
}

data class NewPipeAudioResult(
    val streamInfo: StreamInfo,
    val audioStream: AudioStream
)
