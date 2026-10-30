@file:Suppress("TooGenericExceptionCaught") // Surface asynchronous controller errors without dropping the car session.

package app.vimusic.android.auto.template

import android.content.ComponentName
import android.media.session.MediaSession
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import androidx.car.app.CarContext
import androidx.car.app.media.MediaPlaybackManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.vimusic.android.auto.AutoCommandHandler
import app.vimusic.android.auto.AutoLibraryTree
import app.vimusic.android.service.PlayerService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CarMediaClient(val context: CarContext, lifecycle: Lifecycle) : DefaultLifecycleObserver {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val libraryListeners = mutableSetOf<(String) -> Unit>()
    private val subscriptions = mutableSetOf<String>()
    var nativePlaybackAvailable = false
        private set
    private val future = MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, PlayerService::class.java)))
        .setListener(object : MediaBrowser.Listener {
            override fun onChildrenChanged(browser: MediaBrowser, parentId: String, itemCount: Int, params: androidx.media3.session.MediaLibraryService.LibraryParams?) {
                libraryListeners.toList().forEach { it(parentId) }
            }
        }).buildAsync()
    var browser: MediaBrowser? = null
        private set
    private val listeners = mutableSetOf<() -> Unit>()
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { listeners.toList().forEach { it() } }
    }

    init {
        lifecycle.addObserver(this)
        future.addListener({
            if (future.isCancelled) return@addListener
            try {
                browser = future.get().also { it.addListener(playerListener) }
                @Suppress("DEPRECATION")
                val token = browser?.sessionExtras?.getParcelable<MediaSession.Token>(AutoCommandHandler.SESSION_TOKEN)
                if (context.carAppApiLevel >= 8 && token != null) {
                    MediaSessionCompat.Token.fromToken(token)?.let {
                        context.getCarService(MediaPlaybackManager::class.java).registerMediaPlaybackToken(it)
                        nativePlaybackAvailable = true
                    }
                }
            } catch (error: Exception) { android.util.Log.e("ViMusicCar", "Unable to connect media session", error) }
            listeners.toList().forEach { it() }
        }, ContextCompat.getMainExecutor(context))
    }

    fun observeLibrary(listener: (String) -> Unit, observe: Boolean) {
        if (observe) libraryListeners += listener else libraryListeners -= listener
    }
    suspend fun subscribe(parent: String) {
        val controller = connected()
        if (subscriptions.add(parent)) controller.subscribe(parent, null)
    }
    fun observe(listener: () -> Unit) { listeners += listener }
    fun stopObserving(listener: () -> Unit) { listeners -= listener }
    suspend fun connected(): MediaBrowser = browser ?: await(future)
    suspend fun children(parent: String, limit: Int = Int.MAX_VALUE): List<MediaItem> {
        val controller = connected()
        val params = androidx.media3.session.MediaLibraryService.LibraryParams.Builder().setExtras(Bundle().apply {
            putBoolean(AutoLibraryTree.FLAT_BROWSE, true)
        }).build()
        val items = mutableListOf<MediaItem>()
        var page = 0
        do {
            val result = await(controller.getChildren(parent, page++, AutoLibraryTree.FLAT_PAGE_SIZE, params))
            if (result.resultCode < 0) throw IOException("Library unavailable")
            val batch = result.value.orEmpty()
            items += batch
            val total = result.params?.extras?.getInt(AutoLibraryTree.TOTAL_ITEMS, items.size) ?: items.size
        } while (batch.isNotEmpty() && items.size < minOf(total, limit))
        return items.take(limit)
    }
    suspend fun search(query: String): List<MediaItem> {
        val controller = connected()
        val searchResult = await(controller.search(query, null))
        if (searchResult.resultCode < 0) throw IOException("Search unavailable")
        val result = await(controller.getSearchResult(query, 0, AutoLibraryTree.PAGE_SIZE, null))
        if (result.resultCode < 0) throw IOException("Search unavailable")
        return result.value?.toList().orEmpty()
    }
    suspend fun play(item: MediaItem) {
        connected().apply { setMediaItem(item); prepare(); play() }
    }
    suspend fun command(action: String, args: Bundle = Bundle.EMPTY) {
        val result = await(connected().sendCustomCommand(AutoCommandHandler.command(action), args))
        if (result.resultCode < 0) throw IOException("Command unavailable")
    }
    override fun onDestroy(owner: LifecycleOwner) {
        browser?.removeListener(playerListener)
        MediaController.releaseFuture(future)
        listeners.clear()
        libraryListeners.clear()
        scope.cancel()
    }

    private suspend fun <T> await(result: ListenableFuture<T>): T = suspendCancellableCoroutine { continuation ->
        result.addListener({
            if (continuation.isActive) {
                try { continuation.resume(result.get()) }
                catch (error: Exception) { continuation.resumeWithException(error.cause ?: error) }
            }
        }, ContextCompat.getMainExecutor(context))
        continuation.invokeOnCancellation { if (result !== future) result.cancel(false) }
    }
}
