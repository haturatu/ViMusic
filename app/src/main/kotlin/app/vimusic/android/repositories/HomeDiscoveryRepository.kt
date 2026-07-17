package app.vimusic.android.repositories

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.requests.discoverPage

interface HomeDiscoveryRepository {
    suspend fun fetchDiscoverPage(): Result<YoutubeMusicInnertube.DiscoverPage>?
}

object YoutubeMusicInnertubeHomeDiscoveryRepository : HomeDiscoveryRepository {
    override suspend fun fetchDiscoverPage(): Result<YoutubeMusicInnertube.DiscoverPage>? =
        YoutubeMusicInnertube.discoverPage()
}
