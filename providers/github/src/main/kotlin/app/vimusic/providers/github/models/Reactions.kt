package app.vimusic.providers.github.models

import app.vimusic.providers.utils.SerializableUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Reactions(
    val url: SerializableUrl,
    @SerialName("total_count")
    val count: Int,
    @SerialName("+1")
    val likes: Int,
    @SerialName("-1")
    val dislikes: Int,
    @SerialName("laugh")
    val laughs: Int,
    val confused: Int,
    @SerialName("heart")
    val hearts: Int,
    @SerialName("hooray")
    val hoorays: Int,
    val eyes: Int,
    @SerialName("rocket")
    val rockets: Int
)
