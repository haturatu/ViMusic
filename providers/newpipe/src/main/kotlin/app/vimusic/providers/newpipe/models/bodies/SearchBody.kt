package app.vimusic.providers.newpipe.models.bodies

import app.vimusic.providers.newpipe.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
    val context: Context = Context.DefaultWeb,
    val query: String,
    val params: String
)
