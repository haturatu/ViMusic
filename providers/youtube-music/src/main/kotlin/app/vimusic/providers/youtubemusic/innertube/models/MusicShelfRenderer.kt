package app.vimusic.providers.youtubemusic.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicShelfRenderer(
    val bottomEndpoint: NavigationEndpoint?,
    val contents: List<Content>?,
    val continuations: List<Continuation>?,
    val title: Runs?
) {
    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    ) {
        @Serializable
        data class ContinuationItemRenderer(
            val continuationEndpoint: ContinuationEndpoint? = null,
        ) {
            @Serializable
            data class ContinuationEndpoint(
                val continuationCommand: ContinuationCommand? = null,
            ) {
                @Serializable
                data class ContinuationCommand(
                    val token: String? = null,
                )
            }
        }

        val runs: Pair<List<Runs.Run>, List<List<Runs.Run>>>
            get() = musicResponsiveListItemRenderer
                ?.flexColumns
                ?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                .orEmpty() to
                    musicResponsiveListItemRenderer
                        ?.flexColumns
                        ?.let { it.getOrNull(1) ?: it.lastOrNull() }
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text
                        ?.splitBySeparator()
                        .orEmpty()

        val thumbnail: Thumbnail?
            get() = musicResponsiveListItemRenderer
                ?.thumbnail
                ?.musicThumbnailRenderer
                ?.thumbnail
                ?.thumbnails
                ?.firstOrNull()
    }
}
