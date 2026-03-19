package com.teamfinder.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import org.jetbrains.exposed.sql.Table
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.javatime.datetime

@Serializable
data class User(
    val id: Int? = null,
    val username: String,
    val email: String,
    val passwordHash: String? = null,
    val fullName: String? = null,
    val groupName: String? = null,
    val avatarUrl: String? = null,
    val about: String? = null,
    val role: UserRole = UserRole.USER,
    val isActive: Boolean = true,
    @Contextual
    val createdAt: LocalDateTime? = null,
    @Contextual
    val updatedAt: LocalDateTime? = null,
    @Contextual
    val lastLogin: LocalDateTime? = null
)

@Serializable
enum class UserRole {
    USER, ADMIN, MODERATOR
}

@Serializable
data class UserProfile(
    val userId: Int,
    val skills: List<Skill> = emptyList(),
    val interests: List<String> = emptyList(),
    val goals: String? = null,
    val portfolioLink: String? = null,
    val experienceYears: Double? = null,
    val preferredRoles: List<String> = emptyList()
)

@Serializable
data class Skill(
    val name: String,
    val level: SkillLevel,
    val category: String? = null
)

@Serializable
enum class SkillLevel {
    BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
}

// Таблицы базы данных - ЭТОТ КОД ОСТАВЛЯЕМ КАК ЕСТЬ


object Users : Table("users") {
    val id = integer("user_id").autoIncrement()
    val username = varchar("username", 100).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val fullName = varchar("full_name", 200).nullable()
    val groupName = varchar("group_name", 50).nullable()
    val avatarUrl = text("avatar_url").nullable()
    val about = text("about").nullable()
    val role = enumerationByName("role", 20, UserRole::class).default(UserRole.USER)
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }
    val lastLogin = datetime("last_login").nullable()
    
    override val primaryKey = PrimaryKey(id)
}

object Profiles : Table("profiles") {
    val profileId = integer("profile_id").autoIncrement()
    val userId = integer("user_id").uniqueIndex().references(Users.id)
    val skills = text("skills").clientDefault { "[]" }
    val interests = text("interests").clientDefault { "[]" }
    val goals = text("goals").nullable()
    val portfolioLink = text("portfolio_link").nullable()
    val experienceYears = decimal("experience_years", 3, 1).nullable()
    val preferredRoles = text("preferred_roles").clientDefault { "[]" }
    
    override val primaryKey = PrimaryKey(profileId)
}