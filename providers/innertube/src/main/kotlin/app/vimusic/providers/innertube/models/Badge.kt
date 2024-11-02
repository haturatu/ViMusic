package app.vimusic.providers.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Badge(
    val musicInlineBadgeRenderer: MusicInlineBadgeRenderer?
) {
    @Serializable
    data class MusicInlineBadgeRenderer(
        val icon: MusicNavigationButtonRenderer.Icon
    )
}

val List<Badge>?.isExplicit
    get() = this?.find {
        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
    } != null
