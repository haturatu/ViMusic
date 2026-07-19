package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vimusic.android.models.Artist
import app.vimusic.android.repositories.ArtistRepository
import app.vimusic.android.ui.state.LoadState
import app.vimusic.android.ui.state.contentOrNull
import app.vimusic.android.ui.state.launchLoad
import app.vimusic.android.utils.requireValue
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class ArtistContent(
    val artist: Artist?,
    val page: YoutubeMusicInnertube.ArtistPage,
)

class ArtistViewModel(
    private val browseId: String,
    private val repository: ArtistRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<LoadState<ArtistContent>>(LoadState.Loading())
    val uiState: StateFlow<LoadState<ArtistContent>> = mutableUiState.asStateFlow()
    private var currentArtist: Artist? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeArtist(browseId).collect { artist ->
                currentArtist = artist
                mutableUiState.value = when (val state = mutableUiState.value) {
                    is LoadState.Content -> state.copy(value = state.value.copy(artist = artist))
                    is LoadState.Error -> state.copy(previous = state.previous?.copy(artist = artist))
                    LoadState.Idle,
                    is LoadState.Loading -> state
                }
            }
        }
    }

    fun loadArtist(
        cachedPage: YoutubeMusicInnertube.ArtistPage? = null,
        force: Boolean = false,
    ) {
        if (!force && mutableUiState.value is LoadState.Content) return
        if (loadJob?.isActive == true) return
        val previous = mutableUiState.value.contentOrNull()
            ?: cachedPage?.let { ArtistContent(currentArtist, it) }
        previous?.let { mutableUiState.value = LoadState.Content(it) }
        loadJob = mutableUiState.launchLoad(
            scope = viewModelScope,
            keepPreviousOnFailure = true,
            onSuccess = { content -> upsertArtistFromPage(currentArtist, content.page) },
        ) {
                repository.fetchArtistPage(browseId).requireValue(
                    nullResultMessage = "Artist request was not executed",
                    nullValueMessage = "Artist page was empty",
                ).getOrThrow().let { page -> ArtistContent(currentArtist, page) }
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
