package app.vimusic.providers.youtubemusic.innertube.utils

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.MusicShelfRenderer
import app.vimusic.providers.youtubemusic.innertube.models.MusicResponsiveListItemRenderer
import app.vimusic.providers.youtubemusic.innertube.models.NavigationEndpoint
import app.vimusic.providers.youtubemusic.innertube.models.isExplicit

// Possible configurations:
// "song" • author(s) • album • duration
// "song" • author(s) • duration
// author(s) • album • duration
// author(s) • duration

fun YoutubeMusicInnertube.SongItem.Companion.from(content: MusicShelfRenderer.Content) = runCatching {
    val (mainRuns, otherRuns) = content.runs

    val album: YoutubeMusicInnertube.Info<NavigationEndpoint.Endpoint.Browse>? = otherRuns
        .getOrNull(otherRuns.lastIndex - 1)
        ?.firstOrNull()
        ?.takeIf { run ->
            run
                .navigationEndpoint
                ?.browseEndpoint
                ?.type == "MUSIC_PAGE_TYPE_ALBUM"
        }
        ?.let(YoutubeMusicInnertube::Info)

    YoutubeMusicInnertube.SongItem(
        info = mainRuns
            .firstOrNull()
            ?.let(YoutubeMusicInnertube::Info),
        authors = otherRuns
            .getOrNull(otherRuns.lastIndex - if (album == null) 1 else 2)
            ?.map(YoutubeMusicInnertube::Info),
        album = album,
        durationText = otherRuns
            .lastOrNull()
            ?.firstOrNull()
            ?.text
            ?.takeIf { ':' in it }
            ?: otherRuns
                .getOrNull(otherRuns.size - 2)
                ?.firstOrNull()
                ?.text,
        explicit = content.musicResponsiveListItemRenderer?.badges.isExplicit,
        thumbnail = content.thumbnail
    ).takeIf { it.info?.endpoint?.videoId != null }
}.getOrNull()

fun YoutubeMusicInnertube.VideoItem.Companion.from(content: MusicShelfRenderer.Content) = runCatching {
    val (mainRuns, otherRuns) = content.runs

    YoutubeMusicInnertube.VideoItem(
        info = mainRuns
            .firstOrNull()
            ?.let(YoutubeMusicInnertube::Info),
        authors = otherRuns
            .getOrNull(otherRuns.lastIndex - 2)
            ?.map(YoutubeMusicInnertube::Info),
        viewsText = otherRuns
            .getOrNull(otherRuns.lastIndex - 1)
            ?.firstOrNull()
            ?.text,
        durationText = otherRuns
            .getOrNull(otherRuns.lastIndex)
            ?.firstOrNull()
            ?.text,
        thumbnail = content.thumbnail
    ).takeIf { it.info?.endpoint?.videoId != null }
}.getOrNull()

fun YoutubeMusicInnertube.VideoItem.Companion.from(renderer: MusicResponsiveListItemRenderer) = runCatching {
    val titleRuns = renderer
        .flexColumns
        .firstOrNull()
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs
        .orEmpty()
    val authorRuns = renderer
        .flexColumns
        .getOrNull(1)
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs
        .orEmpty()

    YoutubeMusicInnertube.VideoItem(
        // Playlist and continuation rows can put the Watch endpoint on the
        // renderer rather than the title run. Prefer that stable endpoint so
        // valid videos are not filtered out below.
        info = renderer.navigationEndpoint?.watchEndpoint?.let { endpoint ->
            YoutubeMusicInnertube.Info(
                name = titleRuns.firstOrNull()?.text,
                endpoint = endpoint,
            )
        } ?: titleRuns
            .firstOrNull()
            ?.let(YoutubeMusicInnertube::Info),
        authors = authorRuns
            .takeIf(List<*>::isNotEmpty)
            ?.map(YoutubeMusicInnertube::Info),
        viewsText = renderer
            .flexColumns
            .getOrNull(2)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.text
            ?.takeIf(String::isNotBlank),
        durationText = renderer
            .fixedColumns
            ?.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.text,
        thumbnail = renderer
            .thumbnail
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull(),
    ).takeIf { it.info?.endpoint?.videoId != null }
}.getOrNull()

fun YoutubeMusicInnertube.AlbumItem.Companion.from(content: MusicShelfRenderer.Content) = runCatching {
    val (mainRuns, otherRuns) = content.runs

    YoutubeMusicInnertube.AlbumItem(
        info = YoutubeMusicInnertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        authors = otherRuns
            .getOrNull(otherRuns.lastIndex - 1)
            ?.map(YoutubeMusicInnertube::Info),
        year = otherRuns
            .getOrNull(otherRuns.lastIndex)
            ?.firstOrNull()
            ?.text,
        thumbnail = content.thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}.getOrNull()

fun YoutubeMusicInnertube.ArtistItem.Companion.from(content: MusicShelfRenderer.Content) = runCatching {
    val (mainRuns, otherRuns) = content.runs

    YoutubeMusicInnertube.ArtistItem(
        info = YoutubeMusicInnertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        subscribersCountText = otherRuns
            .lastOrNull()
            ?.last()
            ?.text,
        thumbnail = content.thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}.getOrNull()

fun YoutubeMusicInnertube.PlaylistItem.Companion.from(content: MusicShelfRenderer.Content) = runCatching {
    val (mainRuns, otherRuns) = content.runs

    YoutubeMusicInnertube.PlaylistItem(
        info = YoutubeMusicInnertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        channel = otherRuns
            .firstOrNull()
            ?.firstOrNull()
            ?.let(YoutubeMusicInnertube::Info),
        songCount = otherRuns
            .lastOrNull()
            ?.firstOrNull()
            ?.text
            ?.split(' ')
            ?.firstOrNull()
            ?.toIntOrNull(),
        thumbnail = content.thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}.getOrNull()
