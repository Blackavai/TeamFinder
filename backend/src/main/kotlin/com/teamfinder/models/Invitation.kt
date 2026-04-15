package com.teamfinder.models

import kotlinx.serialization.Serializable
import com.teamfinder.utils.LocalDateTimeSerializer
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

@Serializable
data class Invitation(
    val id: Int? = null,
    val projectId: Int,
    val fromUserId: Int,
    val toUserId: Int,
    val roleId: Int? = null,
    val message: String? = null,
    val status: String = "отправлено",
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime? = null,
    val fromUserName: String? = null,
    val toUserName: String? = null,
    val projectTitle: String? = null
)

@Serializable
enum class InvitationStatus {
    ОТПРАВЛЕНО, ПРИНЯТО, ОТКЛОНЕНО
}

// Таблица Invitations
object InvitationsTable : Table("invitations") {
    val id = integer("invitation_id").autoIncrement()
    val projectId = integer("project_id").references(Projects.id)
    val fromUserId = integer("from_user_id").references(Users.id)
    val toUserId = integer("to_user_id").references(Users.id)
    val roleId = integer("role_id").references(ProjectRolesTable.id).nullable()
    val message = text("message").nullable()
    val status = varchar("status", 50).default("отправлено")
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}
