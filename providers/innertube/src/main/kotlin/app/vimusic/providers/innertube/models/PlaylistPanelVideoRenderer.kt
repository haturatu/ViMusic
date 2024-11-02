package app.vimusic.providers.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistPanelVideoRenderer(
    val title: Runs?,
    val longBylineText: Runs?,
    val shortBylineText: Runs?,
    val lengthText: Runs?,
    val navigationEndpoint: NavigationEndpoint?,
    val thumbnail: ThumbnailRenderer.MusicThumbnailRenderer.Thumbnail?,
    val badges: List<Badge>?
)
