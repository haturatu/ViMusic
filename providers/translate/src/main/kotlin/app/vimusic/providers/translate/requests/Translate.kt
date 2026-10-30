package app.vimusic.providers.translate.requests

import app.vimusic.providers.translate.Translate
import app.vimusic.providers.translate.models.Language
import java.nio.charset.Charset

suspend fun Translate.translate(
    text: String,
    from: Language = Language.Auto,
    to: Language,
    host: String = "translate.googleapis.com",
    charset: Charset = Charsets.UTF_8
): String {
    require(to != Language.Auto) { "The target language cannot be Auto" }
    val encoding = charset.name().replace("_", "-")

    return client.get("https://$host/translate_a/single", charset = charset) {
        dt.forEach { addQueryParameter("dt", it) }
        addQueryParameter("client", "gtx")
        addQueryParameter("ie", encoding)
        addQueryParameter("oe", encoding)
        addQueryParameter("otf", "1")
        addQueryParameter("ssel", "0")
        addQueryParameter("tsel", "0")
        addQueryParameter("sl", from.code)
        addQueryParameter("tl", to.code)
        addQueryParameter("hl", to.code)
        addQueryParameter("q", text)
    }
}
