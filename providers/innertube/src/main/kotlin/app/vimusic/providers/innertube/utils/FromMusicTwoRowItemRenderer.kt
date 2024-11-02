package app.vimusic.providers.innertube.utils

import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.MusicTwoRowItemRenderer

fun Innertube.AlbumItem.Companion.from(renderer: MusicTwoRowItemRenderer) = Innertube.AlbumItem(
    info = renderer
        .title
        ?.runs
        ?.firstOrNull()
        ?.let(Innertube::Info),
    authors = null,
    year = renderer
        .subtitle
        ?.runs
        ?.lastOrNull()
        ?.text,
    thumbnail = renderer
        .thumbnailRenderer
        ?.musicThumbnailRenderer
        ?.thumbnail
        ?.thumbnails
        ?.firstOrNull()
).takeIf { it.info?.endpoint?.browseId != null }

fun Innertube.ArtistItem.Companion.from(renderer: MusicTwoRowItemRenderer) = Innertube.ArtistItem(
    info = renderer
        .title
        ?.runs
        ?.firstOrNull()
        ?.let(Innertube::Info),
    subscribersCountText = renderer
        .subtitle
        ?.runs
        ?.firstOrNull()
        ?.text,
    thumbnail = renderer
        .thumbnailRenderer
        ?.musicThumbnailRenderer
        ?.thumbnail
        ?.thumbnails
        ?.firstOrNull()
).takeIf { it.info?.endpoint?.browseId != null }

fun Innertube.PlaylistItem.Companion.from(renderer: MusicTwoRowItemRenderer) =
    Innertube.PlaylistItem(
        info = renderer
            .title
            ?.runs
            ?.firstOrNull()
            ?.let(Innertube::Info),
        channel = renderer
            .subtitle
            ?.runs
            ?.getOrNull(2)
            ?.let(Innertube::Info),
        songCount = renderer
            .subtitle
            ?.runs
            ?.getOrNull(4)
            ?.text
            ?.split(' ')
            ?.firstOrNull()
            ?.toIntOrNull(),
        thumbnail = renderer
            .thumbnailRenderer
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull()
    ).takeIf { it.info?.endpoint?.browseId != null }
