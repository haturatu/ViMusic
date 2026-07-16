package app.vimusic.android.repositories

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.requests.discoverPage

interface HomeDiscoveryRepository {
    suspend fun fetchDiscoverPage(): Result<NewPipeMusic.DiscoverPage>?
}

object NewPipeMusicHomeDiscoveryRepository : HomeDiscoveryRepository {
    override suspend fun fetchDiscoverPage(): Result<NewPipeMusic.DiscoverPage>? =
        NewPipeMusic.discoverPage()
}
