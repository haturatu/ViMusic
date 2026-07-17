package app.vimusic.providers.youtubemusic.innertube.utils

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.PlaylistPanelVideoRenderer
import app.vimusic.providers.youtubemusic.innertube.models.isExplicit

fun YoutubeMusicInnertube.SongItem.Companion.from(renderer: PlaylistPanelVideoRenderer) = YoutubeMusicInnertube.SongItem(
    info = YoutubeMusicInnertube.Info(
        name = renderer
            .title
            ?.text,
        endpoint = renderer
            .navigationEndpoint
            ?.watchEndpoint
    ),
    authors = renderer
        .longBylineText
        ?.splitBySeparator()
        ?.getOrNull(0)
        ?.map(YoutubeMusicInnertube::Info),
    album = renderer
        .longBylineText
        ?.splitBySeparator()
        ?.getOrNull(1)
        ?.getOrNull(0)
        ?.let(YoutubeMusicInnertube::Info),
    thumbnail = renderer
        .thumbnail
        ?.thumbnails
        ?.getOrNull(0),
    durationText = renderer
        .lengthText
        ?.text,
    explicit = renderer.badges.isExplicit
).takeIf { it.info?.endpoint?.videoId != null }
