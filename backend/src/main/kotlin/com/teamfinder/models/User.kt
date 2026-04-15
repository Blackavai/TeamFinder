package com.teamfinder.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.math.BigDecimal
import java.time.LocalDateTime

@Serializable
data class User(
    val id: Int? = null,
    val email: String? = null,
    val passwordHash: String? = null,
    val username: String,
    val firstName: String,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    val skills: List<Skill> = emptyList(),
    val interests: List<String> = emptyList(),
    val goals: String? = null,
    val rating: BigDecimal? = null,
    val responseTimeAvg: Int? = null,
    val portfolioUrl: String? = null,
    @Contextual
    val createdAt: LocalDateTime? = null,
    @Contextual
    val lastActive: LocalDateTime? = null
)

@Serializable
data class Skill(
    val name: String,
    val level: String? = null,
    val category: String? = null
)

// Таблица Users
object Users : Table("users") {
    val id = integer("user_id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex().nullable()
    val passwordHash = varchar("password_hash", 255).nullable()
    val username = varchar("username", 50).uniqueIndex()
    val firstName = varchar("first_name", 100)
    val lastName = varchar("last_name", 100).nullable()
    val avatarUrl = text("avatar_url").nullable()
    val skills = text("skills").clientDefault { "[]" }
    val interests = text("interests").clientDefault { "[]" }
    val goals = text("goals").nullable()
    val rating = decimal("rating", 3, 2).default(BigDecimal.ZERO)
    val responseTimeAvg = integer("response_time_avg").nullable()
    val portfolioUrl = text("portfolio_url").nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val lastActive = datetime("last_active").nullable()

    override val primaryKey = PrimaryKey(id)
}
