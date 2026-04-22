package com.teamfinder.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

// ============================================
// EXPOSED TABLE
// ============================================

object Files : Table("files") {
    val fileId = integer("file_id").autoIncrement()
    val userId = reference("user_id", Users.userId) // Кто загрузил
    val entityType = varchar("entity_type", 20) // 'project'
    val entityId = integer("entity_id")
    val fileName = varchar("file_name", 255) // Оригинальное имя файла
    val filePath = text("file_path") // Путь на сервере (например, /static/uuid.jpg)
    val uploadedAt = datetime("uploaded_at")

    override val primaryKey = PrimaryKey(fileId)
}

// ============================================
// DATA CLASS (DTO)
// ============================================

@Serializable
data class FileDTO(
    val fileId: Int,
    val fileName: String,
    val url: String // Публичный URL для доступа к файлу
)