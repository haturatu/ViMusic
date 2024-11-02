package app.vimusic.providers.sponsorblock.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class Segment(
    internal val segment: List<Double>,
    @SerialName("UUID")
    val uuid: String? = null,
    val category: Category,
    @SerialName("actionType")
    val action: Action,
    val description: String
) {
    val start get() = segment.first().seconds
    val end get() = segment[1].seconds
}
