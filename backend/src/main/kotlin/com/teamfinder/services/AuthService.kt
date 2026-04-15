package com.teamfinder.services

import com.teamfinder.models.User
import com.teamfinder.repositories.UserRepository
import com.teamfinder.security.JwtConfig
import java.time.LocalDateTime

class AuthService(
    private val jwtConfig: JwtConfig,
    private val userRepository: UserRepository = UserRepository()
) {

    suspend fun register(username: String, email: String, password: String, firstName: String): AuthResult {
        // Проверяем email
        userRepository.findByEmail(email)?.let {
            return AuthResult.Error("Пользователь с таким email уже существует")
        }

        // Проверяем username
        userRepository.findByUsername(username)?.let {
            return AuthResult.Error("Имя пользователя уже занято")
        }

        // Проверяем пароль
        if (!isPasswordValid(password)) {
            return AuthResult.Error("Пароль должен содержать минимум 8 символов, включая буквы и цифры")
        }

        val user = User(
            username = username,
            email = email,
            firstName = firstName,
            createdAt = LocalDateTime.now()
        )

        val created = userRepository.create(user, password)

        return if (created != null) {
            AuthResult.Success(
                accessToken = jwtConfig.generateAccessToken(created),
                refreshToken = jwtConfig.generateRefreshToken(created.id!!),
                user = created
            )
        } else {
            AuthResult.Error("Не удалось создать пользователя")
        }
    }

    suspend fun login(email: String, password: String): AuthResult {
        val user = userRepository.validateCredentials(email, password)

        return if (user != null) {
            userRepository.updateLastLogin(user.id!!)
            AuthResult.Success(
                accessToken = jwtConfig.generateAccessToken(user),
                refreshToken = jwtConfig.generateRefreshToken(user.id),
                user = user
            )
        } else {
            AuthResult.Error("Неверный email или пароль")
        }
    }

    suspend fun refreshToken(refreshToken: String): AuthResult {
        val userId = jwtConfig.extractUserId(refreshToken)
            ?: return AuthResult.Error("Недействительный токен обновления")

        val user = userRepository.findById(userId)
            ?: return AuthResult.Error("Пользователь не найден")

        return AuthResult.Success(
            accessToken = jwtConfig.generateAccessToken(user),
            refreshToken = jwtConfig.generateRefreshToken(userId),
            user = user
        )
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.length >= 8 && password.any { it.isLetter() } && password.any { it.isDigit() }
    }
}

sealed class AuthResult {
    data class Success(
        val accessToken: String,
        val refreshToken: String,
        val user: User
    ) : AuthResult()

    data class Error(val message: String) : AuthResult()
}
