package app.vimusic.android.auto.template

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.media.model.MediaPlaybackTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.vimusic.android.R
import app.vimusic.android.auto.AutoCommandHandler
import kotlinx.coroutines.launch

internal class CarPlaybackScreen(context: CarContext, private val client: CarMediaClient) : Screen(context), DefaultLifecycleObserver {
    private val changed: () -> Unit = { invalidate() }
    init { lifecycle.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) { client.observe(changed) }
    override fun onStop(owner: LifecycleOwner) { client.stopObserving(changed) }
    override fun onGetTemplate(): Template {
        val header = Header.Builder().setTitle(carContext.getString(R.string.auto_now_playing)).setStartHeaderAction(androidx.car.app.model.Action.BACK)
            .addEndHeaderAction(CarUi.action(carContext, R.drawable.playlist) { screenManager.push(CarQueueScreen(carContext, client)) })
            .addEndHeaderAction(CarUi.action(carContext, R.drawable.search) { screenManager.push(CarSearchScreen(carContext, client)) }).build()
        if (client.nativePlaybackAvailable && carContext.carAppApiLevel >= 8) return MediaPlaybackTemplate.Builder().setHeader(header).build()
        val player = client.browser ?: return ListTemplate.Builder().setHeader(header).setLoading(true).build()
        val list = ItemList.Builder().setNoItemsMessage(carContext.getString(R.string.auto_empty))
        list.addItem(Row.Builder().setTitle(player.mediaMetadata.title?.toString().orEmpty().ifBlank { carContext.getString(R.string.auto_now_playing) })
            .addText(player.mediaMetadata.artist?.toString().orEmpty()).build())
        list.addItem(Row.Builder().setTitle(carContext.getString(if (player.isPlaying) R.string.auto_pause else R.string.auto_play))
            .setOnClickListener { if (player.isPlaying) player.pause() else { player.prepare(); player.play() }; invalidate() }.build())
        list.addItem(Row.Builder().setTitle(carContext.getString(R.string.auto_previous)).setOnClickListener { player.seekToPreviousMediaItem() }.build())
        list.addItem(Row.Builder().setTitle(carContext.getString(R.string.auto_next)).setOnClickListener { player.seekToNextMediaItem() }.build())
        list.addItem(Row.Builder().setTitle(carContext.getString(R.string.favorites)).setOnClickListener {
            client.scope.launch { runCatching { client.command(AutoCommandHandler.FAVORITE) } }
        }.build())
        return ListTemplate.Builder().setHeader(header).setSingleList(list.build()).build()
    }
}
