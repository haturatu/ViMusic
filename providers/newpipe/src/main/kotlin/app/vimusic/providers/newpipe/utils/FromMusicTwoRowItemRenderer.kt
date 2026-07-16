package app.vimusic.providers.newpipe.utils

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.MusicTwoRowItemRenderer

fun NewPipeMusic.AlbumItem.Companion.from(renderer: MusicTwoRowItemRenderer) = NewPipeMusic.AlbumItem(
    info = renderer
        .title
        ?.runs
        ?.firstOrNull()
        ?.let(NewPipeMusic::Info),
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

fun NewPipeMusic.ArtistItem.Companion.from(renderer: MusicTwoRowItemRenderer) = NewPipeMusic.ArtistItem(
    info = renderer
        .title
        ?.runs
        ?.firstOrNull()
        ?.let(NewPipeMusic::Info),
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

fun NewPipeMusic.PlaylistItem.Companion.from(renderer: MusicTwoRowItemRenderer) =
    NewPipeMusic.PlaylistItem(
        info = renderer
            .title
            ?.runs
            ?.firstOrNull()
            ?.let(NewPipeMusic::Info),
        channel = renderer
            .subtitle
            ?.runs
            ?.getOrNull(2)
            ?.let(NewPipeMusic::Info),
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
