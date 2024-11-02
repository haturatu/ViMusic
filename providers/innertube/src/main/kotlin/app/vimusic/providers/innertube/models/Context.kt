package app.vimusic.providers.innertube.models

import io.ktor.client.request.headers
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.parameters
import io.ktor.http.userAgent
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.Locale

@Serializable
data class Context(
    val client: Client,
    val thirdParty: ThirdParty? = null,
    val user: User? = User()
) {
    @Serializable
    data class Client(
        val clientName: String,
        val clientVersion: String,
        val platform: String? = null,
        val hl: String = "en",
        val gl: String = "US",
        val visitorData: String = DEFAULT_VISITOR_DATA,
        val androidSdkVersion: Int? = null,
        val userAgent: String? = null,
        val referer: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val acceptHeader: String? = null,
        val timeZone: String? = "UTC",
        val utcOffsetMinutes: Int? = 0,
        @Transient
        val apiKey: String? = null
    )

    @Serializable
    data class ThirdParty(
        val embedUrl: String
    )

    @Serializable
    data class User(
        val lockedSafetyMode: Boolean = false
    )

    context(HttpMessageBuilder)
    fun apply() {
        client.userAgent?.let { userAgent(it) }

        headers {
            client.referer?.let { append("Referer", it) }
            append("X-Youtube-Bootstrap-Logged-In", "false")
            append("X-YouTube-Client-Name", client.clientName)
            append("X-YouTube-Client-Version", client.clientVersion)
            client.apiKey?.let { append("X-Goog-Api-Key", it) }
        }

        parameters {
            client.apiKey?.let { append("key", it) }
        }
    }

    companion object {
        private val Context.withLang: Context get() {
            val locale = Locale.getDefault()

            return copy(
                client = client.copy(
                    hl = locale
                        .toLanguageTag()
                        .replace("-Hant", "")
                        .takeIf { it in validLanguageCodes } ?: "en",
                    gl = locale
                        .country
                        .takeIf { it in validCountryCodes } ?: "US"
                )
            )
        }
        const val DEFAULT_VISITOR_DATA = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"

        val DefaultWeb get() = DefaultWebNoLang.withLang

        val DefaultWebNoLang = Context(
            client = Client(
                clientName = "WEB_REMIX",
                clientVersion = "1.20220606.03.00",
                platform = "DESKTOP",
                userAgent = UserAgents.DESKTOP,
                referer = "https://music.youtube.com/"
            )
        )

        val DefaultWebOld = Context(
            client = Client(
                clientName = "WEB",
                clientVersion = "2.20240509.00.00",
                platform = "DESKTOP",
                userAgent = UserAgents.DESKTOP,
                referer = "https://music.youtube.com/",
                apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
            )
        )

        val DefaultIOS = Context(
            client = Client(
                clientName = "IOS",
                clientVersion = "19.29.1",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osName = "iOS",
                osVersion = "17.5.1.21F90",
                acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                userAgent = UserAgents.IOS,
                apiKey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc"
            )
        )

        val DefaultAndroid = Context(
            client = Client(
                clientName = "ANDROID",
                clientVersion = "17.36.4",
                platform = "MOBILE",
                androidSdkVersion = 30,
                userAgent = UserAgents.ANDROID,
                apiKey = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"
            )
        )

        val DefaultAndroidMusic = Context(
            client = Client(
                clientName = "ANDROID_MUSIC",
                clientVersion = "5.22.1",
                platform = "MOBILE",
                androidSdkVersion = 30,
                userAgent = UserAgents.ANDROID_MUSIC,
                apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI"
            )
        )

        val DefaultAgeRestrictionBypass = Context(
            client = Client(
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                platform = "TV",
                userAgent = UserAgents.PLAYSTATION
            )
        )
    }
}

// @formatter:off
@Suppress("MaximumLineLength")
val validLanguageCodes =
    listOf("af", "az", "id", "ms", "ca", "cs", "da", "de", "et", "en-GB", "en", "es", "es-419", "eu", "fil", "fr", "fr-CA", "gl", "hr", "zu", "is", "it", "sw", "lt", "hu", "nl", "nl-NL", "no", "or", "uz", "pl", "pt-PT", "pt", "ro", "sq", "sk", "sl", "fi", "sv", "bo", "vi", "tr", "bg", "ky", "kk", "mk", "mn", "ru", "sr", "uk", "el", "hy", "iw", "ur", "ar", "fa", "ne", "mr", "hi", "bn", "pa", "gu", "ta", "te", "kn", "ml", "si", "th", "lo", "my", "ka", "am", "km", "zh-CN", "zh-TW", "zh-HK", "ja", "ko")

@Suppress("MaximumLineLength")
val validCountryCodes =
    listOf("DZ", "AR", "AU", "AT", "AZ", "BH", "BD", "BY", "BE", "BO", "BA", "BR", "BG", "KH", "CA", "CL", "HK", "CO", "CR", "HR", "CY", "CZ", "DK", "DO", "EC", "EG", "SV", "EE", "FI", "FR", "GE", "DE", "GH", "GR", "GT", "HN", "HU", "IS", "IN", "ID", "IQ", "IE", "IL", "IT", "JM", "JP", "JO", "KZ", "KE", "KR", "KW", "LA", "LV", "LB", "LY", "LI", "LT", "LU", "MK", "MY", "MT", "MX", "ME", "MA", "NP", "NL", "NZ", "NI", "NG", "NO", "OM", "PK", "PA", "PG", "PY", "PE", "PH", "PL", "PT", "PR", "QA", "RO", "RU", "SA", "SN", "RS", "SG", "SK", "SI", "ZA", "ES", "LK", "SE", "CH", "TW", "TZ", "TH", "TN", "TR", "UG", "UA", "AE", "GB", "US", "UY", "VE", "VN", "YE", "ZW")
// @formatter:on

@Suppress("MaximumLineLength")
object UserAgents {
    const val DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36"
    const val ANDROID = "com.google.android.youtube/17.36.4 (Linux; U; Android 11) gzip"
    const val ANDROID_MUSIC = "com.google.android.youtube/19.29.1  (Linux; U; Android 11) gzip"
    const val PLAYSTATION = "Mozilla/5.0 (PlayStation 4 5.55) AppleWebKit/601.2 (KHTML, like Gecko)"
    const val IOS = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)"
}
