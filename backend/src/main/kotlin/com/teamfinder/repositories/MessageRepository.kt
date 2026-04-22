package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.*
import org.jetbrains.exposed.sql.*
import java.time.LocalDateTime

class MessageRepository {

    // Сохранить новое сообщение в БД
    suspend fun createMessage(senderId: Int, message: ChatMessageDTO): ChatMessageDTO = dbQuery {
        val now = LocalDateTime.now()
        val insertStatement = Messages.insert {
            it[this.senderId] = senderId
            it[chatType] = message.chatType
            it[chatId] = message.chatId
            it[content] = message.content
            it[timestamp] = now
        }
        
        // Возвращаем DTO с id и timestamp из базы
        message.copy(
            messageId = insertStatement.resultedValues!!.single()[Messages.messageId],
            timestamp = now.toString()
        )
    }

    // Получить историю сообщений для чата
    suspend fun getMessagesForChat(chatId: Int, chatType: String): List<ChatMessageDTO> = dbQuery {
        (Messages innerJoin Users)
            .select { (Messages.chatId eq chatId) and (Messages.chatType eq chatType) }
            .orderBy(Messages.timestamp to SortOrder.ASC)
            .map {
                ChatMessageDTO(
                    messageId = it[Messages.messageId],
                    senderId = it[Messages.senderId],
                    senderUsername = it[Users.username],
                    chatId = it[Messages.chatId],
                    chatType = it[Messages.chatType],
                    content = it[Messages.content],
                    timestamp = it[Messages.timestamp].toString()
                )
            }
    }
}