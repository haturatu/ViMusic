package app.vimusic.providers.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ThumbnailRenderer(
    @JsonNames("croppedSquareThumbnailRenderer")
    val musicThumbnailRenderer: MusicThumbnailRenderer?
) {
    @Serializable
    data class MusicThumbnailRenderer(
        val thumbnail: Thumbnail?
    ) {
        @Serializable
        data class Thumbnail(
            val thumbnails: List<app.vimusic.providers.innertube.models.Thumbnail>?
        )
    }
}

@Serializable
data class ThumbnailOverlay(
    val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer
) {
    @Serializable
    data class MusicItemThumbnailOverlayRenderer(
        val content: Content
    ) {
        @Serializable
        data class Content(
            val musicPlayButtonRenderer: MusicPlayButtonRenderer
        ) {
            @Serializable
            data class MusicPlayButtonRenderer(
                val playNavigationEndpoint: NavigationEndpoint?
            )
        }
    }
}
