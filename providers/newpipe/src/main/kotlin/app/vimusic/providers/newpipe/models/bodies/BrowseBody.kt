package app.vimusic.providers.newpipe.models.bodies

import app.vimusic.providers.newpipe.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context = Context.DefaultWeb,
    val browseId: String,
    val params: String? = null
)
