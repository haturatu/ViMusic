package app.vimusic.providers.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class GridRenderer(
    val items: List<Item>?,
    val header: Header?
) {
    @Serializable
    data class Item(
        val musicNavigationButtonRenderer: MusicNavigationButtonRenderer?,
        val musicTwoRowItemRenderer: MusicTwoRowItemRenderer?
    )

    @Serializable
    data class Header(
        val gridHeaderRenderer: GridHeaderRenderer?
    )

    @Serializable
    data class GridHeaderRenderer(
        val title: Runs?
    )
}
