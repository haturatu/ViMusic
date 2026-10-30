package app.vimusic.android.auto.template

import androidx.car.app.CarContext
import app.vimusic.android.R
import app.vimusic.android.auto.AutoMediaId

internal class CarDiscoverScreen(context: CarContext, client: CarMediaClient) :
    CarLibraryScreen(context, client, AutoMediaId.DISCOVER, context.getString(R.string.discover))
