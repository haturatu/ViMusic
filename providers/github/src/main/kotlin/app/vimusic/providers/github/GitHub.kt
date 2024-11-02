package app.vimusic.providers.github

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val API_VERSION = "2022-11-28"
private const val CONTENT_TYPE = "application"
private const val CONTENT_SUBTYPE = "vnd.github+json"

object GitHub {
    internal val httpClient by lazy {
        HttpClient(CIO) {
            val contentType = ContentType(CONTENT_TYPE, CONTENT_SUBTYPE)

            install(ContentNegotiation) {
                val json = Json {
                    ignoreUnknownKeys = true
                }

                json(json)
                json(
                    json = json,
                    contentType = contentType
                )
            }

            defaultRequest {
                url("https://api.github.com")
                headers["X-GitHub-Api-Version"] = API_VERSION

                accept(contentType)
                contentType(ContentType.Application.Json)
            }

            expectSuccess = true
        }
    }

    fun HttpRequestBuilder.withPagination(size: Int, page: Int) {
        require(page > 0) { "GitHub error: invalid page ($page), pagination starts at page 1" }
        require(size > 0) { "GitHub error: invalid page size ($size), a page has to have at least a single item" }

        parameter("per_page", size)
        parameter("page", page)
    }
}
