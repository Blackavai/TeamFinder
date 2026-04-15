package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

class ResponseRepository {

    // ========== CREATE ==========

    suspend fun create(response: Response): Response? = dbQuery {
        try {
            val insert = ResponsesTable.insert {
                it[projectId] = response.projectId
                it[userId] = response.userId
                response.roleId?.let { r -> it[roleId] = r }
                it[message] = response.message
                it[status] = response.status
                it[createdAt] = LocalDateTime.now()
            }

            Response(
                id = insert[ResponsesTable.id],
                projectId = response.projectId,
                userId = response.userId,
                roleId = response.roleId,
                message = response.message,
                status = response.status,
                createdAt = LocalDateTime.now(),
                userName = getUserName(response.userId),
                projectTitle = getProjectTitle(response.projectId)
            )
        } catch (e: Exception) {
            println("❌ Ошибка в create Response: ${e.message}")
            null
        }
    }

    // ========== READ ==========

    suspend fun findById(id: Int): Response? = dbQuery {
        ResponsesTable.select { ResponsesTable.id eq id }
            .map { toResponse(it) }
            .singleOrNull()
    }

    suspend fun findByProject(projectId: Int): List<Response> = dbQuery {
        ResponsesTable.select { ResponsesTable.projectId eq projectId }
            .orderBy(ResponsesTable.createdAt to SortOrder.DESC)
            .map { toResponse(it) }
    }

    suspend fun findByUser(userId: Int): List<Response> = dbQuery {
        ResponsesTable.select { ResponsesTable.userId eq userId }
            .orderBy(ResponsesTable.createdAt to SortOrder.DESC)
            .map { toResponse(it) }
    }

    suspend fun findByProjectAndUser(projectId: Int, userId: Int): Response? = dbQuery {
        ResponsesTable.select {
            (ResponsesTable.projectId eq projectId) and (ResponsesTable.userId eq userId)
        }.map { toResponse(it) }.singleOrNull()
    }

    suspend fun findByStatus(status: String): List<Response> = dbQuery {
        ResponsesTable.select { ResponsesTable.status eq status }
            .orderBy(ResponsesTable.createdAt to SortOrder.DESC)
            .map { toResponse(it) }
    }

    // ========== UPDATE ==========

    suspend fun updateStatus(id: Int, status: String): Boolean = dbQuery {
        ResponsesTable.update({ ResponsesTable.id eq id }) {
            it[ResponsesTable.status] = status
        } > 0
    }

    suspend fun update(id: Int, response: Response): Boolean = dbQuery {
        ResponsesTable.update({ ResponsesTable.id eq id }) {
            it[message] = response.message
            it[status] = response.status
            response.roleId?.let { r -> it[roleId] = r }
        } > 0
    }

    // ========== DELETE ==========

    suspend fun delete(id: Int): Boolean = dbQuery {
        ResponsesTable.deleteWhere { ResponsesTable.id eq id } > 0
    }

    suspend fun deleteByProjectAndUser(projectId: Int, userId: Int): Boolean = dbQuery {
        ResponsesTable.deleteWhere {
            (ResponsesTable.projectId eq projectId) and (ResponsesTable.userId eq userId)
        } > 0
    }

    // ========== HELPERS ==========

    private fun toResponse(row: ResultRow): Response = Response(
        id = row[ResponsesTable.id],
        projectId = row[ResponsesTable.projectId],
        userId = row[ResponsesTable.userId],
        roleId = row[ResponsesTable.roleId],
        message = row[ResponsesTable.message],
        status = row[ResponsesTable.status],
        createdAt = row[ResponsesTable.createdAt],
        userName = getUserName(row[ResponsesTable.userId]),
        projectTitle = getProjectTitle(row[ResponsesTable.projectId])
    )

    private fun getUserName(userId: Int): String? {
        return Users.slice(Users.username)
            .select { Users.id eq userId }
            .map { it[Users.username] }
            .singleOrNull()
    }

    private fun getProjectTitle(projectId: Int): String? {
        return Projects.slice(Projects.title)
            .select { Projects.id eq projectId }
            .map { it[Projects.title] }
            .singleOrNull()
    }
}
