package app.vimusic.providers.sponsorblock.requests

import app.vimusic.providers.sponsorblock.SponsorBlock
import app.vimusic.providers.sponsorblock.models.Action
import app.vimusic.providers.sponsorblock.models.Category
import app.vimusic.providers.sponsorblock.models.Segment
import app.vimusic.providers.utils.SerializableUUID
import app.vimusic.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

suspend fun SponsorBlock.segments(
    videoId: String,
    categories: List<Category>? = listOf(Category.Sponsor, Category.OfftopicMusic, Category.PoiHighlight),
    actions: List<Action>? = listOf(Action.Skip, Action.POI),
    segments: List<SerializableUUID>? = null
) = runCatchingCancellable {
    httpClient.get("/api/skipSegments") {
        parameter("videoID", videoId)
        if (!categories.isNullOrEmpty()) categories.forEach { parameter("category", it.serialName) }
        if (!actions.isNullOrEmpty()) actions.forEach { parameter("action", it.serialName) }
        if (!segments.isNullOrEmpty()) segments.forEach { parameter("requiredSegment", it) }
        parameter("service", "YouTube")
    }.body<List<Segment>>()
}
