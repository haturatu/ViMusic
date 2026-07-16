package app.vimusic.providers.youtubemusic.innertube.utils

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.MusicTwoRowItemRenderer

fun YoutubeMusicInnertube.AlbumItem.Companion.from(renderer: MusicTwoRowItemRenderer) = YoutubeMusicInnertube.AlbumItem(
    info = renderer
        .title
        ?.runs
        ?.firstOrNull()
        ?.let(YoutubeMusicInnertube::Info),
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

fun YoutubeMusicInnertube.ArtistItem.Companion.from(renderer: MusicTwoRowItemRenderer) = YoutubeMusicInnertube.ArtistItem(
    info = renderer
        .title
        ?.runs
        ?.firstOrNull()
        ?.let(YoutubeMusicInnertube::Info),
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

fun YoutubeMusicInnertube.PlaylistItem.Companion.from(renderer: MusicTwoRowItemRenderer) =
    YoutubeMusicInnertube.PlaylistItem(
        info = renderer
            .title
            ?.runs
            ?.firstOrNull()
            ?.let(YoutubeMusicInnertube::Info),
        channel = renderer
            .subtitle
            ?.runs
            ?.getOrNull(2)
            ?.let(YoutubeMusicInnertube::Info),
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
