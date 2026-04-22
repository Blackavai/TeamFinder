package com.teamfinder.services

import com.teamfinder.models.*
import com.teamfinder.repositories.UserRepository
import com.teamfinder.security.JwtConfig // Твой класс генерации JWT
import com.teamfinder.utils.PasswordUtils // Твой утилитный класс

class AuthService(
    private val userRepository: UserRepository,
    private val jwtConfig: JwtConfig
) {
    suspend fun register(request: RegisterUserRequest): String {
        // Проверяем, свободен ли email и username
        if (userRepository.findUserByEmail(request.email) != null) {
            throw IllegalArgumentException("Email уже зарегистрирован")
        }
        if (userRepository.findUserByUsername(request.username) != null) {
            throw IllegalArgumentException("Имя пользователя уже занято")
        }

        val hashedPassword = PasswordUtils.hash(request.password)
        val user = userRepository.createUser(request, hashedPassword)
            ?: throw Exception("Не удалось создать пользователя")

        // Возвращаем JWT токен
        return jwtConfig.generateToken(user.userId.toString())
    }

    suspend fun login(request: LoginRequest): String {
        val userRow = userRepository.findUserByEmail(request.email)
            ?: throw IllegalArgumentException("Неверный email или пароль")

        val storedHash = userRow[Users.passwordHash]
            ?: throw IllegalArgumentException("Неверный способ авторизации")

        if (!PasswordUtils.verify(request.password, storedHash)) {
            throw IllegalArgumentException("Неверный email или пароль")
        }

        val userId = userRow[Users.userId]
        return jwtConfig.generateToken(userId.toString())
    }

    suspend fun telegramAuth(request: TelegramAuthRequest): String {
         // 1. Собираем все поля реквеста (кроме hash) в мапу
        val dataMap = mutableMapOf<String, String>().apply {
            put("id", request.id)
            put("first_name", request.first_name)
            put("auth_date", request.auth_date.toString())
            // Добавляем опциональные поля только если они не null
            request.last_name?.let { put("last_name", it) }
            request.username?.let { put("username", it) }
            request.photo_url?.let { put("photo_url", it) }
        }

        // 2. Проверяем подпись
        if (!TelegramAuthValidator.verify(dataMap, request.hash)) {
            throw IllegalArgumentException("Fake Telegram data! Неверная подпись.")
        }

        // ВАЖНО: В продакшене здесь должна быть функция проверки request.hash 
        // с использованием токена твоего Telegram-бота. 
        // Если проверка не пройдена -> throw Exception("Fake Telegram data!")

        val provider = "telegram"
        val providerId = request.id

        // 1. Проверяем, есть ли уже такой юзер
        val existingUserId = userRepository.findUserIdByProvider(provider, providerId)
        if (existingUserId != null) {
            // Если есть, просто логиним его и отдаем токен
            return jwtConfig.generateToken(existingUserId.toString())
        }

        // 2. Если юзера нет, будем регистрировать.
        // Юзернейм в базе уникален и обязателен. Если у него в ТГ нет юзернейма, генерируем его:
        val baseUsername = request.username ?: "tg_${request.id}"
        
        // Проверяем, не занят ли username (вдруг совпал)
        var finalUsername = baseUsername
        var counter = 1
        while (userRepository.findUserByUsername(finalUsername) != null) {
            finalUsername = "${baseUsername}_$counter"
            counter++
        }

        // 3. Сохраняем в БД
        val newUser = userRepository.createSocialUser(
            provider = provider,
            providerId = providerId,
            username = finalUsername,
            firstName = request.first_name,
            lastName = request.last_name,
            avatarUrl = request.photo_url
        ) ?: throw Exception("Не удалось создать пользователя через Telegram")

        return jwtConfig.generateToken(newUser.userId.toString())
    }
}