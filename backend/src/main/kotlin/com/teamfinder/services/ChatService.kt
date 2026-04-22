package com.teamfinder.services

import com.teamfinder.models.ChatMessageDTO
import com.teamfinder.repositories.MessageRepository
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class ChatService(private val messageRepository: MessageRepository) {
    // Потокобезопасная мапа для хранения подключений
    // Ключ - ID чата (например, "team-123"), значение - список сессий
    private val connections = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    // Когда пользователь подключается
    fun onJoin(chatIdentifier: String, session: WebSocketSession) {
        // Если для этого чата еще нет комнаты, создаем ее
        val room = connections.computeIfAbsent(chatIdentifier) { ConcurrentHashMap.newKeySet() }
        room.add(session)
    }

    // Когда пользователь отключается
    fun onLeave(chatIdentifier: String, session: WebSocketSession) {
        connections[chatIdentifier]?.remove(session)
        // Если в комнате никого не осталось, можно ее удалить
        if (connections[chatIdentifier]?.isEmpty() == true) {
            connections.remove(chatIdentifier)
        }
    }

    // Разослать сообщение всем в комнате
    private suspend fun broadcast(chatIdentifier: String, message: ChatMessageDTO) {
        val messageJson = Json.encodeToString(message)
        connections[chatIdentifier]?.forEach { session ->
            try {
                session.send(Frame.Text(messageJson))
            } catch (e: Exception) {
                // Клиент мог оборвать соединение, это нормально
                println("Could not send message to session: ${e.message}")
            }
        }
    }

    // Когда от клиента приходит новое сообщение
    suspend fun onMessageReceived(senderId: Int, senderUsername: String, rawMessage: String) {
        // 1. Парсим JSON
        val incomingDto = Json.decodeFromString<ChatMessageDTO>(rawMessage).copy(
            senderId = senderId,
            senderUsername = senderUsername
        )

        // 2. Сохраняем в базу
        val savedMessage = messageRepository.createMessage(senderId, incomingDto)
        
        // 3. Рассылаем всем в комнате
        val chatIdentifier = "${savedMessage.chatType}-${savedMessage.chatId}"
        broadcast(chatIdentifier, savedMessage)
    }
}