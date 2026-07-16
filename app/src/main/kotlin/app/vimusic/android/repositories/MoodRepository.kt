package app.vimusic.android.repositories

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.requests.BrowseResult
import app.vimusic.providers.youtubemusic.innertube.requests.browse

interface MoodRepository {
    suspend fun fetchMoodPage(browseId: String, params: String?): Result<BrowseResult>?
}

object YoutubeMusicInnertubeMoodRepository : MoodRepository {
    override suspend fun fetchMoodPage(browseId: String, params: String?): Result<BrowseResult>? =
        YoutubeMusicInnertube.browse(BrowseBody(browseId = browseId, params = params))
}
