package app.vimusic.providers.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.nio.charset.Charset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface ProviderHttpTransport {
    suspend fun execute(client: OkHttpClient, request: Request): ProviderHttpResponse
}

class ProviderHttpResponse(
    val status: Int,
    val headers: Headers,
    val body: ByteArray,
    val url: HttpUrl,
) {
    fun bodyAsText(defaultCharset: Charset = Charsets.UTF_8): String {
        val charset = headers["Content-Type"]?.toMediaTypeOrNull()?.charset(defaultCharset) ?: defaultCharset
        return body.toString(charset).removePrefix("\uFEFF")
    }
}

object OkHttpProviderTransport : ProviderHttpTransport {
    override suspend fun execute(client: OkHttpClient, request: Request): ProviderHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        // Materialize and close before resuming, including a cancellation race.
                        val result = response.use {
                            ProviderHttpResponse(it.code, it.headers, it.body.bytes(), it.request.url)
                        }
                        continuation.resume(result)
                    } catch (error: IOException) {
                        continuation.resumeWithException(error)
                    }
                }
            })
        }
}
