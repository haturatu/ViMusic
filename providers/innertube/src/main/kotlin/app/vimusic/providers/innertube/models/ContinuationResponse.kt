package app.vimusic.providers.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ContinuationResponse(
    val continuationContents: ContinuationContents?,
    val responseContext: ResponseContext?
) {
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
