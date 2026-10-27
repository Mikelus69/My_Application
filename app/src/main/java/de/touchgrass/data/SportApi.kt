package de.touchgrass.data

import de.touchgrass.data.dto.AchievementDto
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.AuthResponse
import de.touchgrass.data.dto.ChangePasswordRequest
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.CreateActivityResponse
import de.touchgrass.data.dto.HealthDto
import de.touchgrass.data.dto.LeaderboardDto
import de.touchgrass.data.dto.LeaderboardMetric
import de.touchgrass.data.dto.LeaderboardPeriod
import de.touchgrass.data.dto.LoginRequest
import de.touchgrass.data.dto.ProgressDto
import de.touchgrass.data.dto.RegisterRequest
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.StatsDto
import de.touchgrass.data.dto.UpdateMeRequest
import de.touchgrass.data.dto.UserDto
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
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
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

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

    @GET("sports")
    suspend fun sports(): List<SportDto>

    @POST("activities")
    suspend fun createActivity(@Body body: CreateActivityRequest): CreateActivityResponse

    @GET("activities")
    suspend fun activities(
        @Query("sport") sport: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): List<ActivityDto>

    @GET("activities/{id}")
    suspend fun activity(@Path("id") id: Long): ActivityDto

    @DELETE("activities/{id}")
    suspend fun deleteActivity(@Path("id") id: Long)

    @GET("map/activities")
    suspend fun mapActivities(): List<ActivityDto>

    @GET("achievements")
    suspend fun achievements(): List<AchievementDto>

    @GET("progress")
    suspend fun progress(): ProgressDto

    @GET("leaderboard")
    suspend fun leaderboard(
        @Query("period") period: LeaderboardPeriod,
        @Query("metric") metric: LeaderboardMetric,
    ): LeaderboardDto

    @GET("stats")
    suspend fun stats(): StatsDto
}

val apiJson = Json { ignoreUnknownKeys = true }

fun createSportApi(baseUrl: String, client: OkHttpClient = OkHttpClient()): SportApi = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(SportApi::class.java)

// Ein schlafender Server bei Render braucht etwa eine Minute bis zur ersten Antwort.
fun httpClient(vararg interceptors: Interceptor): OkHttpClient = OkHttpClient.Builder()
    .readTimeout(2, TimeUnit.MINUTES)
    .apply { interceptors.forEach { addInterceptor(it) } }
    .build()
