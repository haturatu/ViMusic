package app.vimusic.android.auto.template

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import app.vimusic.android.BuildConfig

class ViMusicCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = if (BuildConfig.DEBUG) {
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    } else HostValidator.Builder(this).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()

    override fun onCreateSession(): Session = object : Session() {
        private val client by lazy { CarMediaClient(carContext, lifecycle) }
        override fun onCreateScreen(intent: Intent): Screen = CarHomeScreen(carContext, client)
    }
}
