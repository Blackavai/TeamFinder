package com.teamfinder.models

import kotlinx.serialization.Serializable
import com.teamfinder.utils.LocalDateTimeSerializer
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class Project(
    val id: Int? = null,
    val authorId: Int = 0,
    val title: String,
    val description: String? = null,
    val status: String = "идея",
    val deadline: LocalDate? = null,
    val industry: String? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime? = null,
    val isActive: Boolean = true,
    val tags: List<String> = emptyList(),
    val roles: List<ProjectRole> = emptyList(),
    val authorName: String? = null
)

@Serializable
data class ProjectRole(
    val id: Int? = null,
    val projectId: Int? = null,
    val roleName: String,
    val requiredSkills: List<String> = emptyList(),
    val spotsTotal: Int,
    val spotsFilled: Int = 0
)

// Таблица Projects
object Projects : Table("projects") {
    val id = integer("project_id").autoIncrement()
    val authorId = integer("author_id").references(Users.id)
    val title = varchar("title", 200)
    val description = text("description").nullable()
    val status = varchar("status", 50).default("идея")
    val deadline = date("deadline").nullable()
    val industry = varchar("industry", 100).nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val isActive = bool("is_active").default(true)

    override val primaryKey = PrimaryKey(id)
}

// Таблица Tags
object Tags : Table("tags") {
    val tagId = integer("tag_id").autoIncrement()
    val name = varchar("name", 50).uniqueIndex()
    val category = varchar("category", 50).nullable()

    override val primaryKey = PrimaryKey(tagId)
}

// Таблица ProjectTags
object ProjectTags : Table("project_tags") {
    val projectId = integer("project_id").references(Projects.id)
    val tagId = integer("tag_id").references(Tags.tagId)

    override val primaryKey = PrimaryKey(projectId, tagId)
}

// Таблица ProjectRoles
object ProjectRolesTable : Table("project_roles") {
    val id = integer("role_id").autoIncrement()
    val projectId = integer("project_id").references(Projects.id)
    val roleName = varchar("role_name", 100)
    val requiredSkills = text("required_skills").clientDefault { "[]" }
    val spotsTotal = integer("spots_total")
    val spotsFilled = integer("spots_filled").default(0)

    override val primaryKey = PrimaryKey(id)
}

// Таблица Files
object FilesTable : Table("files") {
    val id = integer("file_id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val entityType = varchar("entity_type", 20)
    val entityId = integer("entity_id")
    val fileName = varchar("file_name", 255)
    val filePath = text("file_path")
    val uploadedAt = datetime("uploaded_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}
