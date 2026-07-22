package app.vimusic.providers.youtubemusic.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ContinuationResponse(
    val continuationContents: ContinuationContents? = null,
    val responseContext: ResponseContext?,
    val onResponseReceivedActions: List<ResponseReceivedAction>? = null,
) {
    @Serializable
    data class ResponseReceivedAction(
        val appendContinuationItemsAction: AppendContinuationItemsAction? = null,
    ) {
        @Serializable
        data class AppendContinuationItemsAction(
            val continuationItems: List<MusicShelfRenderer.Content>? = null,
        )
    }

    @Serializable
    data class ContinuationContents(
        @JsonNames("musicPlaylistShelfContinuation")
        val musicShelfContinuation: MusicShelfRenderer?,
        @JsonNames("sectionListContinuation")
        val sectionListContinuation: SectionListRenderer?,
        val gridContinuation: GridRenderer?,
        val playlistPanelContinuation: NextResponse.MusicQueueRenderer.Content.PlaylistPanelRenderer?
    )
}
