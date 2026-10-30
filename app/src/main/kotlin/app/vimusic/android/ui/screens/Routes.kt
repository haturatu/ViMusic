package app.vimusic.android.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import app.vimusic.android.LocalAppContainer
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.R
import app.vimusic.android.handleUrl
import app.vimusic.android.models.Mood
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.ui.screens.album.AlbumScreen
import app.vimusic.android.ui.screens.artist.ArtistScreen
import app.vimusic.android.ui.screens.playlist.PlaylistScreen
import app.vimusic.android.ui.screens.search.SearchScreen
import app.vimusic.android.ui.screens.searchresult.SearchResultScreen
import app.vimusic.android.ui.screens.settings.LogsScreen
import app.vimusic.android.ui.screens.settings.SettingsScreen
import app.vimusic.android.utils.launchDatabaseAction
import app.vimusic.android.utils.toast
import app.vimusic.compose.routing.Route0
import app.vimusic.compose.routing.Route1
import app.vimusic.compose.routing.Route4
import app.vimusic.compose.routing.RouteHandlerScope
import app.vimusic.core.data.enums.BuiltInPlaylist

/**
 * Marker class for linters that a composable is a route and should not be handled like a regular
 * composable, but rather as an entrypoint.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class Route

val albumRoute = Route1<String>("albumRoute")
val artistRoute = Route1<String>("artistRoute")
val builtInPlaylistRoute = Route1<BuiltInPlaylist>("builtInPlaylistRoute")
val localPlaylistRoute = Route1<Long>("localPlaylistRoute")
val logsRoute = Route0("logsRoute")
val playlistRoute = Route4<String, String?, Int?, Boolean>("playlistRoute")
val moodRoute = Route1<Mood>("moodRoute")
val searchResultRoute = Route1<String>("searchResultRoute")
val searchRoute = Route1<String>("searchRoute")
val settingsRoute = Route0("settingsRoute")

@Composable
fun RouteHandlerScope.GlobalRoutes() {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val container = LocalAppContainer.current
    val onlineSearchRepository = container.onlineSearchRepository

    albumRoute { browseId ->
        AlbumScreen(browseId = browseId)
    }

    artistRoute { browseId ->
        ArtistScreen(browseId = browseId)
    }

    logsRoute {
        LogsScreen()
    }

    playlistRoute { browseId, params, maxDepth, shouldDedup ->
        PlaylistScreen(
            browseId = browseId,
            params = params,
            maxDepth = maxDepth,
            shouldDedup = shouldDedup
        )
    }

    settingsRoute {
        SettingsScreen()
    }

    searchRoute { initialTextInput ->
        SearchScreen(
            initialTextInput = initialTextInput,
            onSearch = { rawQuery ->
                val query = rawQuery.trim()
                if (query.isNotEmpty()) {
                    if (!DataPreferences.pauseSearchHistory) container.databaseScope.launchDatabaseAction {
                        onlineSearchRepository.saveHistory(query)
                    }

                    args[0] = query
                    if (child == searchResultRoute) replace(null)
                    replace(searchResultRoute)
                }
            },
            onViewPlaylist = { url ->
                with(context) {
                    runCatching {
                        handleUrl(url.toUri(), binder)
                    }.onFailure {
                        toast(getString(R.string.error_url, url))
                    }
                }
            }
        )
    }

    searchResultRoute { query ->
        SearchResultScreen(
            query = query,
            onSearchAgain = { searchRoute(query) }
        )
    }
}
