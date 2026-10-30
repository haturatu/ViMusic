@file:Suppress("TooManyFunctions", "CyclomaticComplexMethod", "TooGenericExceptionCaught")

package app.vimusic.android.auto

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.vimusic.android.AppContainer
import app.vimusic.android.Database
import app.vimusic.android.R
import app.vimusic.android.models.Song
import app.vimusic.android.preferences.AppearancePreferences
import app.vimusic.core.ui.utils.songBundle
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.preferences.OrderPreferences
import app.vimusic.android.service.completedDownloadIds
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.completed
import app.vimusic.core.data.enums.SongSortBy
import app.vimusic.core.data.enums.SortOrder
import app.vimusic.providers.youtubemusic.innertube.models.NavigationEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** One browse tree for both MediaBrowser hosts and the Car App Library UI. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoLibraryTree(
    internal val context: Context,
    private val container: AppContainer,
    private val cached: (Song) -> Boolean,
    val search: AutoSearchCoordinator,
    private val currentSongId: () -> String? = { null },
) {
    private val items = object : LinkedHashMap<String, MediaItem>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaItem>?) = size > 4_096
    }
    private val snapshots = object : LinkedHashMap<String, Pair<Long, List<MediaItem>>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<MediaItem>>>?) = size > 16
    }

    fun invalidate() = synchronized(snapshots) { snapshots.clear() }
    fun known(id: String): MediaItem? = synchronized(items) { items[id] }
    private fun remember(item: MediaItem): MediaItem = item.also { synchronized(items) { items[it.mediaId] = it } }

    fun folder(kind: String, key: String = "", title: String, artwork: String? = null): MediaItem = remember(
        MediaItem.Builder().setMediaId(AutoMediaId(kind, key).encoded).setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
                .setArtworkUri(artwork?.toUri() ?: icon(R.drawable.playlist))
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build(),
        ).build(),
    )

    fun track(item: MediaItem, parent: String): MediaItem {
        remember(item)
        return remember(item.buildUpon().setMediaId(AutoMediaId("track", item.mediaId, parent).encoded)
            .setMediaMetadata(item.mediaMetadata.buildUpon().setIsBrowsable(false).setIsPlayable(true)
                .setSubtitle(item.mediaMetadata.artist).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setSupportedCommands(AutoCommandHandler.ITEM_ACTIONS).build()).build())
    }

    fun root(): MediaItem = folder("root", title = "ViMusic")
    fun title(id: String): String = known(id)?.mediaMetadata?.title?.toString() ?: "ViMusic"
    private fun named(kind: String, resource: Int) = folder(kind, title = context.getString(resource))
    private fun icon(drawable: Int): Uri = Uri.parse("android.resource://${context.packageName}/$drawable")

    suspend fun children(parent: String): List<MediaItem> {
        val id = requireNotNull(AutoMediaId.parse(parent)) { "Unknown parent" }
        val source = if (id.kind == "page") id.key else parent
        val all = rawChildren(source)
        val actionCount = all.takeWhile { AutoMediaId.parse(it.mediaId)?.kind in listOf("play-all", "shuffle") }.size
        val actions = if (id.kind == "page") emptyList() else all.take(actionCount)
        val start = if (id.kind == "page") id.offset.coerceAtMost(all.size) else actionCount
        val end = if (id.kind == "page") (start.toLong() + id.length).coerceAtMost(all.size.toLong()).toInt() else all.size
        val slice = all.subList(start, end)
        if (slice.size + actions.size <= PAGE_SIZE) return actions + slice
        var span = PAGE_SIZE
        while ((slice.size.toLong() + span - 1) / span > PAGE_SIZE - actions.size) span *= PAGE_SIZE
        return actions + (start until end step span).map { offset ->
            val pageId = AutoMediaId("page", source, offset = offset, length = minOf(span, end - offset)).encoded
            folder("page", source, "${offset - actionCount + 1}–${minOf(offset + span, end) - actionCount}")
                .buildUpon().setMediaId(pageId).build().also(::remember)
        }
    }

    private suspend fun rawChildren(parent: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val id = requireNotNull(AutoMediaId.parse(parent))
        when (id.kind) {
            "root" -> listOf(named("home", R.string.auto_home), named("library", R.string.library), named("discover", R.string.discover), named("offline", R.string.offline))
            "home" -> listOf(named("resume", R.string.auto_resume), named("quick-picks", R.string.quick_picks), named("history", R.string.auto_recent), named("favorites", R.string.favorites), named("top", R.string.auto_top))
            "library" -> listOf(named("songs", R.string.songs), named("playlists", R.string.playlists), named("albums", R.string.albums), named("artists", R.string.artists), named("local", R.string.local), named("history", R.string.history))
            "discover" -> listOf(named("recommended", R.string.auto_recommended), named("related", R.string.auto_related), named("radios", R.string.auto_radio), named("new-albums", R.string.auto_new_albums))
            "offline" -> listOf(named("cached", R.string.cached), named("downloads", R.string.auto_downloads), named("local", R.string.local), named("offline-playlists", R.string.auto_offline_playlists))
            "playlists", "offline-playlists" -> container.homeRepository.observePlaylistPreviews(OrderPreferences.playlistSortBy, OrderPreferences.playlistSortOrder).first().mapNotNull {
                if (id.kind == "offline-playlists" && offlinePlaylist(it.playlist.id).isEmpty()) null
                else folder(if (id.kind == "playlists") "playlist" else "offline-playlist", it.playlist.id.toString(), it.playlist.name, it.playlist.thumbnail)
            }
            "albums" -> Database.albums(OrderPreferences.albumSortBy, OrderPreferences.albumSortOrder).first().map { folder("album", it.id, it.title.orEmpty(), it.thumbnailUrl) }
            "artists" -> Database.artists(OrderPreferences.artistSortBy, OrderPreferences.artistSortOrder).first().map { folder("artist", it.id, it.name.orEmpty(), it.thumbnailUrl) }
            "artist" -> listOf(folder("artist-songs", id.key, context.getString(R.string.songs)), folder("artist-albums", id.key, context.getString(R.string.albums)), radioItem("artist", id.key, title(parent)))
            "artist-albums" -> onlineOrLocal(
                { artistAlbums(id.key) },
                { val name = Database.artist(id.key).first()?.name; Database.albumsByRowIdDesc().first().filter { name != null && it.authorsText?.contains(name, true) == true }.map { folder("album", it.id, it.title.orEmpty(), it.thumbnailUrl) } },
            )
            "new-albums" -> container.homeRepository.fetchDiscoverPage()?.getOrThrow()?.newReleaseAlbums.orEmpty().map { folder("album", it.key, it.info?.name.orEmpty(), it.thumbnail?.url) }
            "search" -> searchResults(id.key)
            "search-songs", "search-artists", "search-albums", "search-playlists" -> search.search(id.key, this@AutoLibraryTree).filter {
                val kind = AutoMediaId.parse(it.mediaId)?.kind
                when (id.kind) { "search-songs" -> kind == "track"; "search-artists" -> kind == "artist"; "search-albums" -> kind == "album"; else -> kind in listOf("online-playlist", "playlist") }
            }
            "radios" -> (container.mediaLibraryRepository.getRecentSongs(30)).filterNot { it.id.startsWith("local:") }.map { radioItem("song", it.id, it.title) }
            else -> {
                val songs = collection(parent)
                val tracks = songs.map { track(it, parent) }
                if (songs.isEmpty()) tracks else listOf(playAction("play-all", parent, R.string.auto_play_all), playAction("shuffle", parent, R.string.shuffle)) + tracks
            }
        }
    }

    private fun playAction(kind: String, parent: String, title: Int): MediaItem = remember(
        MediaItem.Builder().setMediaId(AutoMediaId(kind, parent).encoded)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(context.getString(title)).setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setArtworkUri(icon(if (kind == "shuffle") R.drawable.shuffle else R.drawable.play)).build()).build(),
    )

    fun radioItem(kind: String, key: String, title: String): MediaItem = remember(
        MediaItem.Builder().setMediaId(AutoMediaId("radio", AutoMediaId(kind, key).encoded).encoded)
            .setMediaMetadata(MediaMetadata.Builder().setTitle("${context.getString(R.string.auto_radio)} · $title").setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build(),
    )

    suspend fun collection(parent: String): List<MediaItem> = withContext(Dispatchers.IO) {
        synchronized(snapshots) { snapshots[parent]?.takeIf { android.os.SystemClock.elapsedRealtime() - it.first < 60_000 }?.let { return@withContext it.second } }
        val id = requireNotNull(AutoMediaId.parse(parent))
        val result: List<MediaItem> = when (id.kind) {
            "songs" -> container.songsRepository.songs(OrderPreferences.songSortBy, OrderPreferences.songSortOrder).map { it.asMediaItem }
            "local" -> container.mediaLibraryRepository.getLocalSongs(OrderPreferences.localSongSortBy, OrderPreferences.localSongSortOrder).map { it.asMediaItem }
            "favorites" -> Database.favorites().first().map { it.asMediaItem }
            "history" -> Database.events().first().map { it.song }.distinctBy { it.id }.map { it.asMediaItem }
            "top" -> container.mediaLibraryRepository.getTopSongs(DataPreferences.topListPeriod.duration?.inWholeMilliseconds, DataPreferences.topListLength).map { it.asMediaItem }
            "cached" -> allSongs().filter { it.id.startsWith("local:") || cached(it) }.map { it.asMediaItem }
            "downloads" -> { val ids = completedDownloadIds(context); allSongs().filter { it.id in ids }.map { it.asMediaItem } }
            "playlist" -> Database.playlistSongs(requireNotNull(id.key.toLongOrNull())).first().map { it.asMediaItem }
            "offline-playlist" -> offlinePlaylist(requireNotNull(id.key.toLongOrNull())).map { it.asMediaItem }
            "album" -> onlineOrLocal(
                { val page = container.albumRepository.fetchAlbumPage(id.key)?.getOrThrow(); if (page == null) emptyList() else Result.success(page).completed().getOrThrow().songsPage?.items.orEmpty().map { it.asMediaItem } },
                { container.albumRepository.observeAlbumSongs(id.key).first().map { it.asMediaItem } },
            )
            "online-playlist" -> container.playlistRepository.fetchPlaylistPage(id.key, null, null, false)?.songsPage?.items.orEmpty().map { it.asMediaItem }
            "artist-songs" -> onlineOrLocal(
                {
                    val page = container.artistRepository.fetchArtistPage(id.key)?.getOrThrow()
                    val songs = mutableListOf<MediaItem>(); var continuation: String? = null; val seen = mutableSetOf<String>()
                    do {
                        val result = container.artistRepository.artistSongsPage(page, continuation).getOrThrow()
                        songs += result?.items.orEmpty().map { it.asMediaItem }
                        continuation = result?.continuation
                    } while (continuation != null && seen.add(continuation))
                    songs
                },
                { Database.artistSongs(id.key).first().map { it.asMediaItem } },
            )
            "quick-picks", "related" -> {
                val source = if (id.kind == "quick-picks" && DataPreferences.quickPicksSource == DataPreferences.QuickPicksSource.Trending) {
                    container.homeRepository.observeTrendingSong()
                } else container.homeRepository.observeLastInteractionSong()
                val seed = (if (id.kind == "related") currentSongId() else null)
                    ?.takeUnless { it.startsWith("local:") }
                    ?: source.first()?.id?.takeUnless { it.startsWith("local:") }
                    ?: "J7p4bzqLvCw"
                val page = if (id.kind == "quick-picks") container.homeRepository.getCachedQuickPicksIfAvailable() else null
                val related = (page ?: seed?.let { container.homeRepository.fetchRelatedPage(it)?.getOrThrow() })?.songs.orEmpty()
                if (related.isEmpty() && seed == null) container.homeRepository.fetchDiscoverPage()?.getOrThrow()?.trending?.songs.orEmpty().map { it.asMediaItem }
                else related.map { it.asMediaItem }
            }
            "recommended" -> container.homeRepository.fetchDiscoverPage()?.getOrThrow()?.trending?.songs.orEmpty().map { it.asMediaItem }
            "resume" -> container.playerRepository.loadQueue().map { AutoPlaybackResumption.restoreItem(it.mediaItem) }
            "search" -> search.search(id.key, this@AutoLibraryTree).filter { it.mediaMetadata.isPlayable == true }.map { canonical(it) }
            else -> emptyList()
        }
        val playable = container.playerRepository.filterBlacklistedSongs(result).filter {
            !AppearancePreferences.hideExplicit || it.mediaMetadata.extras?.songBundle?.explicit != true
        }
        playable.forEach(::remember)
        synchronized(snapshots) { snapshots[parent] = android.os.SystemClock.elapsedRealtime() to playable }
        playable
    }

    suspend fun item(mediaId: String): MediaItem {
        known(mediaId)?.let { return it }
        val id = AutoMediaId.parse(mediaId)
        if (id == null || id.kind == "track") {
            val canonical = canonical(MediaItem.Builder().setMediaId(mediaId).build())
            return canonical.buildUpon().setMediaId(mediaId).setMediaMetadata(
                canonical.mediaMetadata.buildUpon().setIsPlayable(true).setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build(),
            ).build().also(::remember)
        }
        if (id.kind in listOf("play-all", "shuffle")) return playAction(id.kind, id.key, if (id.kind == "shuffle") R.string.shuffle else R.string.auto_play_all)
        if (id.kind == "radio") return radioItem(AutoMediaId.parse(id.key)?.kind ?: "song", AutoMediaId.parse(id.key)?.key ?: id.key, id.key).buildUpon().setMediaId(mediaId).build().also(::remember)
        val labels = mapOf(
            "root" to R.string.auto_home, "home" to R.string.auto_home, "library" to R.string.library,
            "discover" to R.string.discover, "offline" to R.string.offline, "resume" to R.string.auto_resume,
            "quick-picks" to R.string.quick_picks, "history" to R.string.history, "favorites" to R.string.favorites,
            "top" to R.string.auto_top, "songs" to R.string.songs, "local" to R.string.local,
            "playlists" to R.string.playlists, "albums" to R.string.albums, "artists" to R.string.artists,
            "cached" to R.string.cached, "downloads" to R.string.auto_downloads,
            "offline-playlists" to R.string.auto_offline_playlists, "recommended" to R.string.auto_recommended,
            "related" to R.string.auto_related, "radios" to R.string.auto_radio, "new-albums" to R.string.auto_new_albums,
            "artist-songs" to R.string.songs, "artist-albums" to R.string.albums,
            "search" to R.string.auto_search, "search-songs" to R.string.songs, "search-artists" to R.string.artists,
            "search-albums" to R.string.albums, "search-playlists" to R.string.playlists,
        )
        val label = when (id.kind) {
            "playlist", "offline-playlist" -> Database.playlist(requireNotNull(id.key.toLongOrNull())).first()?.name ?: id.key
            "album" -> Database.album(id.key).first()?.title ?: id.key
            "artist" -> Database.artist(id.key).first()?.name ?: id.key
            "page" -> "${id.offset + 1}–${id.offset.toLong() + id.length}"
            "online-playlist" -> id.key
            else -> context.getString(requireNotNull(labels[id.kind]) { "Unknown collection" })
        }
        return folder(id.kind, id.key, label).buildUpon().setMediaId(mediaId).build().also(::remember)
    }

    suspend fun canonical(item: MediaItem): MediaItem {
        val parsed = AutoMediaId.parse(item.mediaId) ?: return AutoPlaybackResumption.restoreItem(item)
        require(parsed.kind == "track") { "Not a track" }
        known(parsed.key)?.let { return it }
        Database.song(parsed.key).first()?.let { return it.asMediaItem.also(::remember) }
        return parsed.parent?.let { collection(it).firstOrNull { track -> track.mediaId == parsed.key } }
            ?: throw IllegalArgumentException("Song no longer exists in this collection")
    }

    suspend fun searchResults(query: String): List<MediaItem> {
        val all = search.search(query, this)
        val folders = listOf(
            folder("search-songs", query, context.getString(R.string.songs)),
            folder("search-artists", query, context.getString(R.string.artists)),
            folder("search-albums", query, context.getString(R.string.albums)),
            folder("search-playlists", query, context.getString(R.string.playlists)),
        )
        return folders + all.filter { it.mediaMetadata.isPlayable == true }.take(20) + all.filter { it.mediaMetadata.isBrowsable == true }.take(20)
    }

    suspend fun radioEndpoint(id: String): NavigationEndpoint.Endpoint.Watch? {
        val parsed = AutoMediaId.parse(id)
        if (parsed?.kind == "radio") return radioEndpoint(parsed.key)
        if (id.startsWith("local:") || parsed?.key?.startsWith("local:") == true) return null
        if (parsed?.kind == "artist") return container.artistRepository.fetchArtistPage(parsed.key)?.getOrThrow()?.radioEndpoint
        return NavigationEndpoint.Endpoint.Watch(videoId = if (parsed?.kind == "track") parsed.key else parsed?.key ?: id)
    }

    private suspend fun artistAlbums(artistId: String): List<MediaItem> {
        val page = container.artistRepository.fetchArtistPage(artistId)?.getOrThrow()
        val albums = mutableListOf<MediaItem>()
        var continuation: String? = null
        val seen = mutableSetOf<String>()
        do {
            val result = container.artistRepository.artistAlbumsPage(page, continuation).getOrThrow()
            albums += result?.items.orEmpty().map { folder("album", it.key, it.info?.name.orEmpty(), it.thumbnail?.url) }
            continuation = result?.continuation
        } while (continuation != null && seen.add(continuation))
        return albums.distinctBy { it.mediaId }
    }

    private suspend fun allSongs(): List<Song> = container.songsRepository.songs(SongSortBy.DateAdded, SortOrder.Descending)
    private suspend fun offlinePlaylist(id: Long) = Database.playlistSongs(id).first().filter { it.id.startsWith("local:") || cached(it) }
    private suspend fun <T> onlineOrLocal(online: suspend () -> List<T>, local: suspend () -> List<T>): List<T> = try {
        online().ifEmpty { local() }
    } catch (error: CancellationException) { throw error } catch (error: Exception) {
        android.util.Log.w("AutoLibrary", "Using local library after network failure", error); local()
    }
    companion object { const val PAGE_SIZE = 50 }
}
