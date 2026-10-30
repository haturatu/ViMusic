@file:Suppress("TooGenericExceptionCaught")

package app.vimusic.android.auto.template

import android.os.Bundle
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Header
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import app.vimusic.android.R
import app.vimusic.android.auto.AutoCommandHandler
import app.vimusic.android.auto.AutoQueueController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CarQueueScreen(context: CarContext, private val client: CarMediaClient) : Screen(context), DefaultLifecycleObserver {
    private var items: List<MediaItem>? = null
    private var signature = ""
    private var warned = false
    private fun snapshot() {
        client.browser?.let { player ->
            items = List(player.mediaItemCount, player::getMediaItemAt)
            signature = AutoQueueController.signature(player)
            warned = false
        }
    }
    private val changed: () -> Unit = {
        if (items == null) snapshot()
        else client.browser?.let { player ->
            if (!warned && signature != AutoQueueController.signature(player)) {
                warned = true
                CarToast.makeText(carContext, R.string.auto_queue_changed, CarToast.LENGTH_SHORT).show()
            }
        }
        invalidate()
    }
    init { lifecycle.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) { snapshot(); client.observe(changed) }
    override fun onStop(owner: LifecycleOwner) { client.stopObserving(changed) }
    override fun onGetTemplate(): Template {
        val header = Header.Builder().setTitle(carContext.getString(R.string.auto_queue)).setStartHeaderAction(Action.BACK)
            .addEndHeaderAction(CarUi.action(carContext, R.drawable.shuffle) { client.scope.launch { runCatching { client.command(AutoCommandHandler.SHUFFLE) } } })
            .addEndHeaderAction(CarUi.action(carContext, R.drawable.infinite) { client.scope.launch { runCatching { client.command(AutoCommandHandler.REPEAT) } } }).build()
        val template = SectionedItemTemplate.Builder().setHeader(header).setActions(CarUi.browseActions(this, client))
        val queue = items ?: return template.setLoading(true).build()
        val count = queue.size
        val expectedSignature = signature
        val list = RowSection.Builder().setNoItemsMessage(carContext.getString(R.string.auto_empty))
        for ((index, item) in queue.withIndex()) {
            val label = "${index + 1}. ${item.mediaMetadata.title?.toString().orEmpty()}"
            list.addItem(CarUi.row(carContext, item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setTitle(label).build()).build(), {
                val player = client.browser
                if (player != null && AutoQueueController.signature(player) == expectedSignature) {
                    player.seekTo(index, C.TIME_UNSET); player.prepare(); player.play()
                    CarNavigation.playback(this, client)
                } else CarToast.makeText(carContext, R.string.auto_queue_changed, CarToast.LENGTH_SHORT).show()
            }, { CarNavigation.push(this, CarQueueItemScreen(carContext, client, item, index, count, expectedSignature), client) }))
        }
        return template.addSection(list.build()).build()
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class CarQueueItemScreen(context: CarContext, private val client: CarMediaClient, private val item: MediaItem, private val index: Int, private val count: Int, private val signature: String) : Screen(context) {
    override fun onGetTemplate(): Template {
        val choices = mutableListOf(Triple(R.string.auto_remove, AutoCommandHandler.REMOVE, index))
        if (index > 0) choices += Triple(R.string.auto_move_up, AutoCommandHandler.MOVE, index - 1)
        if (index + 1 < count) choices += Triple(R.string.auto_move_down, AutoCommandHandler.MOVE, index + 1)
        val list = ItemList.Builder()
        choices.forEach { (title, action, destination) -> list.addItem(Row.Builder().setTitle(carContext.getString(title)).setOnClickListener {
            client.scope.launch {
                try {
                    client.command(action, Bundle().apply {
                        putInt(AutoCommandHandler.ARG_INDEX, index)
                        putInt(AutoCommandHandler.ARG_TO_INDEX, destination)
                        putString(AutoCommandHandler.ARG_EXPECTED_ID, item.mediaId)
                        putString(AutoCommandHandler.ARG_QUEUE_SIGNATURE, signature)
                    })
                    screenManager.pop()
                } catch (error: CancellationException) { throw error } catch (_: Exception) { CarToast.makeText(carContext, R.string.auto_queue_changed, CarToast.LENGTH_SHORT).show() }
            }
        }.build()) }
        return ListTemplate.Builder().setHeader(CarUi.header(this, item.mediaMetadata.title?.toString().orEmpty()))
            .apply { CarUi.browseActions(this@CarQueueItemScreen, client).forEach { addAction(it) } }
            .setSingleList(list.build()).build()
    }
}
