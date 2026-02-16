package app.vimusic.android.repositories

import app.vimusic.providers.sponsorblock.SponsorBlock
import app.vimusic.providers.sponsorblock.models.Segment
import app.vimusic.providers.sponsorblock.requests.segments

interface SponsorBlockRepository {
    suspend fun fetchSegments(videoId: String): Result<List<Segment>>?
}

object ApiSponsorBlockRepository : SponsorBlockRepository {
    override suspend fun fetchSegments(videoId: String): Result<List<Segment>>? =
        SponsorBlock.segments(videoId)
}
