package com.teamfinder.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import org.jetbrains.exposed.sql.Table
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.javatime.datetime

@Serializable
data class Chat(
    val id: Int? = null,
    val type: ChatType,
    val name: String? = null,
    val projectId: Int? = null,
    @Contextual
    val createdAt: LocalDateTime? = null,
    val participants: List<Int> = emptyList(),
    val lastMessage: Message? = null
)

@Serializable
data class Message(
    val id: Int? = null,
    val chatId: Int,
    val senderId: Int,
    val senderName: String? = null,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    @Contextual
    val sentAt: LocalDateTime? = null,
    @Contextual
    val updatedAt: LocalDateTime? = null,
    var isRead: Boolean = false,
    var isDelivered: Boolean = false
)

@Serializable
data class Typing(
    val chatId: Int,
    val userId: Int,
    val isTyping: Boolean,
    @Contextual
    val timestamp: LocalDateTime? = null
)

@Serializable
enum class ChatType {
    DIRECT, GROUP, PROJECT
}

@Serializable
enum class MessageType {
    TEXT, IMAGE, FILE, SYSTEM
}

object Chats : Table("chats") {
    val id = integer("chat_id").autoIncrement()
    val type = enumerationByName("type", 20, ChatType::class)
    val name = varchar("name", 255).nullable()
    val projectId = integer("project_id").references(Projects.id).nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    
    override val primaryKey = PrimaryKey(id)
}

object ChatParticipants : Table("chat_participants") {
    val chatId = integer("chat_id").references(Chats.id)
    val userId = integer("user_id").references(Users.id)
    val joinedAt = datetime("joined_at").clientDefault { LocalDateTime.now() }
    val lastReadAt = datetime("last_read_at").clientDefault { LocalDateTime.now() }
    
    override val primaryKey = PrimaryKey(chatId, userId)
}

object Messages : Table("messages") {
    val id = integer("message_id").autoIncrement()
    val chatId = integer("chat_id").references(Chats.id)
    val senderId = integer("sender_id").references(Users.id)
    val content = text("content")
    val type = enumerationByName("type", 20, MessageType::class).default(MessageType.TEXT)
    val sentAt = datetime("sent_at").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }
    val isRead = bool("is_read").default(false)
    val isDelivered = bool("is_delivered").default(false)
    
    override val primaryKey = PrimaryKey(id)
}