package com.teamfinder.models

import org.jetbrains.exposed.sql.javatime.datetime
import kotlinx.serialization.Serializable
import com.teamfinder.utils.LocalDateTimeSerializer
import org.jetbrains.exposed.sql.Table
import java.time.LocalDateTime

@Serializable
data class Project(
    val id: Int? = null,
    val authorId: Int,
    val title: String,
    val description: String,
    val briefDescription: String,
    val stage: ProjectStage,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime? = null,
    val viewsCount: Int = 0,
    val likesCount: Int = 0,
    val tags: List<String> = emptyList(),
    val neededRoles: List<Role> = emptyList(),
    val authorName: String? = null
)

@Serializable
data class Role(
    val id: Int? = null,
    val projectId: Int? =null,
    val title: String,
    val description: String,
    val requiredSkills: List<String> = emptyList(),
    val isFilled: Boolean = false,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime? = null
)

@Serializable
enum class ProjectStage {
    IDEA, DEVELOPMENT, TESTING, COMPLETED
}

@Serializable
enum class ProjectStatus {
    ACTIVE, ARCHIVED, BLOCKED
}

// Таблицы БД (не трогаем)
object Projects : Table("projects") {
    val id = integer("project_id").autoIncrement()
    val authorId = integer("author_id").references(Users.id)
    val title = varchar("title", 255)
    val description = text("description")
    val briefDescription = varchar("brief_description", 500)
    val stage = enumerationByName("stage", 20, ProjectStage::class)
    val status = enumerationByName("status", 20, ProjectStatus::class).default(ProjectStatus.ACTIVE)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }
    val viewsCount = integer("views_count").default(0)
    val likesCount = integer("likes_count").default(0)
    
    override val primaryKey = PrimaryKey(id)
}

object ProjectTags : Table("project_tags") {
    val projectId = integer("project_id").references(Projects.id)
    val tagId = integer("tag_id").references(Tags.tagId)
    override val primaryKey = PrimaryKey(projectId, tagId)
}

object Tags : Table("tags") {
    val tagId = integer("tag_id").autoIncrement()
    val name = varchar("name", 100).uniqueIndex()
    override val primaryKey = PrimaryKey(tagId)
}

object Vacancies : Table("vacancies") {
    val vacancyId = integer("vacancy_id").autoIncrement()
    val projectId = integer("project_id").references(Projects.id)
    val roleTitle = varchar("role_title", 200)
    val roleDescription = text("role_description")
    val requiredSkills = text("required_skills")
    val isFilled = bool("is_filled").default(false)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(vacancyId)
}

object ProjectLikes : Table("project_likes") {
    val projectId = integer("project_id").references(Projects.id)
    val userId = integer("user_id").references(Users.id)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(projectId, userId)
}

object Comments : Table("comments") {
    val commentId = integer("comment_id").autoIncrement()
    val projectId = integer("project_id").references(Projects.id)
    val userId = integer("user_id").references(Users.id)
    val parentCommentId = integer("parent_comment_id").references(commentId).nullable()
    val content = text("content")
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(commentId)
}