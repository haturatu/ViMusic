package app.vimusic.android.auto.template

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Row
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridSection
import androidx.car.app.model.RowSection
import androidx.car.app.model.Section
import androidx.car.app.constraints.ConstraintManager
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import app.vimusic.android.R

internal object CarUi {
    fun icon(context: CarContext, drawable: Int): CarIcon = CarIcon.Builder(IconCompat.createWithResource(context, drawable)).build()
    fun action(context: CarContext, drawable: Int, click: () -> Unit) = Action.Builder().setIcon(icon(context, drawable)).setOnClickListener(click).build()
    fun header(screen: Screen, title: String, client: CarMediaClient, toggle: (() -> Unit)? = null): Header = Header.Builder()
        .setTitle(title.take(80)).setStartHeaderAction(Action.BACK)
        .addEndHeaderAction(action(screen.carContext, R.drawable.play) { screen.screenManager.push(CarPlaybackScreen(screen.carContext, client)) })
        .apply { if (toggle != null) addEndHeaderAction(action(screen.carContext, R.drawable.shapes, toggle)) }
        .build()
    fun limit(context: CarContext, grid: Boolean = false): Int = context.getCarService(ConstraintManager::class.java)
        .getContentLimit(if (grid) ConstraintManager.CONTENT_LIMIT_TYPE_GRID else ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        .coerceIn(3, 100)
    fun row(context: CarContext, item: MediaItem, click: () -> Unit, options: (() -> Unit)? = null): Row = Row.Builder()
        .setTitle(item.mediaMetadata.title?.toString()?.take(100).orEmpty().ifBlank { "ViMusic" })
        .apply {
            item.mediaMetadata.subtitle?.toString()?.takeIf { it.isNotBlank() }?.let { addText(it.take(100)) }
            setImage(icon(context, if (item.mediaMetadata.isBrowsable == true) R.drawable.playlist else R.drawable.musical_notes))
            setBrowsable(item.mediaMetadata.isBrowsable == true)
            setOnClickListener(click)
            if (options != null) addAction(action(context, R.drawable.ellipsis_horizontal, options))
        }.build()
    fun section(context: CarContext, title: String, items: List<MediaItem>, grid: Boolean, click: (MediaItem) -> Unit, options: (MediaItem) -> Unit): Section<*> =
        if (grid) GridSection.Builder().setTitle(title).setItems(items.map { item ->
            GridItem.Builder().setTitle(item.mediaMetadata.title?.toString()?.take(100).orEmpty().ifBlank { "ViMusic" })
                .setImage(icon(context, if (item.mediaMetadata.isBrowsable == true) R.drawable.playlist else R.drawable.musical_notes))
                .setOnClickListener { click(item) }.build()
        }).build() else RowSection.Builder().setTitle(title).setItems(items.map { item ->
            row(context, item, { click(item) }, if (item.mediaMetadata.isPlayable == true) ({ options(item) }) else null)
        }).build()
}
