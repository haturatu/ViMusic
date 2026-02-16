package app.vimusic.android.repositories

import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.BrowseBody
import app.vimusic.providers.innertube.requests.BrowseResult
import app.vimusic.providers.innertube.requests.browse

interface MoodRepository {
    suspend fun fetchMoodPage(browseId: String, params: String?): Result<BrowseResult>?
}

object InnertubeMoodRepository : MoodRepository {
    override suspend fun fetchMoodPage(browseId: String, params: String?): Result<BrowseResult>? =
        Innertube.browse(BrowseBody(browseId = browseId, params = params))
}
