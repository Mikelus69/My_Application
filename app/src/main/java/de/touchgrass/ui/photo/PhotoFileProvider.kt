package de.touchgrass.ui.photo

import android.content.Context
import androidx.core.content.FileProvider
import de.touchgrass.BuildConfig
import java.io.File

const val PHOTO_AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileprovider"

// Muss zu <cache-path name="camera" path="camera/" /> in res/xml/file_paths.xml passen.
fun cameraDir(context: Context): File = File(context.cacheDir, "camera")

class PhotoFileProvider : FileProvider()
