@file:Suppress("TooGenericExceptionCaught") // UI state must terminate for every non-cancellation failure.

package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vimusic.android.models.Artist
import app.vimusic.android.repositories.ArtistRepository
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface ArtistUiState {
    data object Loading : ArtistUiState
    data class Content(
        val artist: Artist?,
        val page: YoutubeMusicInnertube.ArtistPage,
    ) : ArtistUiState
    data class Error(
        val artist: Artist?,
        val throwable: Throwable,
        val stalePage: YoutubeMusicInnertube.ArtistPage? = null,
    ) : ArtistUiState
}

class ArtistViewModel(
    private val browseId: String,
    private val repository: ArtistRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<ArtistUiState>(ArtistUiState.Loading)
    val uiState: StateFlow<ArtistUiState> = mutableUiState.asStateFlow()
    private var currentArtist: Artist? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeArtist(browseId).collect { artist ->
                currentArtist = artist
                mutableUiState.value = when (val state = mutableUiState.value) {
                    ArtistUiState.Loading -> state
                    is ArtistUiState.Content -> state.copy(artist = artist)
                    is ArtistUiState.Error -> state.copy(artist = artist)
                }
            }
        }
    }

    fun loadArtist(
        cachedPage: YoutubeMusicInnertube.ArtistPage? = null,
        force: Boolean = false,
    ) {
        if (!force && mutableUiState.value is ArtistUiState.Content) return
        if (!force && cachedPage != null) {
            mutableUiState.value = ArtistUiState.Content(currentArtist, cachedPage)
            return
        }
        if (loadJob?.isActive == true) return
        val stalePage = when (val state = mutableUiState.value) {
            is ArtistUiState.Content -> state.page
            is ArtistUiState.Error -> state.stalePage
            ArtistUiState.Loading -> cachedPage
        }
        mutableUiState.value = ArtistUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val result = repository.fetchArtistPage(browseId)
                    ?: Result.failure(IllegalStateException("Artist provider returned null"))
                result.fold(
                    onSuccess = { page ->
                        if (page == null) {
                            mutableUiState.value = ArtistUiState.Error(
                                currentArtist,
                                IllegalStateException("Artist page was empty"),
                                stalePage,
                            )
                        } else {
                            mutableUiState.value = ArtistUiState.Content(currentArtist, page)
                            upsertArtistFromPage(currentArtist, page)
                        }
                    },
                    onFailure = { error ->
                        mutableUiState.value = ArtistUiState.Error(currentArtist, error, stalePage)
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableUiState.value = ArtistUiState.Error(currentArtist, error, stalePage)
            }
        }
    }

    fun upsertArtistFromPage(
        currentArtist: Artist?,
        page: YoutubeMusicInnertube.ArtistPage
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
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.SongItem>?> =
        repository.artistSongsPage(artistPage = artistPage, continuation = continuation)

    suspend fun albumsPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.AlbumItem>?> =
        repository.artistAlbumsPage(artistPage = artistPage, continuation = continuation)

    suspend fun singlesPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.AlbumItem>?> =
        repository.artistSinglesPage(artistPage = artistPage, continuation = continuation)

    companion object {
        fun factory(
            browseId: String,
            repository: ArtistRepository
        ) = viewModelFactory {
            ArtistViewModel(browseId = browseId, repository = repository)
        }
    }
}
