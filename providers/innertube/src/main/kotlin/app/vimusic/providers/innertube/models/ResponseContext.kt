package app.vimusic.providers.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ResponseContext(
    val visitorData: String?
)
