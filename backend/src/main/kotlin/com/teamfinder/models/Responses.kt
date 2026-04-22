package com.teamfinder.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

// ============================================
// EXPOSED TABLE
// ============================================

object Responses : Table("responses") {
    val responseId = integer("response_id").autoIncrement()
    val projectId = reference("project_id", Projects.projectId)
    val userId = reference("user_id", Users.userId) // Кто откликнулся
    val roleId = reference("role_id", ProjectRoles.roleId).nullable() // На какую роль
    val message = text("message").nullable()
    val status = varchar("status", 50).default("рассматривается")
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(responseId)
    
    init {
        uniqueIndex(projectId, userId, roleId) // Юзер может откликнуться на роль только один раз
    }
}

// ============================================
// DATA CLASSES (DTO)
// ============================================

@Serializable
data class ResponseDTO(
    val responseId: Int,
    val projectId: Int,
    val userId: Int,
    val roleId: Int?,
    val message: String?,
    val status: String,
    val createdAt: String,
    // Вложенные данные для удобства фронтенда
    val projectTitle: String, // Название проекта
    val userUsername: String, // Имя откликнувшегося
    val roleName: String?     // Название роли
)

// Реквест на создание отклика
@Serializable
data class CreateResponseRequest(
    val projectId: Int,
    val roleId: Int?,
    val message: String?
)

// Реквест на изменение статуса отклика (для автора проекта)
@Serializable
data class UpdateResponseStatusRequest(
    val status: String // "принят" или "отклонён"
)