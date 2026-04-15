package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.*
import com.teamfinder.utils.PasswordUtils
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.math.BigDecimal
import java.time.LocalDateTime

class UserRepository {

    // ========== AUTH ==========

    suspend fun create(user: User, password: String): User? = dbQuery {
        val passwordHash = PasswordUtils.hash(password)

        val insert = Users.insert {
            it[username] = user.username
            it[email] = user.email
            it[passwordHash] = passwordHash
            it[firstName] = user.firstName
            user.lastName?.let { ln -> it[lastName] = ln }
            it[avatarUrl] = user.avatarUrl
            it[skills] = Json.encodeToString(ListSerializer(SkillSerializer()), user.skills)
            it[interests] = Json.encodeToString(ListSerializer(String.serializer()), user.interests)
            it[goals] = user.goals
            it[rating] = user.rating ?: BigDecimal.ZERO
            it[createdAt] = LocalDateTime.now()
        }

        insert.resultedValues?.first()?.let { toUser(it) }
    }

    suspend fun findByEmail(email: String): User? = dbQuery {
        Users.select { Users.email eq email }
            .map { toUser(it) }
            .singleOrNull()
    }

    suspend fun findById(id: Int): User? = dbQuery {
        Users.select { Users.id eq id }
            .map { toUser(it) }
            .singleOrNull()
    }

    suspend fun findByUsername(username: String): User? = dbQuery {
        Users.select { Users.username eq username }
            .map { toUser(it) }
            .singleOrNull()
    }

    suspend fun validateCredentials(email: String, password: String): User? = dbQuery {
        val user = Users.select { Users.email eq email }
            .map { toUser(it) }
            .singleOrNull()

        if (user != null && user.passwordHash != null && PasswordUtils.verify(password, user.passwordHash)) {
            return@dbQuery user
        }
        null
    }

    suspend fun updateLastLogin(id: Int): Boolean = dbQuery {
        Users.update({ Users.id eq id }) {
            it[lastActive] = LocalDateTime.now()
        } > 0
    }

    // ========== PROFILE ==========

    suspend fun update(id: Int, user: User): Boolean = dbQuery {
        Users.update({ Users.id eq id }) {
            it[firstName] = user.firstName
            user.lastName?.let { ln -> it[lastName] = ln }
            it[avatarUrl] = user.avatarUrl
            it[skills] = Json.encodeToString(ListSerializer(SkillSerializer()), user.skills)
            it[interests] = Json.encodeToString(ListSerializer(String.serializer()), user.interests)
            it[goals] = user.goals
            user.portfolioUrl?.let { p -> it[portfolioUrl] = p }
        } > 0
    }

    suspend fun delete(id: Int): Boolean = dbQuery {
        Users.deleteWhere { Users.id eq id } > 0
    }

    // ========== PAGINATION & SEARCH ==========

    suspend fun count(): Int = dbQuery {
        Users.selectAll().count().toInt()
    }

    suspend fun count(query: String): Int = dbQuery {
        if (query.isBlank()) {
            Users.selectAll().count().toInt()
        } else {
            Users.select {
                (Users.username like "%$query%") or
                (Users.firstName like "%$query%") or
                (Users.lastName like "%$query%") or
                (Users.email like "%$query%")
            }.count().toInt()
        }
    }

    suspend fun getAll(page: Int, limit: Int): List<User> = dbQuery {
        val offset = (page - 1) * limit

        Users.selectAll()
            .limit(limit, offset.toLong())
            .orderBy(Users.createdAt to SortOrder.DESC)
            .map { toUser(it) }
    }

    suspend fun search(query: String, page: Int, limit: Int): List<User> = dbQuery {
        val offset = (page - 1) * limit

        Users.select {
            (Users.username like "%$query%") or
            (Users.firstName like "%$query%") or
            (Users.lastName like "%$query%")
        }
        .limit(limit, offset.toLong())
        .orderBy(Users.username to SortOrder.ASC)
        .map { toUser(it) }
    }

    // ========== SKILLS SEARCH ==========

    suspend fun searchBySkills(skills: List<String>, page: Int, limit: Int): List<User> = dbQuery {
        val offset = (page - 1) * limit

        // Ищем пользователей, у которых в JSONB skills есть хотя бы один из запрошенных
        Users.selectAll()
            .limit(limit, offset.toLong())
            .map { toUser(it) }
            .filter { user ->
                user.skills.any { it.name in skills }
            }
    }

    // ========== MAPPERS ==========

    private fun toUser(row: ResultRow): User = User(
        id = row[Users.id],
        email = row[Users.email],
        passwordHash = row[Users.passwordHash],
        username = row[Users.username],
        firstName = row[Users.firstName],
        lastName = row[Users.lastName],
        avatarUrl = row[Users.avatarUrl],
        skills = parseSkills(row[Users.skills]),
        interests = parseStringList(row[Users.interests]),
        goals = row[Users.goals],
        rating = row[Users.rating],
        responseTimeAvg = row[Users.responseTimeAvg],
        portfolioUrl = row[Users.portfolioUrl],
        createdAt = row[Users.createdAt],
        lastActive = row[Users.lastActive]
    )

    private fun parseSkills(json: String): List<Skill> {
        return try {
            Json.decodeFromString<List<Skill>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseStringList(json: String): List<String> {
        return try {
            Json.decodeFromString<List<String>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
