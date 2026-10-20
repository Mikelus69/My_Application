package de.touchgrass.testing

import de.touchgrass.data.SportApi
import de.touchgrass.data.TokenStore
import de.touchgrass.data.dto.AuthResponse
import de.touchgrass.data.dto.ChangePasswordRequest
import de.touchgrass.data.dto.GoalType
import de.touchgrass.data.dto.HealthDto
import de.touchgrass.data.dto.LoginRequest
import de.touchgrass.data.dto.RegisterRequest
import de.touchgrass.data.dto.UpdateMeRequest
import de.touchgrass.data.dto.UserDto
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

    private fun notExpected(name: String): Nothing = error("$name() wird in diesem Test nicht erwartet")
}

class FakeTokenStore(initial: String? = null) : TokenStore {
    private val current = MutableStateFlow(initial)
    override val token: Flow<String?> = current
    override suspend fun save(token: String) { current.value = token }
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
