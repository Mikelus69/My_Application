package de.touchgrass.data

import de.touchgrass.data.dto.AuthResponse
import de.touchgrass.data.dto.ChangePasswordRequest
import de.touchgrass.data.dto.HealthDto
import de.touchgrass.data.dto.LoginRequest
import de.touchgrass.data.dto.RegisterRequest
import de.touchgrass.data.dto.UpdateMeRequest
import de.touchgrass.data.dto.UserDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT

interface SportApi {
    @GET("health")
    suspend fun health(): HealthDto

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("me")
    suspend fun me(): UserDto

    @PATCH("me")
    suspend fun updateMe(@Body body: UpdateMeRequest): UserDto

    @PUT("me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @DELETE("me")
    suspend fun deleteMe()
}

val apiJson = Json { ignoreUnknownKeys = true }

fun createSportApi(baseUrl: String, client: OkHttpClient = OkHttpClient()): SportApi = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(SportApi::class.java)
