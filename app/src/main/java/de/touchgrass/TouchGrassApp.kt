package de.touchgrass

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

class TouchGrassApp : Application(), SingletonImageLoader.Factory {
    val container by lazy { AppContainer(this, BuildConfig.API_BASE_URL) }

    override fun newImageLoader(context: PlatformContext): ImageLoader = container.imageLoader
}
