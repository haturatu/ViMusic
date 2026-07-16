package app.vimusic.providers.newpipe.models.bodies

import app.vimusic.providers.newpipe.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class ContinuationBody(
    val context: Context = Context.DefaultWeb,
    val continuation: String
)
