package app.vimusic.providers.innertube.utils

import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.SectionListRenderer

private val SectionListRenderer.Content.title: String? get() {
    val title = musicCarouselShelfRenderer
        ?.header
        ?.musicCarouselShelfBasicHeaderRenderer
        ?.title
        ?: musicShelfRenderer
            ?.title

    return title?.runs?.firstOrNull()?.text
}

private val SectionListRenderer.Content.strapline get() = musicCarouselShelfRenderer
    ?.header
    ?.musicCarouselShelfBasicHeaderRenderer
    ?.strapline
    ?.runs
    ?.firstOrNull()
    ?.text

private fun normalizeTitle(text: String?) = text?.trim()?.lowercase()

internal fun SectionListRenderer.findSectionByTitle(text: String) = contents
    ?.find { normalizeTitle(it.title) == normalizeTitle(text) }
    ?: contents?.find { normalizeTitle(it.title)?.contains(normalizeTitle(text) ?: "") == true }

internal fun SectionListRenderer.findSectionByTitleAny(vararg texts: String) = contents
    ?.find { content ->
        val title = normalizeTitle(content.title)
        title != null && texts.any { text ->
            val target = normalizeTitle(text)
            target != null && (title == target || title.contains(target))
        }
    }

internal fun SectionListRenderer.findSectionByStrapline(text: String) = contents?.find { it.strapline == text }

infix operator fun <T : Innertube.Item> Innertube.ItemsPage<T>?.plus(other: Innertube.ItemsPage<T>) =
    other.copy(
        items = (this?.items?.plus(other.items ?: emptyList()) ?: other.items)
            ?.distinctBy(Innertube.Item::key),
        continuation = other.continuation
            ?.takeIf { it.isNotBlank() }
    )
