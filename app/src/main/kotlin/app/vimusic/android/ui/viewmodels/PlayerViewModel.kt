package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.repositories.PlayerRepository

class PlayerViewModel(
    private val repository: PlayerRepository
) : ViewModel() {
    fun observeLikedAt(songId: String) = repository.observeLikedAt(songId)

    fun setLikedAt(songId: String, likedAt: Long?) = repository.setLikedAt(songId = songId, likedAt = likedAt)

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
