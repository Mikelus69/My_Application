package de.touchgrass.ui.photo

import java.io.File

const val UNSUPPORTED_PHOTO = "Dieses Bildformat wird nicht unterstützt."

class PhotoException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun interface PhotoPreparer {
    suspend fun prepare(source: String): File
}
