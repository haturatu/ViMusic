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
    init { lifecycle.addObserver(this) }
    override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
    private fun search(text: String, submit: Boolean) {
        query = text.take(200)
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
    override fun onGetTemplate(): Template {
        val list = ItemList.Builder().setNoItemsMessage(carContext.getString(if (failed) R.string.auto_error else R.string.auto_empty))
        results.take(CarUi.limit(carContext)).forEach { item -> list.addItem(CarUi.row(carContext, item, {
            if (item.mediaMetadata.isBrowsable == true) screenManager.push(CarLibraryScreen(carContext, client, item.mediaId, item.mediaMetadata.title.toString()))
            else scope.launch {
                try { client.play(item); screenManager.push(CarPlaybackScreen(carContext, client)) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { CarToast.makeText(carContext, R.string.auto_error, CarToast.LENGTH_SHORT).show() }
            }
        }, if (item.mediaMetadata.isPlayable == true) ({ screenManager.push(CarSongOptionsScreen(carContext, client, item)) }) else null)) }
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) { search(searchText, false) }
            override fun onSearchSubmitted(searchText: String) { search(searchText, true) }
        }).setHeaderAction(Action.BACK).setSearchHint(carContext.getString(R.string.search_placeholder))
            .setInitialSearchText(query).setShowKeyboardByDefault(true).setLoading(loading)
            .apply { if (!loading) setItemList(list.build()) }.build()
    }
}
