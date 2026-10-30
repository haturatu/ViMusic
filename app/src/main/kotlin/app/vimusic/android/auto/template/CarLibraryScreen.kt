@file:Suppress("TooGenericExceptionCaught") // Host templates remain usable after a failed request.

package app.vimusic.android.auto.template

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import app.vimusic.android.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal open class CarLibraryScreen(
    context: CarContext,
    protected val client: CarMediaClient,
    private val parent: String,
    private val title: String,
) : Screen(context), DefaultLifecycleObserver {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var items: List<MediaItem> = emptyList()
    private var loading = true
    private var failed = false
    private var grid = false
    private var page = 0
    private val changed: () -> Unit = { if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) reload() }
    private val playbackChanged: () -> Unit = { invalidate() }

    init { lifecycle.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) { client.observeLibrary(changed, true); client.observe(playbackChanged); reload() }
    override fun onStop(owner: LifecycleOwner) { client.observeLibrary(changed, false); client.stopObserving(playbackChanged); job?.cancel() }
    override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
    private fun reload() {
        job?.cancel()
        job = scope.launch {
            try {
                items = client.children(parent)
                client.subscribe(parent)
                failed = false
            } catch (error: CancellationException) { throw error } catch (error: Exception) { android.util.Log.w("ViMusicCar", "Unable to load collection", error); failed = true }
            loading = false
            invalidate()
        }
    }
    protected fun open(item: MediaItem) {
        if (item.mediaMetadata.isBrowsable == true) screenManager.push(CarLibraryScreen(carContext, client, item.mediaId, item.mediaMetadata.title.toString()))
        else scope.launch {
            try { client.play(item); screenManager.push(CarPlaybackScreen(carContext, client)) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { android.util.Log.w("ViMusicCar", "Unable to play selection", error); androidx.car.app.CarToast.makeText(carContext, R.string.auto_error, androidx.car.app.CarToast.LENGTH_SHORT).show() }
        }
    }
    override fun onGetTemplate(): Template {
        val header = CarUi.header(this, title) { grid = !grid; page = 0; invalidate() }
        val template = SectionedItemTemplate.Builder().setHeader(header).setActions(CarUi.browseActions(this, client))
        // Loading and results belong to one host step. Switching template types spends
        // another step and can exceed the car host's task limit while browsing.
        if (loading) return template.setLoading(true).build()
        if (failed || items.isEmpty()) return template.addSection(androidx.car.app.model.RowSection.Builder().setItems(listOf(
            Row.Builder().setTitle(carContext.getString(if (failed) R.string.auto_error else R.string.auto_empty))
                .apply { if (failed) setOnClickListener { loading = true; reload(); invalidate() } }.build(),
        )).build()).build()
        val size = CarUi.BROWSE_PAGE_SIZE
        page = page.coerceIn(0, (items.size - 1) / size)
        val visible = items.drop(page * size).take(size)
        val sections = mutableListOf(CarUi.section(carContext, title, visible, grid, ::open) {
            screenManager.push(CarSongOptionsScreen(carContext, client, it))
        })
        val navigation = mutableListOf<Row>()
        if (page > 0) navigation += Row.Builder().setTitle(carContext.getString(R.string.auto_previous)).setOnClickListener { page--; invalidate() }.build()
        if ((page + 1) * size < items.size) navigation += Row.Builder().setTitle(carContext.getString(R.string.auto_next)).setOnClickListener { page++; invalidate() }.build()
        if (navigation.isNotEmpty()) sections += androidx.car.app.model.RowSection.Builder().setItems(navigation).build()
        return template.setSections(sections).build()
    }
}
