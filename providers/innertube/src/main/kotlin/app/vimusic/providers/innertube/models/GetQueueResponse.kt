package app.vimusic.providers.innertube.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetQueueResponse(
    @SerialName("queueDatas")
    val queueData: List<QueueData>?
) {
    @Serializable
    data class QueueData(
        val content: NextResponse.MusicQueueRenderer.Content.PlaylistPanelRenderer.Content?
    )
}
