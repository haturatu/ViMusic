@file:Suppress("TooGenericExceptionCaught")

package app.vimusic.android.auto.template

import android.os.Bundle
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.media3.common.MediaItem
import app.vimusic.android.R
import app.vimusic.android.auto.AutoCommandHandler
import app.vimusic.android.auto.AutoMediaId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CarSongOptionsScreen(context: CarContext, private val client: CarMediaClient, private val item: MediaItem) : Screen(context) {
    override fun onGetTemplate(): Template {
        val aggregate = AutoMediaId.parse(item.mediaId)?.kind in listOf("play-all", "shuffle", "radio")
        val local = (AutoMediaId.parse(item.mediaId)?.key ?: item.mediaId).startsWith("local:")
        val choices = mutableListOf(
            R.string.play_next to AutoCommandHandler.NEXT,
            R.string.auto_add_queue to AutoCommandHandler.APPEND,
        )
        if (!aggregate) choices += R.string.favorites to AutoCommandHandler.FAVORITE
        if (!aggregate && !local) choices += R.string.auto_radio to AutoCommandHandler.RADIO
        val list = ItemList.Builder()
        choices.forEach { (title, action) -> list.addItem(Row.Builder().setTitle(carContext.getString(title)).setOnClickListener {
            client.scope.launch {
                try {
                    client.command(action, Bundle().apply { putString(AutoCommandHandler.ARG_MEDIA_ID, item.mediaId) })
                    screenManager.pop()
                } catch (error: CancellationException) { throw error } catch (_: Exception) { CarToast.makeText(carContext, R.string.auto_error, CarToast.LENGTH_SHORT).show() }
            }
        }.build()) }
        return ListTemplate.Builder().setHeader(CarUi.header(this, item.mediaMetadata.title?.toString() ?: carContext.getString(R.string.auto_options), client)).setSingleList(list.build()).build()
    }
}
