package com.teamfinder.models

import kotlinx.serialization.Serializable
import com.teamfinder.utils.LocalDateTimeSerializer
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

@Serializable
data class ChatMessage(
    val id: Int? = null,
    val senderId: Int,
    val chatType: ChatType,
    val chatId: Int,
    val content: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val timestamp: LocalDateTime? = null,
    val isRead: Boolean = false,
    val senderName: String? = null
)

@Serializable
enum class ChatType {
    TEAM, RESPONSE
}

// Таблица Messages
object MessagesTable : Table("messages") {
    val id = integer("message_id").autoIncrement()
    val senderId = integer("sender_id").references(Users.id)
    val chatType = varchar("chat_type", 20)
    val chatId = integer("chat_id")
    val content = text("content")
    val timestamp = datetime("timestamp").clientDefault { LocalDateTime.now() }
    val isRead = bool("is_read").default(false)

    override val primaryKey = PrimaryKey(id)
}
