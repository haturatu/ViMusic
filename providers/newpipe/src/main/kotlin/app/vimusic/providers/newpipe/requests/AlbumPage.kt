package app.vimusic.providers.newpipe.requests

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.NavigationEndpoint
import app.vimusic.providers.newpipe.models.bodies.BrowseBody
import java.net.URI

suspend fun NewPipeMusic.albumPage(body: BrowseBody) =
    playlistPage(body)
        ?.map { album ->
            album.url?.let {
                playlistPage(body = BrowseBody(browseId = "VL${URI(it).query.split('&')
                    .firstOrNull { parameter -> parameter.startsWith("list=") }
                    ?.substringAfter('=')}"))
                    ?.getOrNull()
                    ?.let { playlist -> album.copy(songsPage = playlist.songsPage) }
            } ?: album
        }
        ?.map { album ->
            album.copy(
                songsPage = album.songsPage?.copy(
                    items = album.songsPage.items?.map { song ->
                        song.copy(
                            authors = song.authors ?: album.authors,
                            album = NewPipeMusic.Info(
                                name = album.title,
                                endpoint = NavigationEndpoint.Endpoint.Browse(
                                    browseId = body.browseId,
                                    params = body.params
                                )
                            ),
                            thumbnail = album.thumbnail
                        )
                    }
                )
            )
        }
