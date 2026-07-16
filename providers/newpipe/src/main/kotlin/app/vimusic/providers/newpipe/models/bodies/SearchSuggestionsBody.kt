package app.vimusic.providers.newpipe.models.bodies

import app.vimusic.providers.newpipe.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchSuggestionsBody(
    val context: Context = Context.DefaultWeb,
    val input: String
)
