package app.vimusic.providers.github

import app.vimusic.providers.utils.ProviderHttpClient
import okhttp3.HttpUrl

private const val API_VERSION = "2022-11-28"

object GitHub {
    internal val httpClient by lazy {
        ProviderHttpClient(
            baseUrl = "https://api.github.com",
            headers = mapOf(
                "X-GitHub-Api-Version" to API_VERSION,
                "Accept" to "application/vnd.github+json",
                "Content-Type" to "application/json",
            ),
        )
    }

    fun HttpUrl.Builder.withPagination(size: Int, page: Int) {
        require(page > 0) { "GitHub error: invalid page ($page), pagination starts at page 1" }
        require(size > 0) { "GitHub error: invalid page size ($size), a page has to have at least a single item" }

        addQueryParameter("per_page", size.toString())
        addQueryParameter("page", page.toString())
    }
}
