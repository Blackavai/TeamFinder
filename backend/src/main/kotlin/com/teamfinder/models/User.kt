package com.teamfinder.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb

// ============================================
// EXPOSED TABLES (Схема БД)
// ============================================

object Users : Table("users") {
    val userId = integer("user_id").autoIncrement()
    val email = varchar("email", 255).nullable().uniqueIndex()
    val passwordHash = varchar("password_hash", 255).nullable()
    
    val username = varchar("username", 50).uniqueIndex()
    val firstName = varchar("first_name", 100)
    val lastName = varchar("last_name", 100).nullable()
    val avatarUrl = text("avatar_url").nullable()
    
    // Используем jsonb для хранения массивов
    val skills = jsonb<List<String>>("skills", Json.Default).default(emptyList())
    val interests = jsonb<List<String>>("interests", Json.Default).default(emptyList())
    val goals = text("goals").nullable()
    
    val rating = decimal("rating", 3, 2).default(java.math.BigDecimal("0.00"))
    val responseTimeAvg = integer("response_time_avg").nullable()
    val portfolioUrl = text("portfolio_url").nullable()
    
    val createdAt = datetime("created_at")
    val lastActive = datetime("last_active").nullable()

    override val primaryKey = PrimaryKey(userId)
}

object UserAuths : Table("user_auth") {
    val authId = integer("auth_id").autoIncrement()
    val userId = reference("user_id", Users.userId)
    val provider = varchar("provider", 20) // 'telegram', etc.
    val providerId = varchar("provider_id", 255)

    override val primaryKey = PrimaryKey(authId)
    
    init {
        uniqueIndex(provider, providerId)
    }
}

// ============================================
// DATA CLASSES (Модели для отдачи клиенту / получения запросов)
// ============================================

@Serializable
data class UserDTO(
    val userId: Int,
    val email: String?,
    val username: String,
    val firstName: String,
    val lastName: String?,
    val avatarUrl: String?,
    val skills: List<String>,
    val interests: List<String>,
    val goals: String?,
    val rating: Double,
    val portfolioUrl: String?
)

@Serializable
data class RegisterUserRequest(
    val email: String,
    val password: String,
    val username: String,
    val firstName: String,
    val lastName: String? = null
)

@Serializable
data class UpdateProfileRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    val skills: List<String>? = null,
    val interests: List<String>? = null,
    val goals: String? = null,
    val portfolioUrl: String? = null
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class TelegramAuthRequest(
    val id: String,          // ID пользователя в Telegram
    val first_name: String,
    val last_name: String? = null,
    val username: String? = null,
    val photo_url: String? = null,
    val auth_date: Long,
    val hash: String         // Хэш для проверки подлинности
)