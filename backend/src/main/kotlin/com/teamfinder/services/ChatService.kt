package com.teamfinder.services

import com.teamfinder.models.ChatMessage
import com.teamfinder.models.ChatType
import com.teamfinder.repositories.ChatRepository
import com.teamfinder.repositories.MessageRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class IncomingMessage(
    val type: String,          // "message", "typing", "read"
    val chatType: String,      // "TEAM", "RESPONSE"
    val chatId: Int,
    val content: String? = null,
    val messageId: Int? = null
)

@Serializable
data class OutgoingMessage(
    val type: String,          // "message", "typing", "read", "error"
    val chatType: String? = null,
    val chatId: Int? = null,
    val data: Any? = null,
    val error: String? = null
)

class ChatService {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    private val messageRepository = MessageRepository()
    private val chatRepository = ChatRepository()

    // Хранение сессий: userId -> Channel для отправки сообщений
    private val userSessions = ConcurrentHashMap<Int, Channel<String>>()

    // Flow для входящих сообщений (для обработки)
    private val _incomingMessages = MutableSharedFlow<ChatMessage>()
    val incomingMessages = _incomingMessages.asSharedFlow()

    // ========== WEBSOCKET MANAGEMENT ==========

    fun connect(userId: Int): Channel<String> {
        val channel = Channel<String>(Channel.UNLIMITED)
        userSessions[userId] = channel
        logger.info("User $userId connected to chat")
        return channel
    }

    fun disconnect(userId: Int) {
        userSessions.remove(userId)?.close()
        logger.info("User $userId disconnected from chat")
    }

    fun isConnected(userId: Int): Boolean = userSessions.containsKey(userId)

    // ========== MESSAGE SENDING ==========

    suspend fun sendMessage(
        senderId: Int,
        chatType: ChatType,
        chatId: Int,
        content: String
    ): ChatMessage? {
        // Проверяем участие пользователя в чате
        val isParticipant = chatRepository.isParticipant(chatType, chatId, senderId)
        if (!isParticipant) {
            logger.warn("User $senderId is not a participant of chat $chatType:$chatId")
            return null
        }

        // Создаём и сохраняем сообщение
        val message = ChatMessage(
            senderId = senderId,
            chatType = chatType,
            chatId = chatId,
            content = content,
            isRead = false
        )

        val saved = messageRepository.create(message)
        if (saved == null) {
            logger.error("Failed to save message")
            return null
        }

        // Отправляем всем участникам чата
        broadcastToChat(chatType, chatId, saved, excludeUserId = senderId)

        // Отправляем отправителю подтверждение
        sendToUser(senderId, OutgoingMessage(
            type = "message",
            chatType = chatType.name,
            chatId = chatId,
            data = saved
        ))

        logger.info("Message sent: sender=$senderId, chat=$chatType:$chatId")
        return saved
    }

    // ========== TYPING INDICATOR ==========

    suspend fun sendTyping(userId: Int, chatType: ChatType, chatId: Int, isTyping: Boolean) {
        val isParticipant = chatRepository.isParticipant(chatType, chatId, userId)
        if (!isParticipant) return

        val participants = chatRepository.getParticipants(chatType, chatId)
        participants.filter { it != userId && isConnected(it) }.forEach { participantId ->
            sendToUser(participantId, OutgoingMessage(
                type = "typing",
                chatType = chatType.name,
                chatId = chatId,
                data = mapOf("userId" to userId, "isTyping" to isTyping)
            ))
        }
    }

    // ========== READ RECEIPTS ==========

    suspend fun markMessagesAsRead(userId: Int, chatType: ChatType, chatId: Int) {
        val isParticipant = chatRepository.isParticipant(chatType, chatId, userId)
        if (!isParticipant) return

        messageRepository.markAllAsRead(chatType, chatId, userId)

        // Уведомляем других участников
        val participants = chatRepository.getParticipants(chatType, chatId)
        participants.filter { it != userId && isConnected(it) }.forEach { participantId ->
            sendToUser(participantId, OutgoingMessage(
                type = "read",
                chatType = chatType.name,
                chatId = chatId,
                data = mapOf("userId" to userId)
            ))
        }
    }

    // ========== BROADCAST ==========

    private fun broadcastToChat(chatType: ChatType, chatId: Int, message: ChatMessage, excludeUserId: Int) {
        val participants = chatRepository.getParticipants(chatType, chatId)
        participants.filter { it != excludeUserId && isConnected(it) }.forEach { participantId ->
            sendToUser(participantId, OutgoingMessage(
                type = "message",
                chatType = chatType.name,
                chatId = chatId,
                data = message
            ))
        }
    }

    private fun sendToUser(userId: Int, outgoing: OutgoingMessage) {
        val channel = userSessions[userId] ?: return
        try {
            channel.trySend(Json.encodeToString(outgoing)).isSuccess
        } catch (e: Exception) {
            logger.error("Failed to send message to user $userId: ${e.message}")
        }
    }

    // ========== CHAT INFO ==========

    suspend fun getChatInfo(chatType: ChatType, chatId: Int, userId: Int): ChatInfo? {
        val isParticipant = chatRepository.isParticipant(chatType, chatId, userId)
        if (!isParticipant) return null

        return when (chatType) {
            ChatType.TEAM -> chatRepository.getOrCreateTeamChat(chatId)
            ChatType.RESPONSE -> chatRepository.getOrCreateResponseChat(chatId)
        }
    }

    suspend fun getUserChats(userId: Int): List<ChatInfo> {
        return chatRepository.getUserChats(userId)
    }

    suspend fun getMessages(chatType: ChatType, chatId: Int, userId: Int, limit: Int, offset: Int): List<ChatMessage>? {
        val isParticipant = chatRepository.isParticipant(chatType, chatId, userId)
        if (!isParticipant) return null

        return messageRepository.findByChat(chatType, chatId, limit, offset)
    }
}
