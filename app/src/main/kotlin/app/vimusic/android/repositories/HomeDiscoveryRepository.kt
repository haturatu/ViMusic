package app.vimusic.android.repositories

import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.requests.discoverPage

interface HomeDiscoveryRepository {
    suspend fun fetchDiscoverPage(): Result<Innertube.DiscoverPage>?
}

object InnertubeHomeDiscoveryRepository : HomeDiscoveryRepository {
    override suspend fun fetchDiscoverPage(): Result<Innertube.DiscoverPage>? =
        Innertube.discoverPage()
}
