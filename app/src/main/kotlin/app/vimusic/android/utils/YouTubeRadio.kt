package app.vimusic.android.utils

import android.util.Log
import androidx.media3.common.MediaItem
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.innertube.models.bodies.NextBody
import app.vimusic.providers.innertube.requests.nextPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class YouTubeRadioPage(
    val items: List<MediaItem>,
    val continuation: String?,
    val playlistId: String?,
    val params: String?,
    val playlistSetVideoId: String?
)

interface YouTubeRadioDataSource {
    suspend fun fetchFirstPage(
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        params: String?
    ): YouTubeRadioPage?

    suspend fun fetchContinuation(continuation: String): YouTubeRadioPage?
}

object InnertubeYouTubeRadioDataSource : YouTubeRadioDataSource {
    override suspend fun fetchFirstPage(
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        params: String?
    ): YouTubeRadioPage? =
        Innertube.nextPage(
            NextBody(
                videoId = videoId,
                playlistId = playlistId,
                params = params,
                playlistSetVideoId = playlistSetVideoId
            )
        )?.map { nextResult ->
            val songsPage = nextResult.itemsPage
            YouTubeRadioPage(
                items = songsPage?.items?.map(Innertube.SongItem::asMediaItem).orEmpty(),
                continuation = songsPage?.continuation,
                playlistId = nextResult.playlistId,
                params = nextResult.params,
                playlistSetVideoId = nextResult.playlistSetVideoId
            )
        }?.getOrNull()

    override suspend fun fetchContinuation(continuation: String): YouTubeRadioPage? =
        Innertube.nextPage(ContinuationBody(continuation = continuation))
            ?.map { songsPage ->
                YouTubeRadioPage(
                    items = songsPage?.items?.map(Innertube.SongItem::asMediaItem).orEmpty(),
                    continuation = songsPage?.continuation,
                    playlistId = null,
                    params = null,
                    playlistSetVideoId = null
                )
            }?.getOrNull()
}

data class YouTubeRadio(
    private val videoId: String? = null,
    private var playlistId: String? = null,
    private var playlistSetVideoId: String? = null,
    private var parameters: String? = null,
    private val dataSource: YouTubeRadioDataSource = InnertubeYouTubeRadioDataSource
) {
    private companion object {
        private const val TAG = "YouTubeRadio"
    }

    private var nextContinuation: String? = null

    suspend fun process(): List<MediaItem> {
        return runCatching {
            var mediaItems: List<MediaItem>? = null

            nextContinuation = withContext(Dispatchers.IO) {
                val continuation = nextContinuation

                val page = if (continuation == null) {
                    dataSource.fetchFirstPage(
                        videoId = videoId,
                        playlistId = playlistId,
                        playlistSetVideoId = playlistSetVideoId,
                        params = parameters
                    )?.also { firstPage ->
                        playlistId = firstPage.playlistId ?: playlistId
                        parameters = firstPage.params ?: parameters
                        playlistSetVideoId = firstPage.playlistSetVideoId ?: playlistSetVideoId
                    }
                } else {
                    dataSource.fetchContinuation(continuation)
                }

                page?.let {
                    mediaItems = it.items
                    it.continuation?.takeUnless { nextContinuation == it }
                }
            }

            mediaItems ?: emptyList()
        }.getOrElse { error ->
            Log.e(TAG, "Radio fetch failed", error)
            nextContinuation = null
            emptyList()
        }
    }
}
