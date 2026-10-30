package app.vimusic.android.auto

import app.vimusic.android.Database
import kotlinx.coroutines.flow.first
import app.vimusic.core.data.enums.PlaylistSortBy
import app.vimusic.android.AppContainer
import app.vimusic.android.utils.asMediaItem
import app.vimusic.core.data.enums.SongSortBy
import app.vimusic.core.data.enums.SortOrder
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Query-keyed results never depend on which controller searched most recently. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoSearchCoordinator(private val container: AppContainer) {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, Pair<Long, List<MediaItem>>>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<MediaItem>>>?) = size > 8
    }

    suspend fun search(query: String, tree: AutoLibraryTree): List<MediaItem> {
        val text = query.trim().take(200)
        if (text.isEmpty()) return emptyList()
        mutex.withLock { cache[text]?.takeIf { android.os.SystemClock.elapsedRealtime() - it.first < 120_000 }?.let { return it.second } }
        val result = coroutineScope {
            val local = async {
                listOf(false, true).flatMap { isLocal ->
                    container.songsRepository.songs(SongSortBy.Title, SortOrder.Ascending, isLocal = isLocal, searchQuery = text)
                }.map { it.asMediaItem }
            }
            val localFolders = async {
                Database.artistsByNameAsc().first().filter { it.name?.contains(text, true) == true }
                    .map { tree.folder("artist", it.id, it.name.orEmpty(), it.thumbnailUrl) } +
                Database.albumsByTitleAsc().first().filter { it.title?.contains(text, true) == true }
                    .map { tree.folder("album", it.id, it.title.orEmpty(), it.thumbnailUrl) } +
                container.homeRepository.observePlaylistPreviews(PlaylistSortBy.Name, SortOrder.Ascending).first()
                    .filter { it.playlist.name.contains(text, true) }
                    .map { tree.folder("playlist", it.playlist.id.toString(), it.playlist.name, it.playlist.thumbnail) }
            }
            val songs = async { online { container.searchResultRepository.searchSongs(text, null)?.getOrThrow()?.items.orEmpty().map { it.asMediaItem } } }
            val artists = async { online { container.searchResultRepository.searchArtists(text, null)?.getOrThrow()?.items.orEmpty().map { tree.folder("artist", it.key, it.info?.name.orEmpty(), it.thumbnail?.url) } } }
            val albums = async { online { container.searchResultRepository.searchAlbums(text, null)?.getOrThrow()?.items.orEmpty().map { tree.folder("album", it.key, it.info?.name.orEmpty(), it.thumbnail?.url) } } }
            val playlists = async { online { container.searchResultRepository.searchPlaylists(text, null)?.getOrThrow()?.items.orEmpty().map { tree.folder("online-playlist", it.key, it.info?.name.orEmpty(), it.thumbnail?.url) } } }
            val parent = AutoMediaId("search", text).encoded
            (local.await() + songs.await()).distinctBy { it.mediaId }.map { tree.track(it, parent) } +
                (localFolders.await() + artists.await() + albums.await() + playlists.await()).distinctBy { it.mediaId }
        }
        mutex.withLock { cache[text] = android.os.SystemClock.elapsedRealtime() to result }
        return result
    }

    @Suppress("TooGenericExceptionCaught") // Preserve local results for every provider/parsing failure.
    private suspend fun <T> online(block: suspend () -> List<T>): List<T> = try {
        kotlinx.coroutines.withTimeoutOrNull(8_000) { block() } ?: emptyList()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        android.util.Log.w("AutoSearch", "Online search unavailable", error)
        emptyList()
    }
}
