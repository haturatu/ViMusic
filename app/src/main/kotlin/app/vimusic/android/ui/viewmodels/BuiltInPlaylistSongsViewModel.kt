package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.models.Song
import app.vimusic.android.models.SongWithContentLength
import app.vimusic.android.repositories.BuiltInPlaylistRepository
import app.vimusic.core.data.enums.BuiltInPlaylist
import app.vimusic.core.data.enums.SongSortBy
import app.vimusic.core.data.enums.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BuiltInPlaylistSongsViewModel(
    private val repository: BuiltInPlaylistRepository
) : ViewModel() {
    fun observeSongs(
        builtInPlaylist: BuiltInPlaylist,
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        topPeriodMillis: Long?,
        topLength: Int,
        isOfflineCached: (SongWithContentLength) -> Boolean
    ): Flow<List<Song>> = when (builtInPlaylist) {
        BuiltInPlaylist.Favorites -> repository.observeFavorites(sortBy = sortBy, sortOrder = sortOrder)

        BuiltInPlaylist.Offline -> repository
            .observeOffline(sortBy = sortBy, sortOrder = sortOrder)
            .map { songs -> songs.filter(isOfflineCached).map(SongWithContentLength::song) }

        BuiltInPlaylist.Top -> repository.observeTop(periodMillis = topPeriodMillis, limit = topLength)

        BuiltInPlaylist.History -> repository.observeHistory()
    }

    companion object {
        fun factory(repository: BuiltInPlaylistRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BuiltInPlaylistSongsViewModel(repository = repository) as T
            }
    }
}
