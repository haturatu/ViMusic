package app.vimusic.providers.innertube.models.bodies

import app.vimusic.providers.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchSuggestionsBody(
    val context: Context = Context.DefaultWeb,
    val input: String
)