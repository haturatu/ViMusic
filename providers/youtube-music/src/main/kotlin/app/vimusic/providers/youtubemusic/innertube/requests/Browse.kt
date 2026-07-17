package app.vimusic.providers.youtubemusic.innertube.requests

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube.toMood
import app.vimusic.providers.youtubemusic.innertube.models.BrowseResponse
import app.vimusic.providers.youtubemusic.innertube.models.GridRenderer
import app.vimusic.providers.youtubemusic.innertube.models.MusicCarouselShelfRenderer
import app.vimusic.providers.youtubemusic.innertube.models.MusicNavigationButtonRenderer
import app.vimusic.providers.youtubemusic.innertube.models.MusicResponsiveListItemRenderer
import app.vimusic.providers.youtubemusic.innertube.models.MusicTwoRowItemRenderer
import app.vimusic.providers.youtubemusic.innertube.models.SectionListRenderer
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.utils.from
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun YoutubeMusicInnertube.browse(body: BrowseBody) = runCatchingCancellable {
    val response = client.post<BrowseResponse>(BROWSE, body)

    BrowseResult(
        title = response
            .header
            ?.musicImmersiveHeaderRenderer
            ?.title
            ?.text
            ?: response
                .header
                ?.musicDetailHeaderRenderer
                ?.title
                ?.text,
        items = response
            .contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.toBrowseItems()
            .orEmpty()
    )
}

fun SectionListRenderer.toBrowseItems() = contents?.mapNotNull { content ->
    when {
        content.gridRenderer != null -> content.gridRenderer.toBrowseItem()
        content.musicCarouselShelfRenderer != null -> content.musicCarouselShelfRenderer.toBrowseItem()
        else -> null
    }
}

fun GridRenderer.toBrowseItem() = BrowseResult.Item(
    title = header
        ?.gridHeaderRenderer
        ?.title
        ?.runs
        ?.firstOrNull()
        ?.text,
    items = items
        ?.mapNotNull {
            it.musicTwoRowItemRenderer?.toItem() ?: it.musicNavigationButtonRenderer?.toItem()
        }
        .orEmpty()
)

fun MusicCarouselShelfRenderer.toBrowseItem(
    fromResponsiveListItemRenderer: ((MusicResponsiveListItemRenderer) -> YoutubeMusicInnertube.Item?)? = null
) = BrowseResult.Item(
    title = header
        ?.musicCarouselShelfBasicHeaderRenderer
        ?.title
        ?.runs
        ?.firstOrNull()
        ?.text,
    items = contents
        ?.mapNotNull {
            it.musicResponsiveListItemRenderer?.let { renderer ->
                fromResponsiveListItemRenderer?.invoke(renderer)
            } ?: it.musicTwoRowItemRenderer?.toItem()
                ?: it.musicNavigationButtonRenderer?.toItem()
        }
        .orEmpty()
)

data class BrowseResult(
    val title: String?,
    val items: List<Item>
) {
    data class Item(
        val title: String?,
        val items: List<YoutubeMusicInnertube.Item>
    )
}

fun MusicTwoRowItemRenderer.toItem() = when {
    isAlbum -> YoutubeMusicInnertube.AlbumItem.from(this)
    isPlaylist -> YoutubeMusicInnertube.PlaylistItem.from(this)
    isArtist -> YoutubeMusicInnertube.ArtistItem.from(this)
    else -> null
}

fun MusicNavigationButtonRenderer.toItem() = when {
    isMood -> toMood()
    else -> null
}
