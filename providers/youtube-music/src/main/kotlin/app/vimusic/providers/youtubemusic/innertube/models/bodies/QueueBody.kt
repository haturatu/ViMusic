package app.vimusic.providers.youtubemusic.innertube.models.bodies

import app.vimusic.providers.youtubemusic.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class QueueBody(
    val context: Context = Context.DefaultWeb,
    val videoIds: List<String>? = null,
    val playlistId: String? = null
)
