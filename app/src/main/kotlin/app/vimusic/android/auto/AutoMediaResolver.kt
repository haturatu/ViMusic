package app.vimusic.android.auto

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import app.vimusic.providers.youtubemusic.innertube.models.NavigationEndpoint

/** Resolution returns data only. Media3 owns applying the returned queue and start position. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoMediaResolver(
    private val tree: AutoLibraryTree,
    private val resumption: AutoPlaybackResumption,
    private val radio: suspend (NavigationEndpoint.Endpoint.Watch?) -> List<MediaItem>,
) {
    suspend fun add(items: List<MediaItem>): List<MediaItem> = items.flatMap { item ->
        val parsed = AutoMediaId.parse(item.mediaId)
        when {
            !item.requestMetadata.searchQuery.isNullOrBlank() -> voice(item.requestMetadata.searchQuery!!)
            parsed == null -> listOf(AutoPlaybackResumption.restoreItem(item))
            parsed.kind == "track" -> listOf(tree.canonical(item))
            parsed.kind == "radio" -> radio(requireNotNull(tree.radioEndpoint(parsed.key)))
            parsed.kind == "shuffle" -> tree.collection(parsed.key).shuffled()
            parsed.kind == "play-all" -> tree.collection(parsed.key)
            else -> tree.collection(item.mediaId)
        }
    }

    suspend fun set(items: List<MediaItem>, startIndex: Int, position: Long): MediaSession.MediaItemsWithStartPosition {
        require(items.isNotEmpty())
        val selectedIndex = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        require(selectedIndex in items.indices)
        if (items.size != 1) {
            val resolved = items.map { add(listOf(it)).single() }
            return MediaSession.MediaItemsWithStartPosition(resolved, selectedIndex, position)
        }
        val item = items.single()
        val id = AutoMediaId.parse(item.mediaId)
        if (id?.kind == "track" && id.parent != null) {
            val queue = tree.collection(id.parent)
            val index = queue.indexOfFirst { it.mediaId == id.key }
            require(index >= 0) { "Selected song was removed" }
            return MediaSession.MediaItemsWithStartPosition(queue, index, position)
        }
        if ((id?.kind == "play-all" && AutoMediaId.parse(id.key)?.kind == "resume") || id?.kind == "resume") {
            return resumption.resolve(resumption.load())
        }
        val queue = add(items)
        require(queue.isNotEmpty()) { "No playable songs" }
        return MediaSession.MediaItemsWithStartPosition(queue, 0, position)
    }

    private suspend fun voice(query: String): List<MediaItem> {
        val results = tree.search.search(query, tree)
        val tracks = results.filter { it.mediaMetadata.isPlayable == true }.map { tree.canonical(it) }
        if (tracks.isNotEmpty()) return tracks
        val collection = results.firstOrNull { it.mediaMetadata.isBrowsable == true } ?: return emptyList()
        return if (AutoMediaId.parse(collection.mediaId)?.kind == "artist") {
            radio(requireNotNull(tree.radioEndpoint(collection.mediaId)))
        } else tree.collection(collection.mediaId)
    }
}
