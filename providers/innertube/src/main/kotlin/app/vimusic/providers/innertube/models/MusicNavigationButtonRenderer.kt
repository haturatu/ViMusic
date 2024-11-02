package app.vimusic.providers.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicNavigationButtonRenderer(
    val buttonText: Runs,
    val solid: Solid?,
    val iconStyle: IconStyle?,
    val clickCommand: NavigationEndpoint
) {
    val isMood: Boolean
        get() = clickCommand.browseEndpoint?.browseId == "FEmusic_moods_and_genres_category"

    @Serializable
    data class Solid(
        val leftStripeColor: Long
    )

    @Serializable
    data class IconStyle(
        val icon: Icon
    )

    @Serializable
    data class Icon(
        val iconType: String
    )
}
