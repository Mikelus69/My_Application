package de.touchgrass.testing

import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerState
import de.touchgrass.data.TimerStore
import de.touchgrass.data.TokenStore
import de.touchgrass.data.dto.AchievementDto
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.AuthResponse
import de.touchgrass.data.dto.ChangePasswordRequest
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.CreateActivityResponse
import de.touchgrass.data.dto.GoalType
import de.touchgrass.data.dto.HealthDto
import de.touchgrass.data.dto.LeaderboardDto
import de.touchgrass.data.dto.LeaderboardMetric
import de.touchgrass.data.dto.LeaderboardPeriod
import de.touchgrass.data.dto.LoginRequest
import de.touchgrass.data.dto.ProgressDto
import de.touchgrass.data.dto.RegisterRequest
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.UpdateMeRequest
import de.touchgrass.data.dto.UserDto
import de.touchgrass.data.dto.Visibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import retrofit2.HttpException
import retrofit2.Response

open class FakeSportApi : SportApi {
    override suspend fun health(): HealthDto = notExpected("health")
    override suspend fun register(body: RegisterRequest): AuthResponse = notExpected("register")
    override suspend fun login(body: LoginRequest): AuthResponse = notExpected("login")
    override suspend fun me(): UserDto = notExpected("me")
    override suspend fun updateMe(body: UpdateMeRequest): UserDto = notExpected("updateMe")
    override suspend fun changePassword(body: ChangePasswordRequest): Unit = notExpected("changePassword")
    override suspend fun deleteMe(): Unit = notExpected("deleteMe")
    override suspend fun sports(): List<SportDto> = notExpected("sports")
    override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse = notExpected("createActivity")
    override suspend fun activities(sport: String?, from: String?, to: String?): List<ActivityDto> = notExpected("activities")
    override suspend fun activity(id: Long): ActivityDto = notExpected("activity")
    override suspend fun deleteActivity(id: Long): Unit = notExpected("deleteActivity")
    override suspend fun mapActivities(): List<ActivityDto> = notExpected("mapActivities")
    override suspend fun achievements(): List<AchievementDto> = notExpected("achievements")
    override suspend fun progress(): ProgressDto = notExpected("progress")
    override suspend fun leaderboard(period: LeaderboardPeriod, metric: LeaderboardMetric): LeaderboardDto = notExpected("leaderboard")

    private fun notExpected(name: String): Nothing = error("$name() wird in diesem Test nicht erwartet")
}

class FakeTokenStore(initial: String? = null) : TokenStore {
    private val current = MutableStateFlow(initial)
    override val token: Flow<String?> = current
    override suspend fun save(token: String) { current.value = token }
    override suspend fun clear() { current.value = null }
}

class FakeTimerStore(initial: TimerState? = null) : TimerStore {
    val current = MutableStateFlow(initial)
    override val state: Flow<TimerState?> = current
    override suspend fun save(state: TimerState) { current.value = state }
    override suspend fun clear() { current.value = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun httpError(code: Int, json: String): HttpException =
    HttpException(Response.error<Any>(code, json.toResponseBody("application/json".toMediaType())))

val testUser = UserDto(
    id = 1,
    username = "max_m",
    email = "max@example.com",
    weightKg = null,
    goalType = GoalType.MINUTES,
    goalTarget = 150,
)

val testSports = listOf(
    SportDto("CYCLING", "Radfahren", "🚴", 7.0),
    SportDto("RUNNING", "Joggen", "🏃", 8.0),
)

fun testActivity(id: Long = 42, sportCode: String = "RUNNING", startedAt: String = "2026-10-05T09:30:00+02:00") = ActivityDto(
    id = id,
    username = "max_m",
    mine = true,
    sportCode = sportCode,
    startedAt = startedAt,
    durationMin = 45,
    caloriesKcal = 480,
    points = 75,
    visibility = Visibility.PRIVATE,
)

fun testAchievement(code: String = "FIRST_ACTIVITY", progress: Int = 1, threshold: Int = 1, unlockedAt: String? = "2026-10-05T10:18:00+02:00") =
    AchievementDto(
        code = code,
        name = "Erste Schritte",
        description = "Erste Aktivität eingetragen",
        emoji = "🥉",
        threshold = threshold,
        progress = progress,
        unlockedAt = unlockedAt,
    )
