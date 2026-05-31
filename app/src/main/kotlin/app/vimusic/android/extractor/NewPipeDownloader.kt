package app.vimusic.android.extractor

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

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

        fun client(dnsTarget: NewPipeDnsTarget) = OkHttpClient.Builder()
            .retryOnConnectionFailure(dnsTarget == NewPipeDnsTarget.System)
            .connectTimeout(if (dnsTarget == NewPipeDnsTarget.System) 10 else 4, TimeUnit.SECONDS)
            .readTimeout(if (dnsTarget == NewPipeDnsTarget.System) 10 else 8, TimeUnit.SECONDS)
            .dns { hostname ->
                when (dnsTarget) {
                    NewPipeDnsTarget.System -> InetAddress.getAllByName(hostname).toList()
                    is NewPipeDnsTarget.Resolved -> resolveAddresses(hostname, dnsTarget.index)
                }
            }
            .build()

        private fun resolveAddresses(hostname: String, index: Int): List<InetAddress> {
            val addresses = InetAddress.getAllByName(hostname).toList()
            val ordered = addresses
                .filterIsInstance<Inet4Address>()

            val selected = ordered.getOrNull(index) ?: return ordered.also {
                Log.d(TAG, "Using IPv4 DNS hostname=$hostname index=$index addresses=${ordered.size}")
            }

            return listOf(selected.also {
                Log.d(TAG, "Resolved DNS hostname=$hostname index=$index address=${it.hostAddress}")
            })
        }

        private const val TAG = "NewPipeDownloader"
    }
}

sealed interface NewPipeDnsTarget {
    val label: String

    data object System : NewPipeDnsTarget {
        override val label = "system"
    }

    data class Resolved(
        val index: Int
    ) : NewPipeDnsTarget {
        override val label = "resolved[$index]"
    }
}
