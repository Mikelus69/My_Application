package de.touchgrass.data

import de.touchgrass.BuildConfig
import de.touchgrass.data.dto.ActivityDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

suspend fun SportApi.uploadPhotoFile(id: Long, file: File): ActivityDto =
    uploadPhoto(id, file.asRequestBody("image/jpeg".toMediaType()))

fun ActivityDto.photoUrl(baseUrl: String = BuildConfig.API_BASE_URL): String? =
    photoVersion?.let { "${baseUrl}activities/$id/photo?v=$it" }
