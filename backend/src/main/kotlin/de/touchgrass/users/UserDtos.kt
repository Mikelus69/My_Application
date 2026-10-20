package de.touchgrass.users

import kotlinx.serialization.Serializable

@Serializable
enum class GoalType { MINUTES, ACTIVITIES, KCAL }

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    val email: String,
    val weightKg: Double? = null,
    val goalType: GoalType,
    val goalTarget: Int,
)

@Serializable
data class RegisterRequest(val username: String, val email: String, val password: String)

@Serializable
data class LoginRequest(val login: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val user: UserDto)

@Serializable
data class UpdateMeRequest(val weightKg: Double? = null, val goalType: GoalType? = null, val goalTarget: Int? = null)

@Serializable
data class ChangePasswordRequest(val oldPassword: String, val newPassword: String)
