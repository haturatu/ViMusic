package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import app.vimusic.android.repositories.PlayerRepository
import app.vimusic.android.utils.launchDatabaseAction

class PlayerViewModel(
    private val repository: PlayerRepository
) : ViewModel() {
    suspend fun insertSong(mediaItem: MediaItem) = repository.insertSong(mediaItem)

    fun observeLikedAt(songId: String) = repository.observeLikedAt(songId)

    fun setLikedAt(songId: String, likedAt: Long?) {
        viewModelScope.launchDatabaseAction {
            repository.setLikedAt(songId = songId, likedAt = likedAt)
        }
    }

    fun observeLoudnessDb(songId: String) = repository.observeLoudnessDb(songId)

    fun observeFormat(songId: String) = repository.observeFormat(songId)

    suspend fun refreshFormat(songId: String, mediaItem: MediaItem?) =
        repository.refreshFormat(songId = songId, mediaItem = mediaItem)

    fun observeLoudnessBoost(songId: String) = repository.observeLoudnessBoost(songId)

    fun setLoudnessBoost(songId: String, loudnessBoost: Float?) {
        viewModelScope.launchDatabaseAction {
            repository.setLoudnessBoost(songId = songId, loudnessBoost = loudnessBoost)
        }
    }

    companion object {
        fun factory(repository: PlayerRepository) = viewModelFactory {
            PlayerViewModel(repository = repository)
        }
    }
}
