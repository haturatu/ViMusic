package app.vimusic.providers.youtubemusic.innertube.requests

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.BrowseResponse
import app.vimusic.providers.youtubemusic.innertube.models.ContinuationResponse
import app.vimusic.providers.youtubemusic.innertube.models.MusicCarouselShelfRenderer
import app.vimusic.providers.youtubemusic.innertube.models.MusicShelfRenderer
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.youtubemusic.innertube.utils.from
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun YoutubeMusicInnertube.playlistPage(body: BrowseBody) = runCatchingCancellable {
    val response = client.post<BrowseResponse>(BROWSE, body, context = body.context)

    if (response.contents?.twoColumnBrowseResultsRenderer == null) {
        val header = response
            .header
            ?.musicDetailHeaderRenderer

        val contents = response
            .contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents

        val musicShelfRenderer = contents
            ?.firstOrNull()
            ?.musicShelfRenderer

        val musicCarouselShelfRenderer = contents
            ?.getOrNull(1)
            ?.musicCarouselShelfRenderer

        YoutubeMusicInnertube.PlaylistOrAlbumPage(
            title = header
                ?.title
                ?.text,
            description = header
                ?.description
                ?.text,
            thumbnail = header
                ?.thumbnail
                ?.musicThumbnailRenderer
                ?.thumbnail
                ?.thumbnails
                ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) },
            authors = header
                ?.subtitle
                ?.splitBySeparator()
                ?.getOrNull(1)
                ?.map(YoutubeMusicInnertube::Info),
            year = header
                ?.subtitle
                ?.splitBySeparator()
                ?.getOrNull(2)
                ?.firstOrNull()
                ?.text,
            url = response
                .microformat
                ?.microformatDataRenderer
                ?.urlCanonical,
            songsPage = musicShelfRenderer.toSongsPage(),
            otherVersions = musicCarouselShelfRenderer
                ?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(YoutubeMusicInnertube.AlbumItem::from),
            otherInfo = header
                ?.secondSubtitle
                ?.text
        )
    } else {
        val header = response
            .contents
            .twoColumnBrowseResultsRenderer
            .tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
            ?.firstOrNull()
            ?.musicResponsiveHeaderRenderer

        val contents = response
            .contents
            .twoColumnBrowseResultsRenderer
            .secondaryContents
            ?.sectionListRenderer
            ?.contents

        val musicShelfRenderer = contents
            ?.firstOrNull()
            ?.musicShelfRenderer

        val musicCarouselShelfRenderer = contents
            ?.getOrNull(1)
            ?.musicCarouselShelfRenderer

        YoutubeMusicInnertube.PlaylistOrAlbumPage(
            title = header
                ?.title
                ?.text,
            description = header
                ?.description
                ?.description
                ?.text,
            thumbnail = header
                ?.thumbnail
                ?.musicThumbnailRenderer
                ?.thumbnail
                ?.thumbnails
                ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) },
            authors = header
                ?.straplineTextOne
                ?.splitBySeparator()
                ?.getOrNull(0)
                ?.map(YoutubeMusicInnertube::Info),
            year = header
                ?.subtitle
                ?.splitBySeparator()
                ?.getOrNull(1)
                ?.firstOrNull()
                ?.text,
            url = response
                .microformat
                ?.microformatDataRenderer
                ?.urlCanonical,
            songsPage = musicShelfRenderer.toSongsPage(),
            otherVersions = musicCarouselShelfRenderer
                ?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(YoutubeMusicInnertube.AlbumItem::from),
            otherInfo = header
                ?.secondSubtitle
                ?.text
        )
    }
}

suspend fun YoutubeMusicInnertube.playlistPage(body: ContinuationBody) = runCatchingCancellable {
    val response = client.post<ContinuationResponse>(
        BROWSE,
        body,
        parameters = mapOf(
            "continuation" to body.continuation,
            "ctoken" to body.continuation,
            "type" to "next",
        ),
        context = body.context,
    )

    val sectionListRenderer = response
        .continuationContents
        ?.sectionListContinuation

    val musicShelfRenderer = response
        .continuationContents
        ?.musicShelfContinuation
        ?: sectionListRenderer
            ?.contents
            ?.firstOrNull { it.musicShelfRenderer != null }
            ?.musicShelfRenderer

    val appendedContents = response
        .onResponseReceivedActions
        ?.flatMap { action ->
            action
                .appendContinuationItemsAction
                ?.continuationItems
                .orEmpty()
        }

    musicShelfRenderer?.toSongsPage()
        ?: appendedContents.toSongsPage()
}

private fun MusicShelfRenderer?.toSongsPage() = this
    ?.contents
    .toSongsPage(
        continuation = this?.continuation(),
)

private fun List<MusicShelfRenderer.Content>?.toSongsPage(
    continuation: String? = continuation(),
) = YoutubeMusicInnertube.ItemsPage(
    items = this
        ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
        ?.mapNotNull(YoutubeMusicInnertube.SongItem::from),
    continuation = continuation,
)

private fun MusicShelfRenderer.continuation() = continuations
    ?.firstOrNull()
    ?.nextContinuationData
    ?.continuation
    ?.takeIf(String::isNotBlank)
    ?: contents.continuation()

private fun List<MusicShelfRenderer.Content>?.continuation() = this
    ?.asReversed()
    ?.firstNotNullOfOrNull(MusicShelfRenderer.Content::continuationItemRenderer)
    ?.continuationEndpoint
    ?.continuationCommand
    ?.token
    ?.takeIf(String::isNotBlank)
