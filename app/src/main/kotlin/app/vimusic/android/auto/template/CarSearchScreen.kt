@file:Suppress("TooGenericExceptionCaught")

package app.vimusic.android.auto.template

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import app.vimusic.android.R
import app.vimusic.android.auto.AutoMediaId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CarSearchScreen(context: CarContext, private val client: CarMediaClient) : Screen(context), DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var query = ""
    private var results = emptyList<MediaItem>()
    private var loading = false
    private var failed = false
    private var page = 0
    private val categoryBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { search(query, true) }
    }
    init { lifecycle.addObserver(this); carContext.onBackPressedDispatcher.addCallback(this, categoryBack) }
    override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
    private fun search(text: String, submit: Boolean) {
        query = text.take(200)
        categoryBack.isEnabled = false
        page = 0
        job?.cancel()
        if (query.isBlank()) { results = emptyList(); loading = false; invalidate(); return }
        loading = true; invalidate()
        val request = query
        job = scope.launch {
            if (!submit) delay(350)
            try { results = client.search(request); failed = false }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { results = emptyList(); failed = true }
            loading = false; invalidate()
        }
    }
    private fun openCategory(item: MediaItem) {
        job?.cancel()
        loading = true; page = 0; categoryBack.isEnabled = true; invalidate()
        job = scope.launch {
            try { results = client.children(item.mediaId); failed = false }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { results = emptyList(); failed = true }
            loading = false; invalidate()
        }
    }
    override fun onGetTemplate(): Template {
        val list = ItemList.Builder().setNoItemsMessage(carContext.getString(if (failed) R.string.auto_error else R.string.auto_empty))
        val size = if (results.size <= CarUi.limit(carContext)) CarUi.limit(carContext) else (CarUi.limit(carContext) - 2).coerceAtLeast(1)
        page = page.coerceIn(0, (results.size - 1).coerceAtLeast(0) / size)
        results.drop(page * size).take(size).forEach { item -> list.addItem(CarUi.row(carContext, item, {
            if (AutoMediaId.parse(item.mediaId)?.kind?.startsWith("search-") == true) openCategory(item)
            else if (item.mediaMetadata.isBrowsable == true) CarNavigation.push(this, CarLibraryScreen(carContext, client, item.mediaId, item.mediaMetadata.title.toString()), client)
            else scope.launch {
                try { client.play(item); CarNavigation.playback(this@CarSearchScreen, client) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { CarToast.makeText(carContext, R.string.auto_error, CarToast.LENGTH_SHORT).show() }
            }
        }, if (item.mediaMetadata.isPlayable == true) ({ CarNavigation.push(this, CarSongOptionsScreen(carContext, client, item), client) }) else null)) }
        if (page > 0) list.addItem(Row.Builder().setTitle(carContext.getString(R.string.auto_previous)).setOnClickListener { page--; invalidate() }.build())
        if ((page + 1) * size < results.size) list.addItem(Row.Builder().setTitle(carContext.getString(R.string.auto_next)).setOnClickListener { page++; invalidate() }.build())
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) { if (searchText != query) search(searchText, false) }
            override fun onSearchSubmitted(searchText: String) { search(searchText, true) }
        }).setHeaderAction(Action.BACK).setSearchHint(carContext.getString(R.string.search_placeholder))
            .setInitialSearchText(query).setShowKeyboardByDefault(!categoryBack.isEnabled).setLoading(loading)
            .apply { if (!loading) setItemList(list.build()) }.build()
    }
}
