package app.vimusic.android.extractor

import dev.kathttp3.KatHttp3Client
import dev.kathttp3.KatHttp3ClientConfig
import dev.kathttp3.KatHttp3Header
import dev.kathttp3.KatHttp3Request
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * NewPipe [Downloader] backed by kathttp3's native HTTP/3 transport.
 *
 * kathttp3 only accepts HTTPS endpoints that negotiate HTTP/3. Requests to servers without HTTP/3,
 * or transport failures, are delegated to [fallback] so extraction remains available.
 */
class KatHttp3Downloader(
    private val fallback: Downloader,
    applicationContext: android.content.Context? = null,
) : Downloader() {
    private val client = KatHttp3Client(
        config = KatHttp3ClientConfig(
            enable0Rtt = true,
        ),
        applicationContext = applicationContext,
    )

    override fun execute(request: Request): Response = try {
        runBlocking {
            client.execute(
                KatHttp3Request(
                    method = request.httpMethod(),
                    url = request.url(),
                    headers = requestHeaders(request),
                    body = request.dataToSend(),
                )
            )
        }.toNewPipeResponse(request.url())
    } catch (error: ReCaptchaException) {
        throw error
    } catch (error: Exception) {
        fallback.execute(request)
    }

    private fun requestHeaders(request: Request): List<KatHttp3Header> {
        val headers = linkedMapOf(
            "User-Agent" to mutableListOf(NewPipeDownloader.USER_AGENT),
            "Connection" to mutableListOf("close"),
            "Cache-Control" to mutableListOf("no-cache"),
        )
        request.headers().forEach { (name, values) -> headers[name] = values.toMutableList() }
        return headers.flatMap { (name, values) -> values.map { KatHttp3Header(name, it) } }
    }

    private fun dev.kathttp3.KatHttp3Response.toNewPipeResponse(requestUrl: String): Response {
        if (status == 429) throw ReCaptchaException("reCaptcha Challenge requested", requestUrl)
        return Response(
            status,
            "",
            headers.groupBy(KatHttp3Header::name, KatHttp3Header::value),
            body.decodeToString(),
            requestUrl,
        )
    }
}
