package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import app.vimusic.android.repositories.PlayerRepository

class PlayerViewModel(
    private val repository: PlayerRepository
) : ViewModel() {
    fun insertSong(mediaItem: MediaItem) = repository.insertSong(mediaItem)

    fun observeLikedAt(songId: String) = repository.observeLikedAt(songId)

    fun setLikedAt(songId: String, likedAt: Long?) = repository.setLikedAt(songId = songId, likedAt = likedAt)

    fun observeFormat(songId: String) = repository.observeFormat(songId)

    suspend fun refreshFormat(songId: String, mediaItem: MediaItem?) =
        repository.refreshFormat(songId = songId, mediaItem = mediaItem)

    fun observeLoudnessBoost(songId: String) = repository.observeLoudnessBoost(songId)

    fun setLoudnessBoost(songId: String, loudnessBoost: Float?) =
        repository.setLoudnessBoost(songId = songId, loudnessBoost = loudnessBoost)

    companion object {
        fun factory(repository: PlayerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlayerViewModel(repository = repository) as T
            }
    }
}
