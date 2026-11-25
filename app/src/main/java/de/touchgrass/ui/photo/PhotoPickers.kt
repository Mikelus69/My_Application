package de.touchgrass.ui.photo

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.util.UUID

class PhotoPickers(val openGallery: () -> Unit, val openCamera: () -> Unit)

// Oben im Screen aufrufen, nie in Dialogen oder Bottom Sheets: Beendet Android die App, während Kamera oder Fotoauswahl
// offen sind, kommt das Ergebnis nur dort wieder an. Deshalb steht auch die Kamera-Uri schon vor dem Start in rememberSaveable.
@Composable
fun rememberPhotoPickers(onPicked: (String) -> Unit, onError: (String) -> Unit): PhotoPickers {
    val context = LocalContext.current
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri.toString())
    }
    val camera = rememberLauncherForActivityResult(TakePicture()) { saved ->
        val capture = pendingCapture
        pendingCapture = null
        if (capture != null) {
            if (saved) onPicked(capture) else context.contentResolver.delete(capture.toUri(), null, null)
        }
    }
    return PhotoPickers(
        openGallery = { gallery.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
        openCamera = {
            val dir = cameraDir(context).apply { mkdirs() }
            val uri = FileProvider.getUriForFile(context, PHOTO_AUTHORITY, File(dir, "${UUID.randomUUID()}.jpg"))
            pendingCapture = uri.toString()
            try {
                camera.launch(uri)
            } catch (e: ActivityNotFoundException) {
                pendingCapture = null
                onError("Keine Kamera-App gefunden.")
            }
        },
    )
}
