package com.teamfinder.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb


// ============================================
// EXPOSED TABLES (Схема БД)
// ============================================

object Tags : Table("tags") {
    val tagId = integer("tag_id").autoIncrement()
    val name = varchar("name", 50).uniqueIndex()
    val category = varchar("category", 50).nullable() // 'skill', 'industry'
    
    override val primaryKey = PrimaryKey(tagId)
}

object Projects : Table("projects") {
    val projectId = integer("project_id").autoIncrement()
    val authorId = reference("author_id", Users.userId)
    val title = varchar("title", 200)
    val description = text("description").nullable()
    val status = varchar("status", 50).default("идея")
    val deadline = date("deadline").nullable()
    val industry = varchar("industry", 100).nullable()
    val createdAt = datetime("created_at")
    val isActive = bool("is_active").default(true)
    
    override val primaryKey = PrimaryKey(projectId)
}

object ProjectTags : Table("project_tags") {
    val projectId = reference("project_id", Projects.projectId)
    val tagId = reference("tag_id", Tags.tagId)
    
    override val primaryKey = PrimaryKey(projectId, tagId)
}

object ProjectRoles : Table("project_roles") {
    val roleId = integer("role_id").autoIncrement()
    val projectId = reference("project_id", Projects.projectId)
    val roleName = varchar("role_name", 100)
    val requiredSkills = jsonb<List<String>>("required_skills", Json.Default).default(emptyList())
    val spotsTotal = integer("spots_total")
    val spotsFilled = integer("spots_filled").default(0)
    
    override val primaryKey = PrimaryKey(roleId)
}

// ============================================
// DATA CLASSES (DTO)
// ============================================

@Serializable
data class RoleDTO(
    val roleId: Int,
    val roleName: String,
    val requiredSkills: List<String>,
    val spotsTotal: Int,
    val spotsFilled: Int
)

@Serializable
data class ProjectDTO(
    val projectId: Int,
    val authorId: Int,
    val title: String,
    val description: String?,
    val status: String,
    val deadline: String?, // Дата строкой YYYY-MM-DD для удобства фронтенда
    val industry: String?,
    val isActive: Boolean,
    val tags: List<String>,
    val roles: List<RoleDTO>
)

// Реквесты от клиента на создание
@Serializable
data class CreateRoleRequest(
    val roleName: String,
    val requiredSkills: List<String>,
    val spotsTotal: Int
)

@Serializable
data class CreateProjectRequest(
    val title: String,
    val description: String?,
    val deadline: String?, // YYYY-MM-DD
    val industry: String?,
    val tags: List<String>,             // Юзер передает просто список строк (тегов)
    val roles: List<CreateRoleRequest>  // И какие роли нужны
)