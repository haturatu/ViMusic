package app.vimusic.android.repositories

import app.vimusic.android.utils.YouTubeRadioDataSource
import app.vimusic.android.utils.YouTubeRadioPage
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.innertube.models.bodies.NextBody
import app.vimusic.providers.innertube.requests.nextPage

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
