@file:Suppress("TooGenericExceptionCaught") // Keep local navigation available when online sections fail.

package app.vimusic.android.auto.template

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Row
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import app.vimusic.android.R
import app.vimusic.android.auto.AutoMediaId
import app.vimusic.android.preferences.PlayerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CarHomeScreen(context: CarContext, private val client: CarMediaClient, private val grid: Boolean = false, initialTab: String = AutoMediaId.HOME) : Screen(context), DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tabs = listOf(
        Triple(AutoMediaId.HOME, R.string.auto_home, R.drawable.musical_notes),
        Triple(AutoMediaId.LIBRARY, R.string.library, R.drawable.playlist),
        Triple(AutoMediaId.DISCOVER, R.string.discover, R.drawable.trending),
        Triple(AutoMediaId.OFFLINE, R.string.offline, R.drawable.airplane),
    )
    private var selected = initialTab
    private var nextFlow: (() -> Screen)? = null
    private var job: Job? = null
    private var sections = emptyList<Pair<MediaItem, List<MediaItem>>>()
    private var loading = true
    private var failed = false
    private val changed: (String) -> Unit = { parent -> if (parent == selected && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) load() }
    private val playbackChanged: () -> Unit = { invalidate() }

    init { lifecycle.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) { client.observeLibrary(changed, true); client.observe(playbackChanged); load() }
    override fun onStop(owner: LifecycleOwner) { client.observeLibrary(changed, false); client.stopObserving(playbackChanged); job?.cancel() }
    override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
    fun startFlow(destination: () -> Screen) {
        if (screenManager.top === this) CarNavigation.push(this, destination(), client)
        else {
            nextFlow = destination
            screenManager.popToRoot()
        }
    }
    private fun load() {
        job?.cancel()
        job = scope.launch {
            try {
                val children = client.children(selected)
                val updated = children.map { it to emptyList<MediaItem>() }.toMutableList()
                client.subscribe(selected)
                if (selected != AutoMediaId.LIBRARY) {
                    val quota = ((CarUi.limit(carContext, grid) - children.size - 1) / children.size.coerceAtLeast(1)).coerceIn(0, 3)
                    children.forEachIndexed { index, folder ->
                        val entries = if (quota == 0) emptyList() else try { client.children(folder.mediaId, quota + 2).filter {
                            AutoMediaId.parse(it.mediaId)?.kind !in listOf("play-all", "shuffle")
                        }.take(quota) } catch (error: CancellationException) { throw error } catch (_: Exception) { emptyList() }
                        updated[index] = folder to entries
                    }
                }
                // Publish the completed sections together; changing row counts after
                // every preview is treated as extra task steps by some car hosts.
                sections = updated
                loading = false; failed = false; invalidate()
            } catch (error: CancellationException) { throw error } catch (error: Exception) { android.util.Log.w("ViMusicCar", "Unable to load home", error); loading = false; failed = true; invalidate() }
        }
    }
    private fun open(item: MediaItem) {
        if (item.mediaMetadata.isBrowsable == true) {
            val title = item.mediaMetadata.title.toString()
            CarNavigation.push(this, when (AutoMediaId.parse(item.mediaId)?.kind) {
                "discover" -> CarDiscoverScreen(carContext, client)
                "offline" -> CarOfflineScreen(carContext, client)
                else -> CarLibraryScreen(carContext, client, item.mediaId, title, grid)
            }, client)
        } else scope.launch {
            try { client.play(item); CarNavigation.playback(this@CarHomeScreen, client) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { androidx.car.app.CarToast.makeText(carContext, R.string.auto_error, androidx.car.app.CarToast.LENGTH_SHORT).show() }
        }
    }
    override fun onGetTemplate(): Template {
        nextFlow?.let { destination ->
            nextFlow = null
            // Deliver the actual back template before beginning the next task.
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (screenManager.top === this && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    CarNavigation.push(this, destination(), client)
                }
            }
        }
        // Tab contents ignore header actions; template actions remain visible on every tab.
        val contents = SectionedItemTemplate.Builder().setActions(CarUi.browseActions(this, client))
        if (!PlayerPreferences.isAndroidAutoEnabled || failed) {
            contents.addSection(androidx.car.app.model.RowSection.Builder().setItems(listOf(
                androidx.car.app.model.Row.Builder().setTitle(carContext.getString(if (failed) R.string.auto_error else R.string.auto_disabled))
                    .setOnClickListener { loading = true; load(); invalidate() }.build(),
            )).build())
        } else if (loading) contents.setLoading(true)
        else {
            sections.forEach { (folder, previews) ->
                val entries = previews + folder
                contents.addSection(CarUi.section(carContext, folder.mediaMetadata.title.toString(), entries, grid, ::open) {
                    CarNavigation.push(this, CarSongOptionsScreen(carContext, client, it), client)
                })
            }
            if (sections.sumOf { it.second.size + 1 } < CarUi.limit(carContext, grid)) contents.addSection(RowSection.Builder().setItems(listOf(Row.Builder()
                .setTitle(carContext.getString(if (grid) R.string.auto_list else R.string.auto_grid))
                .setImage(CarUi.icon(carContext, R.drawable.shapes))
                .setOnClickListener { CarNavigation.push(this, CarHomeScreen(carContext, client, !grid, selected), client) }.build())).build())
        }
        return TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabContentId: String) { if (selected != tabContentId) { selected = tabContentId; loading = true; sections = emptyList(); load(); invalidate() } }
        }).setHeaderAction(Action.APP_ICON).setActiveTabContentId(selected)
            .apply { tabs.forEach { (id, label, icon) -> addTab(Tab.Builder().setContentId(id).setTitle(carContext.getString(label)).setIcon(CarUi.icon(carContext, icon)).build()) } }
            .setTabContents(TabContents.Builder(contents.build()).build()).build()
    }
}
