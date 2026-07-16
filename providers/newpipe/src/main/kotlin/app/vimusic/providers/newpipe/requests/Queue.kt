package app.vimusic.providers.newpipe.requests

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.GetQueueResponse
import app.vimusic.providers.newpipe.models.bodies.QueueBody
import app.vimusic.providers.newpipe.utils.from
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun NewPipeMusic.queue(body: QueueBody) = runCatchingCancellable {
    val response = client.post<GetQueueResponse>(
        QUEUE, body, fieldMask = "queueDatas.content.$PLAYLIST_PANEL_VIDEO_RENDERER_MASK"
    )

    response
        .queueData
        ?.mapNotNull { queueData ->
            queueData
                .content
                ?.playlistPanelVideoRenderer
                ?.let(NewPipeMusic.SongItem::from)
        }
}

suspend fun NewPipeMusic.song(videoId: String): Result<NewPipeMusic.SongItem?>? =
    queue(QueueBody(videoIds = listOf(videoId)))?.map { it?.firstOrNull() }
