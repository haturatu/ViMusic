package app.vimusic.providers.sponsorblock.requests

import app.vimusic.providers.sponsorblock.SponsorBlock
import app.vimusic.providers.sponsorblock.models.Action
import app.vimusic.providers.sponsorblock.models.Category
import app.vimusic.providers.sponsorblock.models.Segment
import app.vimusic.providers.utils.SerializableUUID
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun SponsorBlock.segments(
    videoId: String,
    categories: List<Category>? = listOf(Category.Sponsor, Category.OfftopicMusic, Category.PoiHighlight),
    actions: List<Action>? = listOf(Action.Skip, Action.POI),
    segments: List<SerializableUUID>? = null
) = runCatchingCancellable {
    httpClient.getJson<List<Segment>>("/api/skipSegments") {
        addQueryParameter("videoID", videoId)
        if (!categories.isNullOrEmpty()) categories.forEach { addQueryParameter("category", it.serialName) }
        if (!actions.isNullOrEmpty()) actions.forEach { addQueryParameter("action", it.serialName) }
        if (!segments.isNullOrEmpty()) segments.forEach { addQueryParameter("requiredSegment", it.toString()) }
        addQueryParameter("service", "YouTube")
    }
}
