package app.vimusic.providers.newpipe.models

import kotlinx.serialization.Serializable

@Serializable
data class ResponseContext(
    val visitorData: String?
)
