package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery // Замени на свой импорт, если нужно
import com.teamfinder.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import java.time.LocalDateTime

class UserRepository {

    // Вспомогательная функция (Extension) для маппинга строки из БД в DTO
    private fun ResultRow.toUserDTO(): UserDTO = UserDTO(
        userId = this[Users.userId],
        email = this[Users.email],
        username = this[Users.username],
        firstName = this[Users.firstName],
        lastName = this[Users.lastName],
        avatarUrl = this[Users.avatarUrl],
        skills = this[Users.skills],
        interests = this[Users.interests],
        goals = this[Users.goals],
        rating = this[Users.rating].toDouble(),
        portfolioUrl = this[Users.portfolioUrl]
    )

    suspend fun createUser(request: RegisterUserRequest, passHash: String): UserDTO? = dbQuery {
        val insertStatement = Users.insert {
            it[email] = request.email
            it[passwordHash] = passHash
            it[username] = request.username
            it[firstName] = request.firstName
            it[lastName] = request.lastName
            it[createdAt] = LocalDateTime.now()
        }
        insertStatement.resultedValues?.singleOrNull()?.toUserDTO()
    }

    suspend fun findUserByEmail(email: String): ResultRow? = dbQuery {
        Users.select { Users.email eq email }.singleOrNull()
    }

    suspend fun findUserByUsername(username: String): ResultRow? = dbQuery {
        Users.select { Users.username eq username }.singleOrNull()
    }

    suspend fun findUserById(id: Int): UserDTO? = dbQuery {
        Users.select { Users.userId eq id }.singleOrNull()?.toUserDTO()
    }

    suspend fun updateUserProfile(userId: Int, request: UpdateProfileRequest): Boolean = dbQuery {
        val updatedRows = Users.update({ Users.userId eq userId }) {
            request.firstName?.let { name -> it[firstName] = name }
            request.lastName?.let { lastName -> it[lastName] = lastName }
            request.avatarUrl?.let { url -> it[avatarUrl] = url }
            request.skills?.let { skills -> it[this.skills] = skills }
            request.interests?.let { interests -> it[this.interests] = interests }
            request.goals?.let { goals -> it[this.goals] = goals }
            request.portfolioUrl?.let { url -> it[portfolioUrl] = url }
        }
        updatedRows > 0
    }

    // Ищем ID пользователя по привязанной соцсети
    suspend fun findUserIdByProvider(provider: String, providerId: String): Int? = dbQuery {
        UserAuths.select { 
            (UserAuths.provider eq provider) and (UserAuths.providerId eq providerId) 
        }.singleOrNull()?.get(UserAuths.userId)
    }

    suspend fun updateAvatarUrl(userId: Int, avatarUrl: String): Boolean = dbQuery {
        Users.update({ Users.userId eq userId }) {
            it[Users.avatarUrl] = avatarUrl
        } > 0
    }

    // Создаем пользователя и сразу привязываем его к соцсети в одной транзакции
    suspend fun createSocialUser(
        provider: String, 
        providerId: String, 
        username: String, 
        firstName: String, 
        lastName: String?, 
        avatarUrl: String?
    ): UserDTO? = dbQuery {
        // 1. Создаем запись в Users
        val insertUser = Users.insert {
            it[this.username] = username
            it[this.firstName] = firstName
            it[this.lastName] = lastName
            it[this.avatarUrl] = avatarUrl
            it[this.createdAt] = java.time.LocalDateTime.now()
        }
        
        val newUserId = insertUser.resultedValues?.singleOrNull()?.get(Users.userId) 
            ?: return@dbQuery null
            
        // 2. Создаем запись в UserAuths
        UserAuths.insert {
            it[this.userId] = newUserId
            it[this.provider] = provider
            it[this.providerId] = providerId
        }
        
        insertUser.resultedValues?.singleOrNull()?.toUserDTO()
    }
}