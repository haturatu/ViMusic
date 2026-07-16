package app.vimusic.providers.newpipe.utils

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.MusicShelfRenderer
import app.vimusic.providers.newpipe.models.NavigationEndpoint
import app.vimusic.providers.newpipe.models.isExplicit

// Possible configurations:
// "song" • author(s) • album • duration
// "song" • author(s) • duration
// author(s) • album • duration
// author(s) • duration

fun NewPipeMusic.SongItem.Companion.from(content: MusicShelfRenderer.Content) = runCatching {
    val (mainRuns, otherRuns) = content.runs

    val album: NewPipeMusic.Info<NavigationEndpoint.Endpoint.Browse>? = otherRuns
        .getOrNull(otherRuns.lastIndex - 1)
        ?.firstOrNull()
        ?.takeIf { run ->
            run
                .navigationEndpoint
                ?.browseEndpoint
                ?.type == "MUSIC_PAGE_TYPE_ALBUM"
        }
        ?.let(NewPipeMusic::Info)

    NewPipeMusic.SongItem(
        info = mainRuns
            .firstOrNull()
            ?.let(NewPipeMusic::Info),
        authors = otherRuns
            .getOrNull(otherRuns.lastIndex - if (album == null) 1 else 2)
            ?.map(NewPipeMusic::Info),
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

fun NewPipeMusic.VideoItem.Companion.from(content: MusicShelfRenderer.Content) = runCatching {
    val (mainRuns, otherRuns) = content.runs

    NewPipeMusic.VideoItem(
        info = mainRuns
            .firstOrNull()
            ?.let(NewPipeMusic::Info),
        authors = otherRuns
            .getOrNull(otherRuns.lastIndex - 2)
            ?.map(NewPipeMusic::Info),
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

fun NewPipeMusic.AlbumItem.Companion.from(content: MusicShelfRenderer.Content) = runCatching {
    val (mainRuns, otherRuns) = content.runs

    NewPipeMusic.AlbumItem(
        info = NewPipeMusic.Info(
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
            ?.map(NewPipeMusic::Info),
        year = otherRuns
            .getOrNull(otherRuns.lastIndex)
            ?.firstOrNull()
            ?.text,
        thumbnail = content.thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}.getOrNull()

fun NewPipeMusic.ArtistItem.Companion.from(content: MusicShelfRenderer.Content) = runCatching {
    val (mainRuns, otherRuns) = content.runs

    NewPipeMusic.ArtistItem(
        info = NewPipeMusic.Info(
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

fun NewPipeMusic.PlaylistItem.Companion.from(content: MusicShelfRenderer.Content) = runCatching {
    val (mainRuns, otherRuns) = content.runs

    NewPipeMusic.PlaylistItem(
        info = NewPipeMusic.Info(
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
            ?.let(NewPipeMusic::Info),
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
