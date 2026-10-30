package app.vimusic.android.auto

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.vimusic.android.R
import app.vimusic.android.repositories.PlayerRepository
import app.vimusic.providers.youtubemusic.innertube.models.NavigationEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoCommandHandler(
    private val tree: AutoLibraryTree,
    private val resolver: AutoMediaResolver,
    private val queue: AutoQueueController,
    private val repository: PlayerRepository,
    private val startRadio: (NavigationEndpoint.Endpoint.Watch?) -> Unit,
) {
    suspend fun execute(action: String, args: Bundle): SessionResult {
        val id = args.getString(ARG_MEDIA_ID)
            ?: args.getString(androidx.media3.session.MediaConstants.EXTRA_KEY_MEDIA_ID)
            ?: args.getString("android.media.browse.extra.MEDIA_ID")
        when (action) {
            FAVORITE -> {
                val current = withContext(Dispatchers.Main.immediate) { queue.player.currentMediaItem }
                val item = id?.let { resolver.add(listOf(MediaItem.Builder().setMediaId(it).build())).firstOrNull() } ?: current
                requireNotNull(item)
                toggleFavorite(item)
            }
            NEXT, APPEND -> {
                requireNotNull(id)
                val items = resolver.add(listOf(MediaItem.Builder().setMediaId(id).build()))
                withContext(Dispatchers.Main.immediate) { if (action == NEXT) queue.playNext(items) else queue.append(items) }
            }
            RADIO -> {
                val current = withContext(Dispatchers.Main.immediate) { queue.player.currentMediaItem?.mediaId }
                val endpoint = withContext(Dispatchers.IO) { requireNotNull(tree.radioEndpoint(id ?: requireNotNull(current))) }
                withContext(Dispatchers.Main.immediate) { startRadio(endpoint) }
            }
            else -> return mutateQueue(action, args)
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    suspend fun toggleFavorite(item: MediaItem) = withContext(Dispatchers.IO) {
        repository.toggleFavorite(item)
        tree.invalidate()
    }

    private suspend fun mutateQueue(action: String, args: Bundle): SessionResult = withContext(Dispatchers.Main.immediate) {
        when (action) {
            REMOVE -> { validateQueue(args); queue.remove(args.getInt(ARG_INDEX, -1), args.getString(ARG_EXPECTED_ID)) }
            MOVE -> { validateQueue(args); queue.move(args.getInt(ARG_INDEX, -1), args.getInt(ARG_TO_INDEX, -1), args.getString(ARG_EXPECTED_ID)) }
            SHUFFLE -> queue.player.shuffleModeEnabled = !queue.player.shuffleModeEnabled
            REPEAT -> queue.player.repeatMode = (queue.player.repeatMode + 1) % 3
            else -> return@withContext SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
        }
        SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private fun validateQueue(args: Bundle) {
        args.getString(ARG_QUEUE_SIGNATURE)?.let { require(it == AutoQueueController.signature(queue.player)) }
    }

    companion object {
        const val SESSION_TOKEN = "app.vimusic.auto.SESSION_TOKEN"
        const val FAVORITE = "app.vimusic.auto.FAVORITE"
        const val NEXT = "app.vimusic.auto.NEXT"
        const val APPEND = "app.vimusic.auto.APPEND"
        const val RADIO = "app.vimusic.auto.RADIO"
        const val REMOVE = "app.vimusic.auto.REMOVE"
        const val MOVE = "app.vimusic.auto.MOVE"
        const val SHUFFLE = "app.vimusic.auto.SHUFFLE"
        const val REPEAT = "app.vimusic.auto.REPEAT"
        const val ARG_MEDIA_ID = "mediaId"
        const val ARG_INDEX = "index"
        const val ARG_TO_INDEX = "toIndex"
        const val ARG_QUEUE_SIGNATURE = "queueSignature"
        const val ARG_EXPECTED_ID = "expectedId"
        val ITEM_ACTIONS = listOf(NEXT, APPEND, RADIO, FAVORITE)
        val ALL_ACTIONS = ITEM_ACTIONS + listOf(REMOVE, MOVE, SHUFFLE, REPEAT)
        fun command(action: String) = SessionCommand(action, Bundle.EMPTY)
        fun buttons(context: Context, liked: Boolean = false): List<CommandButton> = listOf(
            Triple(FAVORITE, if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED, R.string.favorites),
            Triple(NEXT, CommandButton.ICON_QUEUE_NEXT, R.string.play_next),
            Triple(APPEND, CommandButton.ICON_QUEUE_ADD, R.string.auto_add_queue),
            Triple(RADIO, CommandButton.ICON_RADIO, R.string.auto_radio),
            Triple(SHUFFLE, CommandButton.ICON_SHUFFLE_ON, R.string.shuffle),
            Triple(REPEAT, CommandButton.ICON_REPEAT_ALL, R.string.auto_repeat),
        ).map { (action, icon, title) -> CommandButton.Builder(icon).setDisplayName(context.getString(title)).setSessionCommand(command(action)).build() }
    }
}
