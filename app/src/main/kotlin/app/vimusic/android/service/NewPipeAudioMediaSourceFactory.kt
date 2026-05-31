package app.vimusic.android.service

import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import android.util.Base64
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import app.vimusic.android.appContainer
import app.vimusic.android.extractor.NewPipeExtractorClient
import app.vimusic.android.extractor.YoutubeHttpDataSourceFactory
import app.vimusic.android.models.Format
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.preferences.PlayerPreferences
import app.vimusic.android.utils.ConditionalCacheDataSourceFactory
import app.vimusic.android.utils.asDataSource
import app.vimusic.android.utils.readOnlyWhen
import app.vimusic.core.ui.utils.songBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.io.IOException

class NewPipeAudioMediaSourceFactory(
    private val context: Context,
    private val cache: Cache,
    private val findMediaItem: suspend (videoId: String) -> MediaItem?,
    private val dbWriteScope: CoroutineScope
) : MediaSource.Factory {
    private val playerRepository = context.appContainer.playerRepository
    private val dataSourceFactory = createDataSourceFactory(context, cache, ::resolveDashManifestUriForPlayback)
    private val delegate = DefaultMediaSourceFactory(
        dataSourceFactory,
        DefaultExtractorsFactory()
    )

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val item = if (mediaItem.isLocal) {
            mediaItem
        } else {
            mediaItem.buildUpon()
                .setUri(mediaItem.mediaId)
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .build()
        }

        return delegate.createMediaSource(item)
    }

    override fun getSupportedTypes(): IntArray = intArrayOf(
        C.CONTENT_TYPE_DASH,
        C.CONTENT_TYPE_OTHER
    )

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider
    ): MediaSource.Factory {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy
    ): MediaSource.Factory {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    private fun resolveDashManifestUriForPlayback(mediaId: String): Uri {
        val result = resolveAudioResult(mediaId)
        writeStreamMetadata(mediaId, result)
        return resolveDashManifestUri(result)
    }

    private fun writeStreamMetadata(mediaId: String, result: app.vimusic.android.extractor.NewPipeAudioResult) {
        val streamInfo = result.streamInfo
        val audioStream = result.audioStream
        val durationText = streamInfo.duration
            .takeIf { it > 0 }
            ?.let(DateUtils::formatElapsedTime)
            ?.removePrefix("0")

        dbWriteScope.launch(Dispatchers.IO) {
            val mediaItem = runCatching { findMediaItem(mediaId) }.getOrNull()
            mediaItem
                ?.mediaMetadata
                ?.extras
                ?.songBundle
                ?.takeIf { it.durationText == null }
                ?.let { extras ->
                    extras.durationText = durationText ?: return@let
                }

            runCatching {
                mediaItem?.let { item ->
                    playerRepository.insertSong(item)
                }
                playerRepository.insertFormat(
                    Format(
                        songId = mediaId,
                        itag = audioStream.itag.takeIf { it > 0 },
                        mimeType = audioStream.format?.mimeType,
                        bitrate = audioStream.averageBitrate
                            .takeIf { it > 0 }
                            ?.toLong(),
                        loudnessDb = null,
                        contentLength = null,
                        lastModified = null
                    )
                )

                durationText?.let {
                    playerRepository.updateDurationText(mediaId, it)
                }
            }.onFailure {
                Log.w(TAG, "Failed to persist stream metadata for $mediaId", it)
            }
        }
    }

    companion object {
        private const val TAG = "NewPipeAudioMediaSourceFactory"

        fun resolveDashManifestUri(mediaId: String): Uri =
            resolveDashManifestUri(resolveAudioResult(mediaId))

        fun createDataSourceFactory(
            context: Context,
            cache: Cache,
            resolveDashManifestUri: (String) -> Uri
        ): DataSource.Factory {
            val playerRepository = context.appContainer.playerRepository
            val upstreamDataSourceFactory = DefaultDataSource.Factory(
                context,
                YoutubeHttpDataSourceFactory(
                    rangeParameterEnabled = true,
                    rnParameterEnabled = true
                )
            )

            return ResolvingDataSource.Factory(
                ConditionalCacheDataSourceFactory(
                    cacheDataSourceFactory = cache.readOnlyWhen { PlayerPreferences.pauseCache }.asDataSource,
                    upstreamDataSourceFactory = upstreamDataSourceFactory,
                    shouldCache = { dataSpec ->
                        if (dataSpec.uri.scheme == "data") return@ConditionalCacheDataSourceFactory false
                        if (!DataPreferences.cacheFavoritesOnly) return@ConditionalCacheDataSourceFactory true

                        val mediaId = dataSpec.key?.let(PlayerService::extractYouTubeVideoId)
                            ?: return@ConditionalCacheDataSourceFactory false
                        playerRepository.isFavoriteNow(mediaId)
                    }
                )
            ) resolver@{ dataSpec ->
                if (dataSpec.isLocal || dataSpec.uri.isNetworkStream) return@resolver dataSpec

                val mediaId = dataSpec.key
                    ?.let(PlayerService::extractYouTubeVideoId)
                    ?: PlayerService.extractYouTubeVideoId(dataSpec.uri.toString())

                dataSpec.withUri(resolveDashManifestUri(mediaId))
            }
        }

        private fun resolveAudioResult(mediaId: String): app.vimusic.android.extractor.NewPipeAudioResult {
            val result = try {
                NewPipeExtractorClient.resolveAudioStream(mediaId)
            } catch (error: IOException) {
                throw error
            } catch (error: ExtractionException) {
                throw IOException("NewPipe extraction failed for $mediaId", error)
            }

            if (result.streamInfo.id != mediaId) {
                throw IOException("Resolved video ID ${result.streamInfo.id} does not match requested video ID $mediaId")
            }

            return result
        }

        private fun resolveDashManifestUri(result: app.vimusic.android.extractor.NewPipeAudioResult): Uri {
            val streamInfo = result.streamInfo
            val audioStream = result.audioStream
            val itagItem = audioStream.itagItem
                ?: throw IOException(
                    "Unsupported audio stream for DASH playback: " +
                        "delivery=${audioStream.deliveryMethod} isUrl=${audioStream.isUrl} itag=${audioStream.itag}"
                )

            val manifest = when (audioStream.deliveryMethod) {
                DeliveryMethod.PROGRESSIVE_HTTP -> YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(
                    audioStream.content,
                    itagItem,
                    streamInfo.duration
                )

                DeliveryMethod.DASH -> YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(
                    audioStream.content,
                    itagItem,
                    streamInfo.duration
                )

                else -> throw IOException(
                    "Unsupported audio stream delivery for DASH playback: ${audioStream.deliveryMethod}"
                )
            }
            val encoded = Base64.encodeToString(manifest.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            return "data:application/dash+xml;base64,$encoded".toUri()
        }

        private val Uri.isNetworkStream: Boolean
            get() = scheme == "http" || scheme == "https"
    }
}
