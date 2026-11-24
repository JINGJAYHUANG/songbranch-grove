package dev.songbranch.grove.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import dev.songbranch.grove.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
