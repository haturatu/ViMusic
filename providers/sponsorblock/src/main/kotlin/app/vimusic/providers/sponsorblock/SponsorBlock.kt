package app.vimusic.providers.sponsorblock

import app.vimusic.providers.utils.ProviderHttpClient

object SponsorBlock {
    internal val httpClient by lazy {
        ProviderHttpClient(
            baseUrl = "https://sponsor.ajay.app",
            headers = mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
            ),
        )
    }
}
