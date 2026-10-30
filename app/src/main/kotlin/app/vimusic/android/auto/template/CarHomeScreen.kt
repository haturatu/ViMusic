@file:Suppress("TooGenericExceptionCaught") // Keep local navigation available when online sections fail.

package app.vimusic.android.auto.template

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.Section
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
internal class CarHomeScreen(context: CarContext, private val client: CarMediaClient) : Screen(context), DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tabs = listOf(
        Triple(AutoMediaId.HOME, R.string.auto_home, R.drawable.musical_notes),
        Triple(AutoMediaId.LIBRARY, R.string.library, R.drawable.playlist),
        Triple(AutoMediaId.DISCOVER, R.string.discover, R.drawable.trending),
        Triple(AutoMediaId.OFFLINE, R.string.offline, R.drawable.airplane),
    )
    private var selected = AutoMediaId.HOME
    private var grid = false
    private var job: Job? = null
    private var sections = emptyList<Pair<MediaItem, List<MediaItem>>>()
    private var loading = true
    private var failed = false
    private val changed: () -> Unit = { if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) load() }

    init { lifecycle.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) { client.observeLibrary(changed, true); load() }
    override fun onStop(owner: LifecycleOwner) { client.observeLibrary(changed, false); job?.cancel() }
    override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
    private fun load() {
        job?.cancel()
        job = scope.launch {
            try {
                val children = client.children(selected)
                sections = children.map { it to emptyList() }
                loading = false; failed = false; invalidate()
                client.subscribe(selected)
                if (selected != AutoMediaId.LIBRARY) {
                    val quota = ((CarUi.limit(carContext, grid) - children.size) / children.size.coerceAtLeast(1)).coerceIn(0, 3)
                    children.forEachIndexed { index, folder ->
                        val entries = try { client.children(folder.mediaId).take(quota) } catch (error: CancellationException) { throw error } catch (_: Exception) { emptyList() }
                        sections = sections.toMutableList().apply { this[index] = folder to entries }
                        invalidate()
                    }
                }
            } catch (error: CancellationException) { throw error } catch (error: Exception) { android.util.Log.w("ViMusicCar", "Unable to load home", error); loading = false; failed = true; invalidate() }
        }
    }
    private fun open(item: MediaItem) {
        if (item.mediaMetadata.isBrowsable == true) {
            val title = item.mediaMetadata.title.toString()
            screenManager.push(when (AutoMediaId.parse(item.mediaId)?.kind) {
                "discover" -> CarDiscoverScreen(carContext, client)
                "offline" -> CarOfflineScreen(carContext, client)
                else -> CarLibraryScreen(carContext, client, item.mediaId, title)
            })
        } else scope.launch {
            try { client.play(item); screenManager.push(CarPlaybackScreen(carContext, client)) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { androidx.car.app.CarToast.makeText(carContext, R.string.auto_error, androidx.car.app.CarToast.LENGTH_SHORT).show() }
        }
    }
    override fun onGetTemplate(): Template {
        val title = carContext.getString(tabs.first { it.first == selected }.second)
        val header = Header.Builder().setTitle(title)
            .addEndHeaderAction(CarUi.action(carContext, R.drawable.search) { screenManager.push(CarSearchScreen(carContext, client)) })
            .addEndHeaderAction(CarUi.action(carContext, R.drawable.play) { screenManager.push(CarPlaybackScreen(carContext, client)) }).build()
        val contents = SectionedItemTemplate.Builder().setHeader(header)
        if (!PlayerPreferences.isAndroidAutoEnabled || failed) {
            contents.addSection(androidx.car.app.model.RowSection.Builder().setItems(listOf(
                androidx.car.app.model.Row.Builder().setTitle(carContext.getString(if (failed) R.string.auto_error else R.string.auto_disabled))
                    .setOnClickListener { loading = true; load(); invalidate() }.build(),
            )).build())
        } else if (loading) contents.setLoading(true)
        else {
            sections.forEach { (folder, previews) ->
                val entries = (previews + folder).take(CarUi.limit(carContext, grid))
                contents.addSection(CarUi.section(carContext, folder.mediaMetadata.title.toString(), entries, grid, ::open) {
                    screenManager.push(CarSongOptionsScreen(carContext, client, it))
                })
            }
            contents.addAction(Action.Builder()
                .setIcon(CarUi.icon(carContext, R.drawable.shapes))
                .setBackgroundColor(CarColor.BLUE)
                .setOnClickListener { grid = !grid; load(); invalidate() }
                .build())
        }
        return TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabContentId: String) { if (selected != tabContentId) { selected = tabContentId; loading = true; sections = emptyList(); load(); invalidate() } }
        }).setHeaderAction(Action.APP_ICON).setActiveTabContentId(selected)
            .apply { tabs.forEach { (id, label, icon) -> addTab(Tab.Builder().setContentId(id).setTitle(carContext.getString(label)).setIcon(CarUi.icon(carContext, icon)).build()) } }
            .setTabContents(TabContents.Builder(contents.build()).build()).build()
    }
}
