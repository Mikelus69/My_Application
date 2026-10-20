package de.touchgrass.data

import de.touchgrass.data.dto.HealthDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET

interface SportApi {
    @GET("health")
    suspend fun health(): HealthDto
}

private val json = Json { ignoreUnknownKeys = true }

fun createSportApi(baseUrl: String): SportApi = Retrofit.Builder()
    .baseUrl(baseUrl)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(SportApi::class.java)
