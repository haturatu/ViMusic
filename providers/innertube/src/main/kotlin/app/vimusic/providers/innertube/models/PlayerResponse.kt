package app.vimusic.providers.innertube.models

import app.vimusic.providers.innertube.Innertube
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus?,
    val playerConfig: PlayerConfig?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?,
    @Transient
    val cpn: String? = null
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String?
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig?
    ) {
        @Serializable
        data class AudioConfig(
            internal val loudnessDb: Double?
        ) {
            // For music clients only
            val normalizedLoudnessDb: Float?
                get() = loudnessDb?.plus(7)?.toFloat()
        }
    }

    @Serializable
    data class StreamingData(
        val adaptiveFormats: List<AdaptiveFormat>?,
        val expiresInSeconds: Long?
    ) {
        val highestQualityFormat: AdaptiveFormat?
            get() = adaptiveFormats?.filter { it.url != null || it.signatureCipher != null }?.let { formats ->
                formats.findLast { it.itag == 251 || it.itag == 140 }
                    ?: formats.maxBy { it.bitrate ?: 0L }
            }

        @Serializable
        data class AdaptiveFormat(
            val itag: Int,
            val mimeType: String,
            val bitrate: Long?,
            val averageBitrate: Long?,
            val contentLength: Long?,
            val audioQuality: String?,
            val approxDurationMs: Long?,
            val lastModified: Long?,
            val loudnessDb: Double?,
            val audioSampleRate: Int?,
            val url: String?,
            val signatureCipher: String?
        ) {
            suspend fun findUrl() = url ?: signatureCipher?.let { Innertube.decodeSignatureCipher(it) }
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String?
    )
}
