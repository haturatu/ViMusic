package app.vimusic.android.extractor

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

class NewPipeDownloader(
    private val client: OkHttpClient
) : Downloader() {
    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody(null)
        val httpRequest = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Connection", "close")
            .addHeader("Cache-Control", "no-cache")
            .apply {
                request.headers().forEach { (name, values) ->
                    removeHeader(name)
                    values.forEach { value -> addHeader(name, value) }
                }
            }
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }

            val responseBody = response.body.string()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                response.request.url.toString()
            )
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
