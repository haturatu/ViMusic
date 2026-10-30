package app.vimusic.providers.github.requests

import app.vimusic.providers.github.GitHub
import app.vimusic.providers.github.models.Release
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun GitHub.releases(
    owner: String,
    repo: String,
    page: Int = 1,
    pageSize: Int = 30
) = runCatchingCancellable {
    httpClient.getJson<List<Release>>("repos/$owner/$repo/releases") {
        withPagination(page = page, size = pageSize)
    }
}
