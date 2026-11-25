package de.touchgrass.data

import de.touchgrass.data.dto.ActivityDto
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class PendingPhotos(private val dir: File) {
    private val uploading = mutableSetOf<Long>()

    fun file(activityId: Long): File = File(dir, "$activityId.jpg")

    fun has(activityId: Long): Boolean = activityId !in uploading && file(activityId).isFile

    fun remove(activityId: Long) {
        file(activityId).delete()
    }

    // Das Foto liegt schon vor dem ersten Versuch unter seiner Aktivität und übersteht so auch einen Abbruch oder das Beenden der App.
    // Es bleibt nur nach Netz- und Serverfehlern. Bei jedem anderen Fehler hat der Server das Foto schon angenommen (BAD_RESPONSE) oder lehnt es auch beim nächsten Versuch ab.
    suspend fun upload(api: SportApi, activityId: Long, photo: File): ActivityDto {
        val waiting = file(activityId)
        uploading += activityId
        try {
            val activity = apiCall {
                if (photo != waiting) {
                    dir.mkdirs()
                    Files.move(photo.toPath(), waiting.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                api.uploadPhotoFile(activityId, waiting)
            }
            waiting.delete()
            return activity
        } catch (e: ApiException) {
            if (!e.temporary) waiting.delete()
            throw e
        } finally {
            uploading -= activityId
        }
    }

    private val ApiException.temporary: Boolean
        get() = code == "NETWORK" || code == "INTERNAL_ERROR" || code.startsWith("HTTP_5")
}
