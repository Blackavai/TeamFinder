package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

class MessageRepository {

    // ========== CREATE ==========

    suspend fun create(message: ChatMessage): ChatMessage? = dbQuery {
        try {
            val insert = MessagesTable.insert {
                it[senderId] = message.senderId
                it[chatType] = message.chatType.name
                it[chatId] = message.chatId
                it[content] = message.content
                it[isRead] = message.isRead
                it[timestamp] = LocalDateTime.now()
            }

            ChatMessage(
                id = insert[MessagesTable.id],
                senderId = message.senderId,
                chatType = message.chatType,
                chatId = message.chatId,
                content = message.content,
                timestamp = LocalDateTime.now(),
                isRead = false,
                senderName = getUserName(message.senderId)
            )
        } catch (e: Exception) {
            println("❌ Ошибка в create Message: ${e.message}")
            null
        }
    }

    // ========== READ ==========

    suspend fun findById(id: Int): ChatMessage? = dbQuery {
        MessagesTable.select { MessagesTable.id eq id }
            .map { toMessage(it) }
            .singleOrNull()
    }

    suspend fun findByChat(chatType: ChatType, chatId: Int, limit: Int = 50, offset: Int = 0): List<ChatMessage> = dbQuery {
        MessagesTable.select {
            (MessagesTable.chatType eq chatType.name) and (MessagesTable.chatId eq chatId)
        }
            .orderBy(MessagesTable.timestamp to SortOrder.ASC)
            .limit(limit, offset.toLong())
            .map { toMessage(it) }
    }

    suspend fun getLatestMessages(chatType: ChatType, chatId: Int, count: Int = 10): List<ChatMessage> = dbQuery {
        MessagesTable.select {
            (MessagesTable.chatType eq chatType.name) and (MessagesTable.chatId eq chatId)
        }
            .orderBy(MessagesTable.timestamp to SortOrder.DESC)
            .limit(count)
            .map { toMessage(it) }
            .reversed()
    }

    suspend fun getUnreadCount(chatType: ChatType, chatId: Int, userId: Int): Int = dbQuery {
        MessagesTable.select {
            (MessagesTable.chatType eq chatType.name) and
            (MessagesTable.chatId eq chatId) and
            (MessagesTable.isRead eq false) and
            (MessagesTable.senderId neq userId)
        }.count().toInt()
    }

    suspend fun countByChat(chatType: ChatType, chatId: Int): Int = dbQuery {
        MessagesTable.select {
            (MessagesTable.chatType eq chatType.name) and (MessagesTable.chatId eq chatId)
        }.count().toInt()
    }

    // ========== UPDATE ==========

    suspend fun markAsRead(id: Int): Boolean = dbQuery {
        MessagesTable.update({ MessagesTable.id eq id }) {
            it[isRead] = true
        } > 0
    }

    suspend fun markAllAsRead(chatType: ChatType, chatId: Int, userId: Int): Boolean = dbQuery {
        MessagesTable.update({
            (MessagesTable.chatType eq chatType.name) and
            (MessagesTable.chatId eq chatId) and
            (MessagesTable.senderId neq userId) and
            (MessagesTable.isRead eq false)
        }) {
            it[isRead] = true
        } > 0
    }

    suspend fun update(id: Int, content: String): Boolean = dbQuery {
        MessagesTable.update({ MessagesTable.id eq id }) {
            it[MessagesTable.content] = content
        } > 0
    }

    // ========== DELETE ==========

    suspend fun delete(id: Int): Boolean = dbQuery {
        MessagesTable.deleteWhere { MessagesTable.id eq id } > 0
    }

    // ========== HELPERS ==========

    private fun toMessage(row: ResultRow): ChatMessage = ChatMessage(
        id = row[MessagesTable.id],
        senderId = row[MessagesTable.senderId],
        chatType = ChatType.valueOf(row[MessagesTable.chatType]),
        chatId = row[MessagesTable.chatId],
        content = row[MessagesTable.content],
        timestamp = row[MessagesTable.timestamp],
        isRead = row[MessagesTable.isRead],
        senderName = getUserName(row[MessagesTable.senderId])
    )

    private fun getUserName(userId: Int): String? {
        return Users.slice(Users.username)
            .select { Users.id eq userId }
            .map { it[Users.username] }
            .singleOrNull()
    }
}
