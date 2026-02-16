package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.models.Artist
import app.vimusic.android.repositories.ArtistRepository
import app.vimusic.providers.innertube.Innertube
import kotlinx.coroutines.flow.Flow

class ArtistViewModel(
    private val browseId: String,
    private val repository: ArtistRepository
) : ViewModel() {
    fun observeArtist(): Flow<Artist?> = repository.observeArtist(browseId)

    suspend fun fetchArtistPage() = repository.fetchArtistPage(browseId)

    fun upsertArtistFromPage(
        currentArtist: Artist?,
        page: Innertube.ArtistPage
    ) {
        repository.upsertArtist(
            Artist(
                id = browseId,
                name = page.name,
                thumbnailUrl = page.thumbnail?.url,
                timestamp = System.currentTimeMillis(),
                bookmarkedAt = currentArtist?.bookmarkedAt
            )
        )
    }

    fun toggleBookmark(artist: Artist?) {
        artist ?: return
        repository.updateArtist(
            artist.copy(
                bookmarkedAt = if (artist.bookmarkedAt == null) System.currentTimeMillis() else null
            )
        )
    }

    suspend fun songsPage(
        artistPage: Innertube.ArtistPage?,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.SongItem>?> =
        repository.artistSongsPage(artistPage = artistPage, continuation = continuation)

    suspend fun albumsPage(
        artistPage: Innertube.ArtistPage?,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.AlbumItem>?> =
        repository.artistAlbumsPage(artistPage = artistPage, continuation = continuation)

    suspend fun singlesPage(
        artistPage: Innertube.ArtistPage?,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.AlbumItem>?> =
        repository.artistSinglesPage(artistPage = artistPage, continuation = continuation)

    companion object {
        fun factory(
            browseId: String,
            repository: ArtistRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ArtistViewModel(browseId = browseId, repository = repository) as T
        }
    }
}
