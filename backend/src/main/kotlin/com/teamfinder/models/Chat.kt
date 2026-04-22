package com.teamfinder.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

// ============================================
// EXPOSED TABLE
// ============================================

object Messages : Table("messages") {
    val messageId = integer("message_id").autoIncrement()
    val senderId = reference("sender_id", Users.userId)
    val chatType = varchar("chat_type", 20) // 'team' или 'response'
    val chatId = integer("chat_id") // ID проекта или ID отклика
    val content = text("content")
    val timestamp = datetime("timestamp")
    val isRead = bool("is_read").default(false)

    override val primaryKey = PrimaryKey(messageId)
}

// ============================================
// DATA CLASS (DTO) для WebSocket
// ============================================

@Serializable
data class ChatMessageDTO(
    val messageId: Int? = null, // Будет null при отправке от клиента, заполнится на сервере
    val senderId: Int,
    val senderUsername: String, // Для удобства UI
    val chatId: Int,
    val chatType: String,
    val content: String,
    val timestamp: String? = null // Заполнится на сервере
)