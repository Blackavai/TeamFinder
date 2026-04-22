package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

class ResponseRepository {

    // Вспомогательная функция для сборки красивого DTO
    private fun ResultRow.toResponseDTO(): ResponseDTO {
        return ResponseDTO(
            responseId = this[Responses.responseId],
            projectId = this[Responses.projectId],
            userId = this[Responses.userId],
            roleId = this[Responses.roleId],
            message = this[Responses.message],
            status = this[Responses.status],
            createdAt = this[Responses.createdAt].toString(),
            // Данные из JOIN-ов
            projectTitle = this[Projects.title],
            userUsername = this[Users.username],
            roleName = this.getOrNull(ProjectRoles.roleName)
        )
    }

    // 1. Создать отклик
    suspend fun createResponse(userId: Int, request: CreateResponseRequest): ResponseDTO? = dbQuery {
        val insertStatement = Responses.insert {
            it[this.userId] = userId
            it[projectId] = request.projectId
            it[roleId] = request.roleId
            it[message] = request.message
            it[createdAt] = LocalDateTime.now()
        }
        val newResponseId = insertStatement.resultedValues?.singleOrNull()?.get(Responses.responseId)
        
        newResponseId?.let { getResponseByIdInternal(it) }
    }

    // 2. Изменить статус отклика (для автора проекта)
    suspend fun updateResponseStatus(responseId: Int, newStatus: String): Boolean = dbQuery {
        Responses.update({ Responses.responseId eq responseId }) {
            it[status] = newStatus
        } > 0
    }
    
    // 3. Получить отклик по ID (внутренний метод для сборки DTO)
    private fun getResponseByIdInternal(id: Int): ResponseDTO? {
        return (Responses
                innerJoin Projects on Responses.projectId eq Projects.projectId
                innerJoin Users on Responses.userId eq Users.userId
                leftJoin ProjectRoles on Responses.roleId eq ProjectRoles.roleId)
            .select { Responses.responseId eq id }
            .singleOrNull()
            ?.toResponseDTO()
    }
    
    // 4. Получить все отклики на конкретный проект
    suspend fun getResponsesForProject(projectId: Int): List<ResponseDTO> = dbQuery {
        (Responses
                innerJoin Projects on Responses.projectId eq Projects.projectId
                innerJoin Users on Responses.userId eq Users.userId
                leftJoin ProjectRoles on Responses.roleId eq ProjectRoles.roleId)
            .select { Responses.projectId eq projectId }
            .map { it.toResponseDTO() }
    }

    // 5. Получить все МОИ отклики (чтобы я мог следить за их статусом)
    suspend fun getMyResponses(userId: Int): List<ResponseDTO> = dbQuery {
        (Responses
                innerJoin Projects on Responses.projectId eq Projects.projectId
                innerJoin Users on Responses.userId eq Users.userId
                leftJoin ProjectRoles on Responses.roleId eq ProjectRoles.roleId)
            .select { Responses.userId eq userId }
            .map { it.toResponseDTO() }
    }
    
    // Вспомогательная функция для проверки, является ли юзер автором проекта
    suspend fun isUserProjectAuthor(userId: Int, projectId: Int): Boolean = dbQuery {
        Projects.select { (Projects.authorId eq userId) and (Projects.projectId eq projectId) }.count() > 0
    }
}