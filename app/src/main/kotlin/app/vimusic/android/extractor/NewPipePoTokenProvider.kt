package app.vimusic.android.extractor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object NewPipePoTokenProvider : PoTokenProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val appContext = AtomicReference<Context?>()
    private val generatorLock = Any()
    private var generator: PoTokenGenerator? = null
    private val clientTokens = mutableMapOf<PoTokenClient, ClientTokenState>()
    private var retryAfterMs = 0L

    fun initialize(context: Context) {
        appContext.set(context.applicationContext)
    }

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        Log.d(TAG, "getWebClientPoToken requested videoId=$videoId")
        return getPoToken(videoId, PoTokenClient.Web)
    }

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? {
        Log.d(TAG, "getWebEmbedClientPoToken requested videoId=$videoId; returning null like NewPipe")
        return null
    }

    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? {
        Log.d(TAG, "getAndroidClientPoToken requested videoId=$videoId")
        return getPoToken(videoId, PoTokenClient.Android)
    }

    override fun getIosClientPoToken(videoId: String): PoTokenResult? {
        Log.d(TAG, "getIosClientPoToken requested videoId=$videoId; returning null like NewPipe")
        return null
    }

    private fun getPoToken(videoId: String, client: PoTokenClient): PoTokenResult? {
        synchronized(generatorLock) {
            if (System.currentTimeMillis() < retryAfterMs) return null
        }

        return runCatching {
            generatePoToken(videoId, client, forceRecreate = false)
        }.recoverCatching { error ->
            Log.w(TAG, "Failed to create ${client.label} poToken; retrying with a new WebView", error)
            generatePoToken(videoId, client, forceRecreate = true)
        }.onFailure { error ->
            synchronized(generatorLock) {
                retryAfterMs = System.currentTimeMillis() + RETRY_COOLDOWN_MILLIS
            }
            Log.w(TAG, "${client.label} poToken unavailable; retrying after cooldown", error)
        }.getOrNull()
    }

    private fun generatePoToken(
        videoId: String,
        client: PoTokenClient,
        forceRecreate: Boolean,
    ): PoTokenResult {
        val currentContext = requireNotNull(appContext.get()) {
            "NewPipePoTokenProvider is not initialized"
        }

        val activeGenerator: PoTokenGenerator
        val activeTokens: ClientTokenState

        synchronized(generatorLock) {
            if (forceRecreate || generator == null || generator?.isExpired() == true) {
                generator?.close()
                generator = WebViewPoTokenGenerator.create(currentContext, this.client)
                clientTokens.clear()
            }

            activeGenerator = generator!!
            activeTokens = clientTokens.getOrPut(client) {
                val requestInfo = client.requestInfo().also {
                    if (client == PoTokenClient.Web) {
                        it.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
                    }
                }
                val visitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                    requestInfo,
                    NewPipe.getPreferredLocalization(),
                    NewPipe.getPreferredContentCountry(),
                    YoutubeParsingHelper.getYouTubeHeaders(),
                    YoutubeParsingHelper.YOUTUBEI_V1_URL,
                    null,
                    false
                )
                ClientTokenState(
                    visitorData = visitorData,
                    streamingPoToken = if (client.requiresStreamingToken) {
                        activeGenerator.generatePoToken(visitorData)
                    } else {
                        null
                    },
                )
            }
            retryAfterMs = 0L
        }

        return PoTokenResult(
            activeTokens.visitorData,
            activeGenerator.generatePoToken(videoId),
            activeTokens.streamingPoToken,
        )
    }

    private enum class PoTokenClient(
        val label: String,
        val requiresStreamingToken: Boolean,
        val requestInfo: () -> InnertubeClientRequestInfo,
    ) {
        Web("Web", true, InnertubeClientRequestInfo::ofWebClient),
        Android("Android", false, InnertubeClientRequestInfo::ofAndroidClient),
    }

    private data class ClientTokenState(
        val visitorData: String,
        val streamingPoToken: String?,
    )

    private const val TAG = "NewPipePoTokenProvider"
    private const val RETRY_COOLDOWN_MILLIS = 30_000L
}

private interface PoTokenGenerator : Closeable {
    fun generatePoToken(identifier: String): String
    fun isExpired(): Boolean
}

private class WebViewPoTokenGenerator private constructor(
    context: Context,
    private val client: OkHttpClient
) : PoTokenGenerator {
    private val webView = WebView(context)
    private var expiresAtMs = 0L
    private val initLatch = CountDownLatch(1)
    private val initError = AtomicReference<Throwable?>()
    private val generationLock = Any()
    private val tokenLock = Any()
    private var tokenLatch: CountDownLatch? = null
    private var tokenResult: String? = null
    private var tokenError: Throwable? = null

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = USER_AGENT
        webView.settings.blockNetworkLoads = true
        webView.addJavascriptInterface(this, JS_INTERFACE)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                if (consoleMessage.message().contains("Uncaught")) {
                    initError.set(PoTokenException(consoleMessage.message()))
                    initLatch.countDown()
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }
    }

    fun initialize(context: Context) {
        val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL(
            "https://www.youtube.com",
            html.replaceFirst("</script>", "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"),
            "text/html",
            "utf-8",
            null
        )
    }

    @JavascriptInterface
    fun downloadAndRunBotguard() {
        runCatching {
            val responseBody = botguardPost(
                "https://www.youtube.com/api/jnn/v1/Create",
                "[ \"$REQUEST_KEY\" ]"
            )
            val challengeData = parseChallengeData(responseBody)
            runOnMain {
                webView.evaluateJavascript(
                    """try {
                        data = $challengeData
                        runBotGuard(data).then(function (result) {
                            this.webPoSignalOutput = result.webPoSignalOutput
                            $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                        }, function (error) {
                            $JS_INTERFACE.onInitializationError(error + "\n" + error.stack)
                        })
                    } catch (error) {
                        $JS_INTERFACE.onInitializationError(error + "\n" + error.stack)
                    }""",
                    null
                )
            }
        }.onFailure(::failInitialization)
    }

    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        runCatching {
            val responseBody = botguardPost(
                "https://www.youtube.com/api/jnn/v1/GenerateIT",
                "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]"
            )
            val (integrityToken, expiresInSeconds) = parseIntegrityTokenData(responseBody)
            expiresAtMs = System.currentTimeMillis() + (expiresInSeconds - 600).coerceAtLeast(60) * 1000
            runOnMain {
                webView.evaluateJavascript("this.integrityToken = $integrityToken") {
                    initLatch.countDown()
                }
            }
        }.onFailure(::failInitialization)
    }

    @JavascriptInterface
    fun onInitializationError(error: String) {
        failInitialization(PoTokenException(error))
    }

    @JavascriptInterface
    @Suppress("UnusedParameter") // Required by the JavaScript bridge callback signature.
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        synchronized(tokenLock) {
            tokenResult = u8ToBase64(poTokenU8)
            tokenLatch?.countDown()
        }
    }

    @JavascriptInterface
    @Suppress("UnusedParameter") // Required by the JavaScript bridge callback signature.
    fun onObtainPoTokenError(identifier: String, error: String) {
        synchronized(tokenLock) {
            tokenError = PoTokenException(error)
            tokenLatch?.countDown()
        }
    }

    override fun generatePoToken(identifier: String): String = synchronized(generationLock) {
        generatePoTokenLocked(identifier)
    }

    private fun generatePoTokenLocked(identifier: String): String {
        val latch = CountDownLatch(1)
        synchronized(tokenLock) {
            tokenLatch = latch
            tokenResult = null
            tokenError = null
        }

        runOnMain {
            val escapedIdentifier = JsonWriter.string(identifier)
            webView.evaluateJavascript(
                """try {
                    identifier = $escapedIdentifier
                    u8Identifier = ${stringToU8(identifier)}
                    poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                    poTokenU8String = ""
                    for (i = 0; i < poTokenU8.length; i++) {
                        if (i != 0) poTokenU8String += ","
                        poTokenU8String += poTokenU8[i]
                    }
                    $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                } catch (error) {
                    $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                }""",
                null
            )
        }

        if (!latch.await(TOKEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw PoTokenException("Timed out while generating poToken")
        }

        synchronized(tokenLock) {
            tokenError?.let { throw it }
            return tokenResult ?: throw PoTokenException("poToken result is empty")
        }
    }

    override fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAtMs

    override fun close() {
        runOnMain {
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.clearCache(true)
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    private fun botguardPost(url: String, body: String): String {
        val response = client.newCall(
            Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json+protobuf".toMediaType()))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("x-goog-api-key", GOOGLE_API_KEY)
                .header("x-user-agent", "grpc-web-javascript/0.1")
                .build()
        ).execute()

        response.use {
            if (!it.isSuccessful) {
                throw PoTokenException("BotGuard request failed: HTTP ${it.code}")
            }
            return it.body.string()
        }
    }

    private fun failInitialization(error: Throwable) {
        initError.set(error)
        initLatch.countDown()
    }

    companion object {
        fun create(context: Context, client: OkHttpClient): WebViewPoTokenGenerator {
            val ref = AtomicReference<WebViewPoTokenGenerator?>()
            runOnMain {
                ref.set(WebViewPoTokenGenerator(context, client).also { it.initialize(context) })
            }

            val generator = requireNotNull(ref.get()) {
                "Failed to create WebView poToken generator"
            }

            if (!generator.initLatch.await(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                generator.close()
                throw PoTokenException("Timed out while initializing poToken WebView")
            }

            generator.initError.get()?.let {
                generator.close()
                throw it
            }

            return generator
        }

        private fun runOnMain(block: () -> Unit) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                block()
            } else {
                val latch = CountDownLatch(1)
                val error = AtomicReference<Throwable?>()
                Handler(Looper.getMainLooper()).post {
                    runCatching(block)
                        .onFailure(error::set)
                    latch.countDown()
                }
                if (!latch.await(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw PoTokenException("Timed out waiting for main thread")
                }
                error.get()?.let { throw it }
            }
        }

        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"
        private const val INIT_TIMEOUT_SECONDS = 30L
        private const val TOKEN_TIMEOUT_SECONDS = 15L
        private const val MAIN_THREAD_TIMEOUT_SECONDS = 10L
    }
}

private class PoTokenException(message: String) : RuntimeException(message)

private fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = JsonParser.array().from(rawChallengeData)
    val challengeData = if (scrambled.size > 1 && scrambled.isString(1)) {
        JsonParser.array().from(descramble(scrambled.getString(1)))
    } else {
        scrambled.getArray(0)
    }

    return JsonWriter.string(
        JsonObject.builder()
            .value("messageId", challengeData.getString(0))
            .`object`("interpreterJavascript")
            .value(
                "privateDoNotAccessOrElseSafeScriptWrappedValue",
                challengeData.getArray(1, null)?.firstOrNull { it is String }
            )
            .value(
                "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue",
                challengeData.getArray(2, null)?.firstOrNull { it is String }
            )
            .end()
            .value("interpreterHash", challengeData.getString(3))
            .value("program", challengeData.getString(4))
            .value("globalName", challengeData.getString(5))
            .value("clientExperimentsStateBlob", challengeData.getString(7))
            .done()
    )
}

private fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val integrityTokenData = JsonParser.array().from(rawIntegrityTokenData)
    return base64ToU8(integrityTokenData.getString(0)) to integrityTokenData.getLong(1)
}

private fun stringToU8(identifier: String): String = newUint8Array(identifier.toByteArray())

private fun u8ToBase64(poToken: String): String =
    Base64.encodeToString(
        poToken.split(",").map { it.toInt().toByte() }.toByteArray(),
        Base64.NO_WRAP or Base64.URL_SAFE
    ).trimEnd('=')

private fun descramble(scrambledChallenge: String): String =
    base64ToByteArray(scrambledChallenge)
        .map { (it + 97).toByte() }
        .toByteArray()
        .decodeToString()

private fun base64ToU8(base64: String): String = newUint8Array(base64ToByteArray(base64))

private fun newUint8Array(contents: ByteArray): String =
    "new Uint8Array([" + contents.joinToString(separator = ",") { it.toUByte().toString() } + "])"

private fun base64ToByteArray(base64: String): ByteArray =
    Base64.decode(
        base64
            .replace('-', '+')
            .replace('_', '/')
            .replace('.', '='),
        Base64.DEFAULT
    )
