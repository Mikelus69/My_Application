package de.touchgrass.ui.photo

import androidx.core.content.FileProvider
import de.touchgrass.BuildConfig

const val PHOTO_AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileprovider"

class PhotoFileProvider : FileProvider()
