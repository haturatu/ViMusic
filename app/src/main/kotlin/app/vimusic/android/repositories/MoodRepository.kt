package app.vimusic.android.repositories

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.bodies.BrowseBody
import app.vimusic.providers.newpipe.requests.BrowseResult
import app.vimusic.providers.newpipe.requests.browse

interface MoodRepository {
    suspend fun fetchMoodPage(browseId: String, params: String?): Result<BrowseResult>?
}

object NewPipeMusicMoodRepository : MoodRepository {
    override suspend fun fetchMoodPage(browseId: String, params: String?): Result<BrowseResult>? =
        NewPipeMusic.browse(BrowseBody(browseId = browseId, params = params))
}
