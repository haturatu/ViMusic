package app.vimusic.providers.newpipe.requests

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.NewPipeMusic.toMood
import app.vimusic.providers.newpipe.models.BrowseResponse
import app.vimusic.providers.newpipe.models.GridRenderer
import app.vimusic.providers.newpipe.models.MusicCarouselShelfRenderer
import app.vimusic.providers.newpipe.models.MusicNavigationButtonRenderer
import app.vimusic.providers.newpipe.models.MusicResponsiveListItemRenderer
import app.vimusic.providers.newpipe.models.MusicTwoRowItemRenderer
import app.vimusic.providers.newpipe.models.SectionListRenderer
import app.vimusic.providers.newpipe.models.bodies.BrowseBody
import app.vimusic.providers.newpipe.utils.from
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun NewPipeMusic.browse(body: BrowseBody) = runCatchingCancellable {
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
    fromResponsiveListItemRenderer: ((MusicResponsiveListItemRenderer) -> NewPipeMusic.Item?)? = null
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
        val items: List<NewPipeMusic.Item>
    )
}

fun MusicTwoRowItemRenderer.toItem() = when {
    isAlbum -> NewPipeMusic.AlbumItem.from(this)
    isPlaylist -> NewPipeMusic.PlaylistItem.from(this)
    isArtist -> NewPipeMusic.ArtistItem.from(this)
    else -> null
}

fun MusicNavigationButtonRenderer.toItem() = when {
    isMood -> toMood()
    else -> null
}
