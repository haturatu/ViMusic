package app.vimusic.providers.newpipe.requests

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.Context
import app.vimusic.providers.newpipe.models.PlayerResponse
import app.vimusic.providers.newpipe.models.bodies.PlayerBody
import app.vimusic.providers.utils.runCatchingCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.security.SecureRandom

private val secureRandom = SecureRandom()
private fun nonce(size: Int): String = ByteArray(size).also(secureRandom::nextBytes).joinToString("") { "%02x".format(it) }

private suspend fun NewPipeMusic.tryContexts(
    body: PlayerBody,
    music: Boolean,
    vararg contexts: Context
): PlayerResponse? {
    contexts.forEach { context ->
        if (!currentCoroutineContext().isActive) return null

        logger.info("Trying ${context.client.clientName} ${context.client.clientVersion} ${context.client.platform}")
        val cpn =
            if (context.client.clientName == "IOS") nonce(16) else null
        runCatchingCancellable {
            client.post<PlayerResponse>(
                if (music) PLAYER_MUSIC else PLAYER,
                body.copy(context = context, cpn = cpn),
                parameters = if (cpn == null) emptyMap() else mapOf(
                    "t" to nonce(12),
                    "id" to body.videoId,
                ),
                context = context,
                extraHeaders = if (cpn == null) emptyMap() else mapOf("X-Goog-Api-Format-Version" to "2"),
            ).also { logger.info("Got $it") }
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

suspend fun NewPipeMusic.player(body: PlayerBody): Result<PlayerResponse?>? = runCatchingCancellable {
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
    } ?: client.post<PlayerResponse>(
        PLAYER,
        body.copy(
            context = Context.DefaultAgeRestrictionBypass.copy(
                thirdParty = Context.ThirdParty(embedUrl = "https://www.youtube.com/watch?v=${body.videoId}")
            ),
            playbackContext = PlayerBody.PlaybackContext(
                contentPlaybackContext = PlayerBody.PlaybackContext.ContentPlaybackContext(
                    signatureTimestamp = getSignatureTimestamp()
                )
            )
        ),
        context = Context.DefaultAgeRestrictionBypass,
    ).takeIf { it.isValid } ?: tryContexts(
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
