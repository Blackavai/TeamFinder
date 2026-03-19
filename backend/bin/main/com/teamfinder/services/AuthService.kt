package com.teamfinder.services

import com.teamfinder.models.User
import com.teamfinder.models.UserRole
import com.teamfinder.repositories.UserRepository
import com.teamfinder.security.JwtConfig
import java.time.LocalDateTime

/**
 * Сервис для работы с аутентификацией пользователей
 */
class AuthService(
    private val jwtConfig: JwtConfig,
    private val userRepository: UserRepository = UserRepository()
) {
    
    /**
     * Регистрация нового пользователя
     * 
     * @param username - имя пользователя
     * @param email - электронная почта
     * @param password - пароль
     * @return результат регистрации (успех или ошибка)
     */
    suspend fun register(username: String, email: String, password: String): AuthResult {
        // Проверяем, не занята ли электронная почта
        val existingUser = userRepository.findByEmail(email)
        if (existingUser != null) {
            return AuthResult.Error("Пользователь с таким email уже существует")
        }
        
        // Проверяем, не занято ли имя пользователя
        val existingUsername = userRepository.findByUsername(username)
        if (existingUsername != null) {
            return AuthResult.Error("Имя пользователя уже занято")
        }
        
        // Проверяем сложность пароля
        if (!isPasswordValid(password)) {
            return AuthResult.Error("Пароль должен содержать минимум 8 символов, включая буквы и цифры")
        }
        
        // Создаём нового пользователя
        val user = User(
            username = username,
            email = email,
            role = UserRole.USER,
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
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
    
    /**
     * Вход пользователя в систему
     * 
     * @param email - электронная почта
     * @param password - пароль
     * @return результат входа (успех или ошибка)
     */
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
    
    /**
     * Обновление токена доступа
     * 
     * @param refreshToken - токен обновления
     * @return новые токены или ошибку
     */
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
    
    /**
     * Проверка сложности пароля
     * 
     * @param password - пароль для проверки
     * @return true если пароль соответствует требованиям
     */
    private fun isPasswordValid(password: String): Boolean {
        return password.length >= 8 && 
               password.any { it.isLetter() } && 
               password.any { it.isDigit() }
    }
}

/**
 * Результат операции аутентификации
 */
sealed class AuthResult {
    /**
     * Успешная аутентификация с токенами и данными пользователя
     */
    data class Success(
        val accessToken: String,   // Токен доступа
        val refreshToken: String,  // Токен обновления
        val user: User             // Данные пользователя
    ) : AuthResult()
    
    /**
     * Ошибка аутентификации с сообщением
     */
    data class Error(val message: String) : AuthResult()
}