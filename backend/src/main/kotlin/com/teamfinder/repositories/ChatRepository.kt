package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

data class ChatInfo(
    val chatType: ChatType,
    val chatId: Int,
    val participants: List<Int>,
    val lastMessage: ChatMessage?,
    val unreadCount: Map<Int, Int> = emptyMap()
)

class ChatRepository {
    private val messageRepository = MessageRepository()

    // ========== CHAT MANAGEMENT ==========

    // Получить или создать чат команды (по projectId)
    suspend fun getOrCreateTeamChat(projectId: Int): ChatInfo = dbQuery {
        val chatType = ChatType.TEAM
        val chatId = projectId // Для team-чата chatId = projectId

        val participants = getTeamParticipants(projectId)
        val lastMessages = messageRepository.getLatestMessages(chatType, chatId, 1)
        val lastMessage = lastMessages.firstOrNull()

        ChatInfo(
            chatType = chatType,
            chatId = chatId,
            participants = participants,
            lastMessage = lastMessage
        )
    }

    // Получить или создать чат заявки (по responseId)
    suspend fun getOrCreateResponseChat(responseId: Int): ChatInfo = dbQuery {
        val chatType = ChatType.RESPONSE
        val chatId = responseId // Для response-чата chatId = responseId

        val participants = getResponseParticipants(responseId)
        val lastMessages = messageRepository.getLatestMessages(chatType, chatId, 1)
        val lastMessage = lastMessages.firstOrNull()

        ChatInfo(
            chatType = chatType,
            chatId = chatId,
            participants = participants,
            lastMessage = lastMessage
        )
    }

    // Получить список всех чатов пользователя
    suspend fun getUserChats(userId: Int): List<ChatInfo> = dbQuery {
        val teamChats = getUserTeamChats(userId)
        val responseChats = getUserResponseChats(userId)

        teamChats + responseChats
    }

    // Проверить, является ли пользователь участником чата
    suspend fun isParticipant(chatType: ChatType, chatId: Int, userId: Int): Boolean = dbQuery {
        when (chatType) {
            ChatType.TEAM -> isTeamMember(chatId, userId)
            ChatType.RESPONSE -> isResponseParticipant(chatId, userId)
        }
    }

    // Получить участников чата
    suspend fun getParticipants(chatType: ChatType, chatId: Int): List<Int> = dbQuery {
        when (chatType) {
            ChatType.TEAM -> getTeamParticipants(chatId)
            ChatType.RESPONSE -> getResponseParticipants(chatId)
        }
    }

    // ========== HELPERS ==========

    private suspend fun getUserTeamChats(userId: Int): List<ChatInfo> {
        // Находим все проекты, где пользователь — автор
        val authoredProjects = Projects.slice(Projects.id)
            .select { Projects.authorId eq userId }
            .map { it[Projects.id] }

        // Находим все проекты, где пользователь откликнулся и был принят
        val acceptedProjects = ResponsesTable.slice(ResponsesTable.projectId)
            .select {
                (ResponsesTable.userId eq userId) and (ResponsesTable.status eq "принят")
            }
            .map { it[ResponsesTable.projectId] }

        val projectIds = (authoredProjects + acceptedProjects).distinct()

        return projectIds.map { projectId ->
            val lastMessages = messageRepository.getLatestMessages(ChatType.TEAM, projectId, 1)
            ChatInfo(
                chatType = ChatType.TEAM,
                chatId = projectId,
                participants = getTeamParticipants(projectId),
                lastMessage = lastMessages.firstOrNull()
            )
        }
    }

    private suspend fun getUserResponseChats(userId: Int): List<ChatInfo> {
        // Мои отклики
        val myResponses = ResponsesTable.slice(ResponsesTable.id)
            .select { ResponsesTable.userId eq userId }
            .map { it[ResponsesTable.id] }

        // Отклики на мои проекты
        val myProjectResponses = ResponsesTable.slice(ResponsesTable.id)
            .select {
                ResponsesTable.projectId inSubQuery (
                    Projects.slice(Projects.id).select { Projects.authorId eq userId }
                )
            }
            .map { it[ResponsesTable.id] }

        val responseIds = (myResponses + myProjectResponses).distinct()

        return responseIds.map { responseId ->
            val lastMessages = messageRepository.getLatestMessages(ChatType.RESPONSE, responseId, 1)
            ChatInfo(
                chatType = ChatType.RESPONSE,
                chatId = responseId,
                participants = getResponseParticipants(responseId),
                lastMessage = lastMessages.firstOrNull()
            )
        }
    }

    private suspend fun getTeamParticipants(projectId: Int): List<Int> {
        // Автор проекта + все принятые участники
        val author = Projects.slice(Projects.authorId)
            .select { Projects.id eq projectId }
            .map { it[Projects.authorId] }
            .singleOrNull() ?: return emptyList()

        val acceptedMembers = ResponsesTable.slice(ResponsesTable.userId)
            .select {
                (ResponsesTable.projectId eq projectId) and (ResponsesTable.status eq "принят")
            }
            .map { it[ResponsesTable.userId] }

        return (listOf(author) + acceptedMembers).distinct()
    }

    private suspend fun isTeamMember(projectId: Int, userId: Int): Boolean {
        val participants = getTeamParticipants(projectId)
        return userId in participants
    }

    private suspend fun getResponseParticipants(responseId: Int): List<Int> {
        val response = ResponsesTable.select { ResponsesTable.id eq responseId }
            .map { row ->
                val userId = row[ResponsesTable.userId]
                val projectId = row[ResponsesTable.projectId]
                val authorId = Projects.slice(Projects.authorId)
                    .select { Projects.id eq projectId }
                    .map { it[Projects.authorId] }
                    .singleOrNull()
                listOfNotNull(userId, authorId)
            }
            .flatten()
            .distinct()

        return response
    }

    private suspend fun isResponseParticipant(responseId: Int, userId: Int): Boolean {
        val participants = getResponseParticipants(responseId)
        return userId in participants
    }
}
