package de.touchgrass.users

import de.touchgrass.auth.JwtService
import de.touchgrass.auth.PasswordHasher
import de.touchgrass.plugins.ApiException
import io.ktor.http.HttpStatusCode

class UserService(private val users: UserRepository, private val jwtService: JwtService) {
    fun register(request: RegisterRequest): AuthResponse {
        val username = request.username.trim()
        val email = request.email.trim().lowercase()
        UserValidation.username(username)
        UserValidation.email(email)
        UserValidation.password(request.password)
        if (users.usernameTaken(username)) {
            throw ApiException(HttpStatusCode.Conflict, "USERNAME_TAKEN", "Dieser Benutzername ist schon vergeben.", "username")
        }
        if (users.emailTaken(email)) {
            throw ApiException(HttpStatusCode.Conflict, "EMAIL_TAKEN", "Diese E-Mail ist schon registriert.", "email")
        }
        val user = users.create(username, email, PasswordHasher.hash(request.password))
        return AuthResponse(jwtService.createToken(user.id), user.toDto())
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = users.findByLogin(request.login)
        if (user == null || !PasswordHasher.verify(request.password, user.passwordHash)) {
            throw ApiException(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS", "Benutzername/E-Mail oder Passwort ist falsch.")
        }
        return AuthResponse(jwtService.createToken(user.id), user.toDto())
    }

    private fun UserRecord.toDto() = UserDto(id, username, email, weightKg, goalType, goalTarget)
}
