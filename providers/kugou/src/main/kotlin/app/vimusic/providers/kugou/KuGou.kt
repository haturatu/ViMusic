package app.vimusic.providers.kugou

import app.vimusic.providers.kugou.models.DownloadLyricsResponse
import app.vimusic.providers.kugou.models.SearchLyricsResponse
import app.vimusic.providers.kugou.models.SearchSongResponse
import app.vimusic.providers.utils.runCatchingCancellable
import app.vimusic.providers.utils.ProviderHttpClient
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64

object KuGou {
    @OptIn(ExperimentalSerializationApi::class)
    private val client by lazy {
        ProviderHttpClient(
            baseUrl = "https://krcs.kugou.com",
            json = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            },
            headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"),
            client = ProviderHttpClient.defaultClient.newBuilder()
                .connectTimeout(500, TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    suspend fun lyrics(artist: String, title: String, duration: Long) = runCatchingCancellable {
        val keyword = keyword(artist, title)
        val infoByKeyword = searchSong(keyword)

        if (infoByKeyword.isNotEmpty()) {
            var tolerance = 0

            while (tolerance <= 5) {
                for (info in infoByKeyword) {
                    if (info.duration >= duration - tolerance && info.duration <= duration + tolerance) {
                        searchLyricsByHash(info.hash).firstOrNull()?.let { candidate ->
                            return@runCatchingCancellable downloadLyrics(
                                candidate.id,
                                candidate.accessKey
                            ).normalize()
                        }
                    }
                }

                tolerance++
            }
        }

        searchLyricsByKeyword(keyword).firstOrNull()?.let { candidate ->
            return@runCatchingCancellable downloadLyrics(
                candidate.id,
                candidate.accessKey
            ).normalize()
        }

        null
    }

    private suspend fun downloadLyrics(id: Long, accessKey: String) = client.getJson<DownloadLyricsResponse>("/download") {
        addQueryParameter("ver", "1")
        addQueryParameter("man", "yes")
        addQueryParameter("client", "pc")
        addQueryParameter("fmt", "lrc")
        addQueryParameter("id", id.toString())
        addQueryParameter("accesskey", accessKey)
    }.content.let { Base64.Mime.decode(it).decodeToString() }.let(KuGou::Lyrics)

    private suspend fun searchLyricsByHash(hash: String) = client.getJson<SearchLyricsResponse>("/search") {
        addQueryParameter("ver", "1")
        addQueryParameter("man", "yes")
        addQueryParameter("client", "mobi")
        addQueryParameter("hash", hash)
    }.candidates

    private suspend fun searchLyricsByKeyword(keyword: String) = client.getJson<SearchLyricsResponse>("/search") {
        addQueryParameter("ver", "1")
        addQueryParameter("man", "yes")
        addQueryParameter("client", "mobi")
        addQueryParameter("keyword", keyword)
    }.candidates

    private suspend fun searchSong(keyword: String) =
        client.getJson<SearchSongResponse>("https://mobileservice.kugou.com/api/v3/search/song") {
            addQueryParameter("version", "9108")
            addQueryParameter("plat", "0")
            addQueryParameter("pagesize", "8")
            addQueryParameter("showtype", "0")
            addQueryParameter("keyword", keyword)
        }.data.info

    private fun keyword(artist: String, title: String): String {
        val (newTitle, featuring) = title.extract(" (feat. ", ')')

        val newArtist = (if (featuring.isEmpty()) artist else "$artist, $featuring")
            .replace(", ", "、")
            .replace(" & ", "、")
            .replace(".", "")

        return "$newArtist - $newTitle"
    }

    private fun String.extract(startDelimiter: String, endDelimiter: Char): Pair<String, String> {
        val startIndex = indexOf(startDelimiter).takeIf { it != -1 } ?: return this to ""
        val endIndex = indexOf(endDelimiter, startIndex).takeIf { it != -1 } ?: return this to ""

        return removeRange(startIndex, endIndex + 1) to substring(startIndex + startDelimiter.length, endIndex)
    }

    @JvmInline
    value class Lyrics(val value: String) {
        @Suppress("CyclomaticComplexMethod")
        fun normalize(): Lyrics {
            var toDrop = 0
            var maybeToDrop = 0

            val text = value.replace("\r\n", "\n").trim()

            for (line in text.lineSequence()) when {
                line.startsWith("[ti:") ||
                        line.startsWith("[ar:") ||
                        line.startsWith("[al:") ||
                        line.startsWith("[by:") ||
                        line.startsWith("[hash:") ||
                        line.startsWith("[sign:") ||
                        line.startsWith("[qq:") ||
                        line.startsWith("[total:") ||
                        line.startsWith("[offset:") ||
                        line.startsWith("[id:") ||
                        line.containsAt("]Written by：", 9) ||
                        line.containsAt("]Lyrics by：", 9) ||
                        line.containsAt("]Composed by：", 9) ||
                        line.containsAt("]Producer：", 9) ||
                        line.containsAt("]作曲 : ", 9) ||
                        line.containsAt("]作词 : ", 9) -> {
                    toDrop += line.length + 1 + maybeToDrop
                    maybeToDrop = 0
                }

                maybeToDrop == 0 -> maybeToDrop = line.length + 1

                else -> {
                    maybeToDrop = 0
                    break
                }
            }

            return Lyrics(text.drop(toDrop + maybeToDrop).removeHtmlEntities())
        }

        private fun String.containsAt(charSequence: CharSequence, startIndex: Int) =
            regionMatches(startIndex, charSequence, 0, charSequence.length)

        private fun String.removeHtmlEntities() = replace("&apos;", "'")
    }
}
