package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import app.vimusic.android.models.Lyrics
import app.vimusic.android.repositories.PlayerLyricsRepository
import app.vimusic.providers.lrclib.models.Track
import kotlin.time.Duration

class PlayerLyricsViewModel(
    private val repository: PlayerLyricsRepository
) : ViewModel() {
    fun observeLyrics(songId: String) = repository.observeLyrics(songId)

    fun upsertLyrics(lyrics: Lyrics) = repository.upsertLyrics(lyrics)

    suspend fun fetchYoutubeMusicInnertubeLyrics(mediaId: String) = repository.fetchYoutubeMusicInnertubeLyrics(mediaId)

    suspend fun fetchBestLrcLibLyrics(
        artist: String,
        title: String,
        duration: Duration,
        album: String?,
        synced: Boolean = true
    ) = repository.fetchBestLrcLibLyrics(
        artist = artist,
        title = title,
        duration = duration,
        album = album,
        synced = synced
    )

    suspend fun fetchKuGouLyrics(artist: String, title: String, durationSeconds: Long) =
        repository.fetchKuGouLyrics(
            artist = artist,
            title = title,
            durationSeconds = durationSeconds
        )

    suspend fun searchSyncedLrcLib(query: String): Result<List<Track>>? =
        repository.searchSyncedLrcLib(query)

    companion object {
        fun factory(repository: PlayerLyricsRepository) = viewModelFactory {
            PlayerLyricsViewModel(repository = repository)
        }
    }
}
