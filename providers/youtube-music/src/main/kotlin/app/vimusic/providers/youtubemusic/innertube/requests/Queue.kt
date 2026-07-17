package app.vimusic.providers.youtubemusic.innertube.requests

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.GetQueueResponse
import app.vimusic.providers.youtubemusic.innertube.models.bodies.QueueBody
import app.vimusic.providers.youtubemusic.innertube.utils.from
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun YoutubeMusicInnertube.queue(body: QueueBody) = runCatchingCancellable {
    val response = client.post<GetQueueResponse>(
        QUEUE, body, fieldMask = "queueDatas.content.$PLAYLIST_PANEL_VIDEO_RENDERER_MASK"
    )

    response
        .queueData
        ?.mapNotNull { queueData ->
            queueData
                .content
                ?.playlistPanelVideoRenderer
                ?.let(YoutubeMusicInnertube.SongItem::from)
        }
}

suspend fun YoutubeMusicInnertube.song(videoId: String): Result<YoutubeMusicInnertube.SongItem?>? =
    queue(QueueBody(videoIds = listOf(videoId)))?.map { it?.firstOrNull() }
