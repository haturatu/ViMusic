package app.vimusic.providers.newpipe.models.bodies

import app.vimusic.providers.newpipe.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class QueueBody(
    val context: Context = Context.DefaultWeb,
    val videoIds: List<String>? = null,
    val playlistId: String? = null
)
