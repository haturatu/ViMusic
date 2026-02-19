package app.vimusic.android.service

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import app.vimusic.android.MainActivity
import app.vimusic.android.R
import app.vimusic.android.appContainer
import app.vimusic.android.models.Album
import app.vimusic.android.models.PlaylistPreview
import app.vimusic.android.models.Song
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.preferences.OrderPreferences
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.forcePlayAtIndex
import app.vimusic.android.utils.intent
import app.vimusic.android.utils.activityPendingIntent
import app.vimusic.core.data.utils.CallValidator
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlayerMediaLibraryService : MediaLibraryService(), ServiceConnection {
    private val mediaLibraryRepository by lazy { applicationContext.appContainer.mediaLibraryRepository }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastSongs = emptyList<Song>()

    private var bound = false
    private var binder: PlayerService.Binder? = null
    private var session: MediaLibraryService.MediaLibrarySession? = null

    private val callValidator by lazy {
        CallValidator(applicationContext, R.xml.allowed_media_browser_callers)
    }

    override fun onCreate() {
        super.onCreate()
        startService(intent<PlayerService>())
    }

    override fun onDestroy() {
        session?.release()
        session = null
        if (bound) unbindService(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibraryService.MediaLibrarySession? {
        if (!callValidator.canCall(controllerInfo.packageName, controllerInfo.uid)) return null
        ensureBound()
        return session
    }

    override fun onServiceConnected(className: ComponentName, service: IBinder) {
        val binder = service as? PlayerService.Binder ?: return
        this.binder = binder
        bound = true

        if (session != null) return

        session = MediaLibraryService.MediaLibrarySession.Builder(this, binder.player, SessionCallback(binder))
            .setSessionActivity(activityPendingIntent<MainActivity>())
            .build()
    }

    override fun onServiceDisconnected(name: ComponentName) {
        bound = false
        binder = null
        session?.release()
        session = null
    }

    private fun ensureBound() {
        if (bound) return
        bindService(intent<PlayerService>(), this, Context.BIND_AUTO_CREATE)
    }

    private fun browseItem(
        id: String,
        title: String,
        subtitle: String? = null,
        iconUri: Uri? = null,
        isBrowsable: Boolean,
        isPlayable: Boolean
    ) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(iconUri)
                .setIsBrowsable(isBrowsable)
                .setIsPlayable(isPlayable)
                .build()
        )
        .build()

    private fun uriFor(@DrawableRes id: Int) = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(resources.getResourcePackageName(id))
        .appendPath(resources.getResourceTypeName(id))
        .appendPath(resources.getResourceEntryName(id))
        .build()

    private val rootMediaItem
        get() = browseItem(
            id = MediaId.ROOT.id,
            title = applicationInfo.loadLabel(packageManager).toString(),
            isBrowsable = true,
            isPlayable = false
        )

    private val songsMediaItem
        get() = browseItem(
            id = MediaId.SONGS.id,
            title = getString(R.string.songs),
            iconUri = uriFor(R.drawable.musical_notes),
            isBrowsable = true,
            isPlayable = false
        )

    private val playlistsMediaItem
        get() = browseItem(
            id = MediaId.PLAYLISTS.id,
            title = getString(R.string.playlists),
            iconUri = uriFor(R.drawable.playlist),
            isBrowsable = true,
            isPlayable = false
        )

    private val albumsMediaItem
        get() = browseItem(
            id = MediaId.ALBUMS.id,
            title = getString(R.string.albums),
            iconUri = uriFor(R.drawable.disc),
            isBrowsable = true,
            isPlayable = false
        )

    private val favoritesMediaItem
        get() = browseItem(
            id = MediaId.FAVORITES.id,
            title = getString(R.string.favorites),
            iconUri = uriFor(R.drawable.heart),
            isBrowsable = false,
            isPlayable = true
        )

    private val offlineMediaItem
        get() = browseItem(
            id = MediaId.OFFLINE.id,
            title = getString(R.string.offline),
            iconUri = uriFor(R.drawable.airplane),
            isBrowsable = false,
            isPlayable = true
        )

    private val topMediaItem
        get() = browseItem(
            id = MediaId.TOP.id,
            title = getString(
                R.string.format_my_top_playlist,
                DataPreferences.topListLength.toString()
            ),
            iconUri = uriFor(R.drawable.trending),
            isBrowsable = false,
            isPlayable = true
        )

    private val localMediaItem
        get() = browseItem(
            id = MediaId.LOCAL.id,
            title = getString(R.string.local),
            iconUri = uriFor(R.drawable.download),
            isBrowsable = false,
            isPlayable = true
        )

    private val shuffleMediaItem
        get() = browseItem(
            id = MediaId.SHUFFLE.id,
            title = getString(R.string.shuffle),
            iconUri = uriFor(R.drawable.shuffle),
            isBrowsable = false,
            isPlayable = true
        )

    private val Song.asBrowsableMediaItem
        get() = browseItem(
            id = (MediaId.SONGS / id).id,
            title = title,
            subtitle = artistsText,
            iconUri = thumbnailUrl?.toUri(),
            isBrowsable = false,
            isPlayable = true
        )

    private val PlaylistPreview.asBrowsableMediaItem
        get() = browseItem(
            id = (MediaId.PLAYLISTS / playlist.id.toString()).id,
            title = playlist.name,
            subtitle = resources.getQuantityString(
                R.plurals.song_count_plural,
                songCount,
                songCount
            ),
            iconUri = uriFor(R.drawable.playlist),
            isBrowsable = false,
            isPlayable = true
        )

    private val Album.asBrowsableMediaItem
        get() = browseItem(
            id = (MediaId.ALBUMS / id).id,
            title = title.orEmpty(),
            subtitle = authorsText.orEmpty(),
            iconUri = thumbnailUrl?.toUri(),
            isBrowsable = false,
            isPlayable = true
        )

    private inner class SessionCallback(
        private val binder: PlayerService.Binder
    ) : MediaLibraryService.MediaLibrarySession.Callback {
        @Suppress("CyclomaticComplexMethod")
        override fun onGetChildren(
            session: MediaLibraryService.MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                val result = runCatching {
                    val items = when (MediaId(parentId)) {
                        MediaId.ROOT -> mutableListOf(
                            songsMediaItem,
                            playlistsMediaItem,
                            albumsMediaItem
                        )

                        MediaId.SONGS ->
                            mediaLibraryRepository
                                .getRecentSongs(limit = 30)
                                .also { lastSongs = it }
                                .map { it.asBrowsableMediaItem }
                                .toMutableList()
                                .apply {
                                    if (isNotEmpty()) add(0, shuffleMediaItem)
                                }

                        MediaId.PLAYLISTS ->
                            mediaLibraryRepository
                                .getPlaylistPreviewsByDateAddedDesc()
                                .map { it.asBrowsableMediaItem }
                                .toMutableList()
                                .apply {
                                    add(0, favoritesMediaItem)
                                    add(1, offlineMediaItem)
                                    add(2, topMediaItem)
                                    add(3, localMediaItem)
                                }

                        MediaId.ALBUMS ->
                            mediaLibraryRepository
                                .getAlbumsByRowIdDesc()
                                .map { it.asBrowsableMediaItem }
                                .toMutableList()

                        else -> mutableListOf()
                    }
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                }
                future.set(
                    result.getOrElse {
                        LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                )
            }

            return future
        }

        override fun onGetLibraryRoot(
            session: MediaLibraryService.MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            LibraryResult.ofItem(
                rootMediaItem,
                MediaLibraryService.LibraryParams.Builder()
                    .setExtras(bundleOf("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT" to 1))
                    .build()
            )
        )

        override fun onGetItem(
            session: MediaLibraryService.MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when (MediaId(mediaId)) {
                MediaId.ROOT -> rootMediaItem
                MediaId.SONGS -> songsMediaItem
                MediaId.PLAYLISTS -> playlistsMediaItem
                MediaId.ALBUMS -> albumsMediaItem
                MediaId.FAVORITES -> favoritesMediaItem
                MediaId.OFFLINE -> offlineMediaItem
                MediaId.TOP -> topMediaItem
                MediaId.LOCAL -> localMediaItem
                MediaId.SHUFFLE -> shuffleMediaItem
                else -> null
            }

            return Futures.immediateFuture(
                if (item == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                else LibraryResult.ofItem(item, null)
            )
        }

        override fun onSearch(
            session: MediaLibraryService.MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            if (query.isNotBlank()) binder.playFromSearch(query)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibraryService.MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))

        @Suppress("CyclomaticComplexMethod")
        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()
            serviceScope.launch {
                val resolved = runCatching {
                    val item = mediaItems.firstOrNull() ?: return@runCatching mediaItems
                    val data = item.mediaId.split('/')
                    var index = 0

                    val mediaList = when (data.getOrNull(0)?.let { MediaId(it) }) {
                        MediaId.SHUFFLE -> lastSongs

                        MediaId.SONGS -> data.getOrNull(1)?.let { songId ->
                            index = lastSongs.indexOfFirst { it.id == songId }
                            lastSongs
                        }

                        MediaId.FAVORITES ->
                            mediaLibraryRepository.getFavoritesShuffled()

                        MediaId.OFFLINE ->
                            mediaLibraryRepository.getOfflineCachedShuffled { binder.isCached(it) }

                        MediaId.TOP -> {
                            val duration = DataPreferences.topListPeriod.duration
                            val length = DataPreferences.topListLength

                            mediaLibraryRepository.getTopSongs(
                                durationMillis = duration?.inWholeMilliseconds,
                                length = length
                            )
                        }

                        MediaId.LOCAL ->
                            mediaLibraryRepository.getLocalSongs(
                                sortBy = OrderPreferences.localSongSortBy,
                                sortOrder = OrderPreferences.localSongSortOrder
                            )

                        MediaId.PLAYLISTS ->
                            data
                                .getOrNull(1)
                                ?.toLongOrNull()
                                ?.let { playlistId ->
                                    mediaLibraryRepository.getPlaylistSongsShuffled(playlistId)
                                }

                        MediaId.ALBUMS ->
                            data
                                .getOrNull(1)
                                ?.let { albumId ->
                                    mediaLibraryRepository.getAlbumSongs(albumId)
                                }

                        else -> emptyList()
                    }

                    (mediaList?.map(Song::asMediaItem) ?: emptyList()).also {
                        if (it.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                binder.player.forcePlayAtIndex(
                                    items = it,
                                    index = index.coerceIn(0, it.size)
                                )
                            }
                        }
                    }
                }
                future.set(resolved.getOrElse { mediaItems })
            }

            return future
        }
    }

    @JvmInline
    private value class MediaId(val id: String) : CharSequence by id {
        companion object {
            val ROOT = MediaId("root")
            val SONGS = MediaId("songs")
            val PLAYLISTS = MediaId("playlists")
            val ALBUMS = MediaId("albums")

            val FAVORITES = MediaId("favorites")
            val OFFLINE = MediaId("offline")
            val TOP = MediaId("top")
            val LOCAL = MediaId("local")
            val SHUFFLE = MediaId("shuffle")
        }

        operator fun div(other: String) = MediaId("$id/$other")
    }
}
