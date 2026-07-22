package app.vimusic.providers.youtubemusic.innertube.utils

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.MusicResponsiveListItemRenderer
import app.vimusic.providers.youtubemusic.innertube.models.NavigationEndpoint
import app.vimusic.providers.youtubemusic.innertube.models.isExplicit

fun YoutubeMusicInnertube.SongItem.Companion.from(renderer: MusicResponsiveListItemRenderer) =
    YoutubeMusicInnertube.SongItem(
        info = (renderer.navigationEndpoint?.watchEndpoint
            ?: renderer
            .flexColumns
            .getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.getOrNull(0)
            ?.navigationEndpoint
            ?.watchEndpoint
            ?: renderer.playlistItemData?.videoId?.let { videoId ->
                NavigationEndpoint.Endpoint.Watch(
                    videoId = videoId,
                    playlistSetVideoId = renderer.playlistItemData.playlistSetVideoId,
                )
            })
            ?.let { endpoint ->
                YoutubeMusicInnertube.Info(
                    name = renderer
                        .flexColumns
                        .getOrNull(0)
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text
                        ?.runs
                        ?.getOrNull(0)
                        ?.text,
                    endpoint = endpoint,
                )
            },
        authors = renderer
            .flexColumns
            .getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.map { YoutubeMusicInnertube.Info(name = it.text, endpoint = it.navigationEndpoint?.endpoint) }
            ?.filterIsInstance<YoutubeMusicInnertube.Info<NavigationEndpoint.Endpoint.Browse>>()
            ?.takeIf(List<Any>::isNotEmpty),
        durationText = renderer
            .fixedColumns
            ?.getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.getOrNull(0)
            ?.text,
        album = renderer
            .flexColumns
            .getOrNull(2)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.let(YoutubeMusicInnertube::Info),
        explicit = renderer.badges.isExplicit,
        thumbnail = renderer
            .thumbnail
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull()
    ).takeIf { it.info?.endpoint?.videoId != null }
