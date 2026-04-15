package com.teamfinder.models

import kotlinx.serialization.Serializable
import com.teamfinder.utils.LocalDateTimeSerializer
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

@Serializable
data class Response(
    val id: Int? = null,
    val projectId: Int,
    val userId: Int,
    val roleId: Int? = null,
    val message: String? = null,
    val status: String = "рассматривается",
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime? = null,
    val userName: String? = null,
    val projectTitle: String? = null
)

@Serializable
enum class ResponseStatus {
    РАССМАТРИВАЕТСЯ, ПРИНЯТ, ОТКЛОНЁН
}

// Таблица Responses
object ResponsesTable : Table("responses") {
    val id = integer("response_id").autoIncrement()
    val projectId = integer("project_id").references(Projects.id)
    val userId = integer("user_id").references(Users.id)
    val roleId = integer("role_id").references(ProjectRolesTable.id).nullable()
    val message = text("message").nullable()
    val status = varchar("status", 50).default("рассматривается")
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}
