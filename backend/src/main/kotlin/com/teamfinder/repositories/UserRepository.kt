package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.*
import com.teamfinder.utils.PasswordUtils
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

class UserRepository {

    suspend fun create(user: User, password: String): User? = dbQuery {
        val passwordHash = PasswordUtils.hash(password)

        val insert = Users.insert {
            it[Users.username] = user.username
            it[Users.email] = user.email
            it[Users.passwordHash] = passwordHash
            it[Users.fullName] = user.fullName
            it[Users.groupName] = user.groupName
            it[Users.avatarUrl] = user.avatarUrl
            it[Users.about] = user.about
            it[Users.role] = user.role
            it[Users.createdAt] = LocalDateTime.now()
            it[Users.updatedAt] = LocalDateTime.now()
        }

        insert.resultedValues?.first()?.let { toUser(it) }
    }

    suspend fun findByEmail(email: String): User? = dbQuery {
        Users.select { Users.email eq email }
            .map { row: ResultRow -> toUser(row) }
            .singleOrNull()
    }

    suspend fun findById(id: Int): User? = dbQuery {
        Users.select { Users.id eq id }
            .map { row: ResultRow -> toUser(row) }
            .singleOrNull()
    }

    suspend fun findByUsername(username: String): User? = dbQuery {
        Users.select { Users.username eq username }
            .map { row: ResultRow -> toUser(row) }
            .singleOrNull()
    }

    suspend fun update(id: Int, user: User): Boolean = dbQuery {
        Users.update({ Users.id eq id }) {
            it[Users.fullName] = user.fullName
            it[Users.groupName] = user.groupName
            it[Users.avatarUrl] = user.avatarUrl
            it[Users.about] = user.about
            it[Users.updatedAt] = LocalDateTime.now()
        } > 0
    }

    suspend fun updateLastLogin(id: Int): Boolean = dbQuery {
        Users.update({ Users.id eq id }) {
            it[Users.lastLogin] = LocalDateTime.now()
        } > 0
    }

    suspend fun delete(id: Int): Boolean = dbQuery {
        Users.deleteWhere { Users.id eq id } > 0
    }

    
    suspend fun getProfile(userId: Int): UserProfile? = dbQuery {
        Profiles.select { Profiles.userId eq userId }
            .map { row: ResultRow -> toProfile(row) }
            .singleOrNull()
    }

    // ИСПРАВЛЕННЫЙ МЕТОД createOrUpdateProfile
    suspend fun createOrUpdateProfile(profile: UserProfile): Boolean = dbQuery {
        val exists = Profiles.select { Profiles.userId eq profile.userId }.count() > 0

        val affectedRows = if (exists) {
            Profiles.update({ Profiles.userId eq profile.userId }) {
                it[Profiles.skills] = profile.skills.toString()
                it[Profiles.interests] = profile.interests.toString()
                it[Profiles.goals] = profile.goals
                it[Profiles.portfolioLink] = profile.portfolioLink
                it[Profiles.experienceYears] = profile.experienceYears?.toBigDecimal()
                it[Profiles.preferredRoles] = profile.preferredRoles.toString()
            }
        } else {
            Profiles.insert {
                it[Profiles.userId] = profile.userId
                it[Profiles.skills] = profile.skills.toString()
                it[Profiles.interests] = profile.interests.toString()
                it[Profiles.goals] = profile.goals
                it[Profiles.portfolioLink] = profile.portfolioLink
                it[Profiles.experienceYears] = profile.experienceYears?.toBigDecimal()
                it[Profiles.preferredRoles] = profile.preferredRoles.toString()
            }.insertedCount
        }
        
        affectedRows > 0
    }

    // ИСПРАВЛЕННЫЙ МЕТОД validateCredentials (с правильным отступом)
    suspend fun validateCredentials(email: String, password: String): User? = dbQuery {
        val user = Users.select { Users.email eq email }
            .map { toUser(it) }
            .singleOrNull()
        
        if (user != null && user.passwordHash != null) {
            val isValid = PasswordUtils.verify(password, user.passwordHash)
            if (isValid) {
                return@dbQuery user
            }
        }
        return@dbQuery null
    }

    private fun toUser(row: ResultRow): User = User(
        id = row[Users.id],
        username = row[Users.username],
        email = row[Users.email],
        passwordHash = row[Users.passwordHash],
        fullName = row[Users.fullName],
        groupName = row[Users.groupName],
        avatarUrl = row[Users.avatarUrl],
        about = row[Users.about],
        role = row[Users.role],
        isActive = row[Users.isActive],
        createdAt = row[Users.createdAt],
        updatedAt = row[Users.updatedAt],
        lastLogin = row[Users.lastLogin]
    )

    private fun toProfile(row: ResultRow): UserProfile = UserProfile(
        userId = row[Profiles.userId],
        skills = parseSkills(row[Profiles.skills]),
        interests = parseList(row[Profiles.interests]),
        goals = row[Profiles.goals],
        portfolioLink = row[Profiles.portfolioLink],
        experienceYears = row[Profiles.experienceYears]?.toDouble(),
        preferredRoles = parseList(row[Profiles.preferredRoles])
    )

    @Suppress("UNCHECKED_CAST")
    private fun parseSkills(json: String): List<Skill> {
        return try {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseList(json: String): List<String> {
        return try {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
// ========== НОВЫЕ МЕТОДЫ ДЛЯ ПАГИНАЦИИ ==========

// Подсчет общего количества пользователей
suspend fun count(): Int = dbQuery {
    Users.selectAll().count().toInt()
}

// Подсчет с учетом поискового запроса
suspend fun count(query: String): Int = dbQuery {
    if (query.isBlank()) {
        Users.selectAll().count().toInt()
    } else {
        Users.select {
            (Users.username like "%$query%") or
            (Users.fullName like "%$query%") or
            (Users.email like "%$query%")
        }.count().toInt()
    }
}

// Получить всех пользователей с пагинацией
suspend fun getAll(page: Int, limit: Int): List<User> = dbQuery {
    val offset = (page - 1) * limit
    
    Users.selectAll()
        .limit(limit, offset.toLong())
        .orderBy(Users.createdAt to SortOrder.DESC)
        .map { toUser(it) }
}

// Поиск с пагинацией (обновленная версия)
suspend fun search(query: String, page: Int, limit: Int): List<User> = dbQuery {
    val offset = (page - 1) * limit
    
    Users.select {
        (Users.username like "%$query%") or
        (Users.fullName like "%$query%") or
        (Users.email like "%$query%")
    }
    .limit(limit, offset.toLong())
    .orderBy(Users.username to SortOrder.ASC)
    .map { toUser(it) }
}
}