package app.vimusic.providers.youtubemusic.innertube.utils

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.MusicTwoRowItemRenderer
import app.vimusic.providers.youtubemusic.innertube.models.splitBySeparator

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

fun YoutubeMusicInnertube.VideoItem.Companion.from(renderer: MusicTwoRowItemRenderer) = runCatching {
    val subtitleParts = renderer.subtitle?.runs?.splitBySeparator().orEmpty()

    // Artist-page video carousels put the Watch endpoint on the renderer,
    // while the title run commonly has no navigation endpoint at all.
    val info = renderer.navigationEndpoint?.watchEndpoint?.let { endpoint ->
        YoutubeMusicInnertube.Info(name = renderer.title?.text, endpoint = endpoint)
    } ?: renderer
        .title
        ?.runs
        ?.firstOrNull()
        ?.let(YoutubeMusicInnertube::Info)

    YoutubeMusicInnertube.VideoItem(
        info = info,
        authors = subtitleParts
            .firstOrNull()
            ?.map(YoutubeMusicInnertube::Info),
        viewsText = subtitleParts
            .getOrNull(1)
            ?.joinToString("") { it.text.orEmpty() },
        durationText = null,
        thumbnail = renderer
            .thumbnailRenderer
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull()
    ).takeIf { it.info?.endpoint?.videoId != null }
}.getOrNull()

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
