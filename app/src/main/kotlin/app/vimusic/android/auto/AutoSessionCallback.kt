@file:Suppress("TooGenericExceptionCaught", "TooManyFunctions") // Complete every asynchronous host request, including failures.

package app.vimusic.android.auto

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.vimusic.android.preferences.PlayerPreferences
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoSessionCallback(
    private val scope: CoroutineScope,
    private val tree: AutoLibraryTree,
    private val resolver: AutoMediaResolver,
    private val commands: AutoCommandHandler,
    private val resumption: AutoPlaybackResumption,
    private val queueSelected: () -> Unit,
) : MediaLibraryService.MediaLibrarySession.Callback {
    private val context: Context get() = tree.context
    override fun onConnectAsync(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.ConnectionResult> =
        Futures.immediateFuture(connectionResult(session, controller))

    fun connectionResult(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        // The deprecated onConnect default is an empty sentinel in Media3 1.11.
        // The controller-aware builder supplies the session/library permissions.
        val defaults = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
        val enabled = PlayerPreferences.isAndroidAutoEnabled || controller.packageName == context.packageName
        val canControl = controller.isTrusted || controller.packageName == context.packageName
        val available = SessionCommands.Builder().addSessionCommands(defaults.availableSessionCommands.commands.filter {
            enabled || MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.contains(it)
        }).apply { if (enabled && canControl) AutoCommandHandler.ALL_ACTIONS.forEach { add(AutoCommandHandler.command(it)) } }.build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(available).setAvailablePlayerCommands(defaults.availablePlayerCommands)
            .build()
    }

    override fun onGetLibraryRoot(session: MediaLibraryService.MediaLibrarySession, controller: MediaSession.ControllerInfo, params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(if (!PlayerPreferences.isAndroidAutoEnabled) LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED) else LibraryResult.ofItem(tree.root(), MediaLibraryService.LibraryParams.Builder().setExtras(Bundle().apply {
            putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
        }).build()))

    override fun onGetChildren(session: MediaLibraryService.MediaLibrarySession, controller: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = library {
        require(page >= 0 && pageSize > 0)
        val flat = controller.packageName == context.packageName && params?.extras?.getBoolean(AutoLibraryTree.FLAT_BROWSE) == true
        val items = tree.children(parentId, flat)
        val resultParams = if (flat) MediaLibraryService.LibraryParams.Builder().setExtras(Bundle(params?.extras ?: Bundle.EMPTY).apply {
            putInt(AutoLibraryTree.TOTAL_ITEMS, items.size)
        }).build() else params
        LibraryResult.ofItemList(slice(items, page, pageSize, if (flat) AutoLibraryTree.FLAT_PAGE_SIZE else AutoLibraryTree.PAGE_SIZE), resultParams)
    }

    override fun onGetItem(session: MediaLibraryService.MediaLibrarySession, controller: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = library {
        val item = tree.item(mediaId)
        LibraryResult.ofItem(item, null)
    }

    override fun onSubscribe(session: MediaLibraryService.MediaLibrarySession, controller: MediaSession.ControllerInfo, parentId: String, params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<Void>> = library {
        val count = tree.children(parentId).size
        withContext(Dispatchers.Main.immediate) { session.notifyChildrenChanged(controller, parentId, count, params) }
        LibraryResult.ofVoid()
    }

    override fun onSearch(session: MediaLibraryService.MediaLibrarySession, controller: MediaSession.ControllerInfo, query: String, params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<Void>> = library {
        val items = tree.searchResults(query)
        withContext(Dispatchers.Main.immediate) { session.notifySearchResultChanged(controller, query, items.size, params) }
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(session: MediaLibraryService.MediaLibrarySession, controller: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int, params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = library {
        require(page >= 0 && pageSize > 0)
        LibraryResult.ofItemList(slice(tree.searchResults(query), page, pageSize), params)
    }

    override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> = future { resolver.add(mediaItems) }

    override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        queueSelected()
        resumption.markExplicitRequest()
        return future {
            val resolved = resolver.set(mediaItems, startIndex, startPositionMs)
            val id = mediaItems.singleOrNull()?.mediaId?.let(AutoMediaId::parse)
            withContext(Dispatchers.Main.immediate) {
                if (id != null && id.kind != "resume" && !(id.kind == "play-all" && AutoMediaId.parse(id.key)?.kind == "resume")) {
                    session.player.shuffleModeEnabled = false
                }
            }
            resolved
        }
    }

    override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
        val saved = resumption.load()
        val result = resumption.resolve(saved)
        if (isForPlayback) withContext(Dispatchers.Main.immediate) { resumption.restoreModes(saved, session.player) }
        result
    }

    override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> = future {
        if (!PlayerPreferences.isAndroidAutoEnabled && controller.packageName != context.packageName) SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED)
        else try { commands.execute(customCommand.customAction, args) } catch (error: IllegalArgumentException) { Log.w("AutoSession", "Invalid custom command arguments", error); SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE) }
    }

    private fun <T : Any> library(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> = future {
        if (!PlayerPreferences.isAndroidAutoEnabled) LibraryResult.ofError<T>(SessionError.ERROR_PERMISSION_DENIED)
        else try { block() } catch (error: CancellationException) { throw error } catch (error: IllegalArgumentException) { Log.w("AutoLibrary", "Invalid library request", error); LibraryResult.ofError<T>(SessionError.ERROR_BAD_VALUE) } catch (error: Exception) {
            Log.e("AutoLibrary", "Library request failed", error); LibraryResult.ofError<T>(SessionError.ERROR_UNKNOWN)
        }
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        val job = scope.launch(Dispatchers.IO) {
            try { result.set(block()) } catch (error: CancellationException) { result.cancel(false); throw error } catch (error: Exception) { Log.e("AutoSession", "Session request failed", error); result.setException(error) }
        }
        job.invokeOnCompletion { if (it is CancellationException) result.cancel(false) }
        result.addListener({ if (result.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return result
    }

    private fun <T> slice(items: List<T>, page: Int, pageSize: Int, maxPageSize: Int = AutoLibraryTree.PAGE_SIZE): List<T> {
        val start = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
        return items.subList(start, (start.toLong() + pageSize.coerceAtMost(maxPageSize)).coerceAtMost(items.size.toLong()).toInt())
    }
}
