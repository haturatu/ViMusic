package app.vimusic.android.auto

import android.net.Uri

/** IDs describe their collection as well as the song; browsing another list cannot change selection. */
data class AutoMediaId(
    val kind: String,
    val key: String = "",
    val parent: String? = null,
    val offset: Int = 0,
    val length: Int = Int.MAX_VALUE,
) {
    val encoded: String get() = Uri.Builder().scheme("vimusic").authority("auto")
        .appendPath(kind).appendPath(key)
        .apply {
            parent?.let { appendQueryParameter("parent", it) }
            if (offset != 0) appendQueryParameter("offset", offset.toString())
            if (length != Int.MAX_VALUE) appendQueryParameter("length", length.toString())
        }.build().toString()

    companion object {
        val ROOT = AutoMediaId("root").encoded
        val HOME = AutoMediaId("home").encoded
        val LIBRARY = AutoMediaId("library").encoded
        val DISCOVER = AutoMediaId("discover").encoded
        val OFFLINE = AutoMediaId("offline").encoded

        fun parse(value: String): AutoMediaId? {
            val uri = Uri.parse(value)
            if (uri.scheme != "vimusic" || uri.authority != "auto") return null
            val paths = uri.pathSegments
            return AutoMediaId(
                kind = paths.firstOrNull() ?: return null,
                key = paths.getOrNull(1).orEmpty(),
                parent = uri.getQueryParameter("parent"),
                offset = uri.getQueryParameter("offset")?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                length = uri.getQueryParameter("length")?.toIntOrNull()?.coerceAtLeast(1) ?: Int.MAX_VALUE,
            )
        }
    }
}
