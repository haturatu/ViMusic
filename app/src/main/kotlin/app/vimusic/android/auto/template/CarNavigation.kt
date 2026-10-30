package app.vimusic.android.auto.template

import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import app.vimusic.android.R

/** Keep browsing below the host's terminal fifth step. */
internal object CarNavigation {
    private const val MAX_BROWSE_DEPTH = 4

    fun push(from: Screen, destination: Screen, client: CarMediaClient) {
        val manager = from.screenManager
        if (manager.top !== from) return
        if (manager.stackSize >= MAX_BROWSE_DEPTH && !(manager.stackSize == MAX_BROWSE_DEPTH && destination is CarPagerScreen)) {
            if (manager.stackSize == MAX_BROWSE_DEPTH) manager.push(CarBrowseLimitScreen(from, client))
            else CarToast.makeText(from.carContext, R.string.auto_navigation_limit, CarToast.LENGTH_SHORT).show()
            return
        }
        android.util.Log.d("ViMusicCar", "Navigate ${from.javaClass.simpleName} -> ${destination.javaClass.simpleName}, depth=${manager.stackSize + 1}")
        manager.push(destination)
    }

    fun playback(from: Screen, client: CarMediaClient) {
        val manager = from.screenManager
        if (manager.top !== from) return
        if (manager.top is CarPlaybackScreen) return
        val root = manager.screenStack.last()
        val destination = CarPlaybackScreen(from.carContext, client)
        if (!client.nativePlaybackAvailable && manager.stackSize >= MAX_BROWSE_DEPTH) {
            push(from, destination, client)
            return
        }
        // Playback completes the browse task. Retain Home as its back destination,
        // rather than carrying every selector into the queue/search task.
        manager.push(destination)
        manager.screenStack.filter { it !== root && it !== destination }.forEach(manager::remove)
    }

    fun search(from: Screen, client: CarMediaClient) {
        val root = from.screenManager.screenStack.last() as? CarHomeScreen ?: return
        root.startFlow { CarSearchScreen(from.carContext, client) }
    }

    fun page(from: Screen, client: CarMediaClient, current: Int, count: Int, select: (Int) -> Unit) {
        push(from, CarPagerScreen(from, current, count, select), client)
    }
}

/** Return to the same browse template ID, so page changes do not exhaust task steps. */
private class CarPagerScreen(from: Screen, private val current: Int, private val count: Int, private val select: (Int) -> Unit) : Screen(from.carContext) {
    override fun onGetTemplate(): Template = MessageTemplate.Builder(carContext.getString(R.string.auto_page, current + 1, count))
        .setHeader(CarUi.header(this, carContext.getString(R.string.auto_pages)))
        .addAction(Action.Builder().setTitle(carContext.getString(R.string.auto_previous)).setEnabled(current > 0)
            .setOnClickListener { select(current - 1); screenManager.pop() }.build())
        .addAction(Action.Builder().setTitle(carContext.getString(R.string.auto_next)).setEnabled(current + 1 < count)
            .setOnClickListener { select(current + 1); screenManager.pop() }.build())
        .build()
}

private class CarBrowseLimitScreen(from: Screen, private val client: CarMediaClient) : Screen(from.carContext) {
    override fun onGetTemplate(): Template = MessageTemplate.Builder(carContext.getString(R.string.auto_navigation_limit))
        .setHeader(CarUi.header(this, "ViMusic"))
        .addAction(Action.Builder().setTitle(carContext.getString(R.string.auto_back)).setOnClickListener { screenManager.pop() }.build())
        .addAction(Action.Builder().setTitle(carContext.getString(R.string.auto_search)).setOnClickListener { CarNavigation.search(this, client) }.build())
        .build()
}
