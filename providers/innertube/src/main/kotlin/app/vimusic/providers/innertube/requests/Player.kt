package app.vimusic.providers.innertube.requests

import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.Context
import app.vimusic.providers.innertube.models.PlayerResponse
import app.vimusic.providers.innertube.models.bodies.PlayerBody
import app.vimusic.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.util.generateNonce
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

private suspend fun Innertube.tryContexts(
    body: PlayerBody,
    music: Boolean,
    vararg contexts: Context
): PlayerResponse? {
    contexts.forEach { context ->
        if (!currentCoroutineContext().isActive) return null

        logger.info("Trying ${context.client.clientName} ${context.client.clientVersion} ${context.client.platform}")
        val cpn =
            if (context.client.clientName == "IOS") generateNonce(16).decodeToString() else null
        runCatchingCancellable {
            client.post(if (music) PLAYER_MUSIC else PLAYER) {
                setBody(
                    body.copy(
                        context = context,
                        cpn = cpn
                    )
                )

                context.apply()

                if (cpn != null) {
                    parameter("t", generateNonce(12))
                    header("X-Goog-Api-Format-Version", "2")
                    parameter("id", body.videoId)
                }
            }.body<PlayerResponse>().also { logger.info("Got $it") }
        }
            ?.getOrNull()
            ?.takeIf { it.isValid }
            ?.let { return it.copy(cpn = cpn) }
    }

    return null
}

private val PlayerResponse.isValid
    get() = playabilityStatus?.status == "OK" &&
            streamingData?.adaptiveFormats?.any { it.url != null || it.signatureCipher != null } == true

suspend fun Innertube.player(body: PlayerBody): Result<PlayerResponse?>? = runCatchingCancellable {
    tryContexts(
        body = body,
        music = false,
        Context.DefaultIOS
    )?.let { response ->
        if (response.playerConfig?.audioConfig?.loudnessDb == null) {
            // On non-music clients, the loudness doesn't get accounted for, resulting in really bland audio
            // Try to recover from this or gracefully accept the user's ears' fate
            tryContexts(
                body = body,
                music = true,
                Context.DefaultWebNoLang
            )?.playerConfig?.let {
                response.copy(playerConfig = it)
            } ?: response
        } else response
    } ?: client.post(PLAYER) {
        setBody(
            body.copy(
                context = Context.DefaultAgeRestrictionBypass.copy(
                    thirdParty = Context.ThirdParty(
                        embedUrl = "https://www.youtube.com/watch?v=${body.videoId}"
                    )
                ),
                playbackContext = PlayerBody.PlaybackContext(
                    contentPlaybackContext = PlayerBody.PlaybackContext.ContentPlaybackContext(
                        signatureTimestamp = getSignatureTimestamp()
                    )
                )
            )
        )
    }.body<PlayerResponse>().takeIf { it.isValid } ?: tryContexts(
        body = body,
        music = false,
        Context.DefaultWebOld,
        Context.DefaultAndroid
    ) ?: tryContexts(
        body = body,
        music = true,
        Context.DefaultWeb,
        Context.DefaultAndroidMusic
    )
}
