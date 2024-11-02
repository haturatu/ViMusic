package app.vimusic.providers.translate.requests

import app.vimusic.providers.translate.Translate
import app.vimusic.providers.translate.models.Language
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.charsets.name
import java.nio.charset.Charset

suspend fun Translate.translate(
    text: String,
    from: Language = Language.Auto,
    to: Language,
    host: String = "translate.googleapis.com",
    charset: Charset = Charsets.UTF_8
): String {
    require(to != Language.Auto) { "The target language cannot be Auto" }
    val encoding = charset.name.replace("_", "-")

    return client.get("https://$host/translate_a/single") {
        dt.forEach { parameter("dt", it) }
        parameter("client", "gtx")
        parameter("ie", encoding)
        parameter("oe", encoding)
        parameter("otf", 1)
        parameter("ssel", 0)
        parameter("tsel", 0)
        parameter("sl", from.code)
        parameter("tl", to.code)
        parameter("hl", to.code)
        parameter("q", text)
    }.bodyAsText(charset)
}
