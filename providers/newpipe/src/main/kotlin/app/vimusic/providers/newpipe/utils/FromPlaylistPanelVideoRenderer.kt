package app.vimusic.providers.newpipe.utils

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.PlaylistPanelVideoRenderer
import app.vimusic.providers.newpipe.models.isExplicit

fun NewPipeMusic.SongItem.Companion.from(renderer: PlaylistPanelVideoRenderer) = NewPipeMusic.SongItem(
    info = NewPipeMusic.Info(
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
        ?.map(NewPipeMusic::Info),
    album = renderer
        .longBylineText
        ?.splitBySeparator()
        ?.getOrNull(1)
        ?.getOrNull(0)
        ?.let(NewPipeMusic::Info),
    thumbnail = renderer
        .thumbnail
        ?.thumbnails
        ?.getOrNull(0),
    durationText = renderer
        .lengthText
        ?.text,
    explicit = renderer.badges.isExplicit
).takeIf { it.info?.endpoint?.videoId != null }
