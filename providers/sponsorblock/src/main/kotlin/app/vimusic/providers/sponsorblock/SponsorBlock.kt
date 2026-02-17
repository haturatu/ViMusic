package app.vimusic.providers.sponsorblock

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.brotli
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.IOException

object SponsorBlock {
    internal val httpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }

            defaultRequest {
                url("https://sponsor.ajay.app")

                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                header("Connection", "close")
                header("Cache-Control", "no-cache")
            }

            install(HttpRequestRetry) {
                retryOnExceptionIf { _, cause -> cause is IOException }
                retryOnServerErrors()
                constantDelay()
                maxRetries = 3
                modifyRequest {
                    it.headers.remove("Connection")
                    it.headers.remove("Cache-Control")
                    it.headers.append("Connection", "close")
                    it.headers.append("Cache-Control", "no-cache")
                }
            }

            install(ContentEncoding) {
                brotli()
                gzip()
                deflate()
            }

            expectSuccess = true
        }
    }
}
