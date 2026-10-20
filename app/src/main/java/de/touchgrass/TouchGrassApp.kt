package de.touchgrass

import android.app.Application

class TouchGrassApp : Application() {
    val container by lazy { AppContainer(BuildConfig.API_BASE_URL) }
}
