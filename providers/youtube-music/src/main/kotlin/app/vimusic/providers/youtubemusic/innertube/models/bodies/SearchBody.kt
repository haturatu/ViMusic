package app.vimusic.providers.youtubemusic.innertube.models.bodies

import app.vimusic.providers.youtubemusic.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
    val context: Context = Context.DefaultWeb,
    val query: String,
    val params: String
)
