package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

class InvitationRepository {

    // ========== CREATE ==========

    suspend fun create(invitation: Invitation): Invitation? = dbQuery {
        try {
            val insert = InvitationsTable.insert {
                it[projectId] = invitation.projectId
                it[fromUserId] = invitation.fromUserId
                it[toUserId] = invitation.toUserId
                invitation.roleId?.let { r -> it[roleId] = r }
                it[message] = invitation.message
                it[status] = invitation.status
                it[createdAt] = LocalDateTime.now()
            }

            Invitation(
                id = insert[InvitationsTable.id],
                projectId = invitation.projectId,
                fromUserId = invitation.fromUserId,
                toUserId = invitation.toUserId,
                roleId = invitation.roleId,
                message = invitation.message,
                status = invitation.status,
                createdAt = LocalDateTime.now(),
                fromUserName = getUserName(invitation.fromUserId),
                toUserName = getUserName(invitation.toUserId),
                projectTitle = getProjectTitle(invitation.projectId)
            )
        } catch (e: Exception) {
            println("❌ Ошибка в create Invitation: ${e.message}")
            null
        }
    }

    // ========== READ ==========

    suspend fun findById(id: Int): Invitation? = dbQuery {
        InvitationsTable.select { InvitationsTable.id eq id }
            .map { toInvitation(it) }
            .singleOrNull()
    }

    suspend fun findByProject(projectId: Int): List<Invitation> = dbQuery {
        InvitationsTable.select { InvitationsTable.projectId eq projectId }
            .orderBy(InvitationsTable.createdAt to SortOrder.DESC)
            .map { toInvitation(it) }
    }

    suspend fun findByToUser(toUserId: Int): List<Invitation> = dbQuery {
        InvitationsTable.select { InvitationsTable.toUserId eq toUserId }
            .orderBy(InvitationsTable.createdAt to SortOrder.DESC)
            .map { toInvitation(it) }
    }

    suspend fun findByFromUser(fromUserId: Int): List<Invitation> = dbQuery {
        InvitationsTable.select { InvitationsTable.fromUserId eq fromUserId }
            .orderBy(InvitationsTable.createdAt to SortOrder.DESC)
            .map { toInvitation(it) }
    }

    suspend fun findByProjectAndUsers(projectId: Int, fromUserId: Int, toUserId: Int): Invitation? = dbQuery {
        InvitationsTable.select {
            (InvitationsTable.projectId eq projectId) and
            (InvitationsTable.fromUserId eq fromUserId) and
            (InvitationsTable.toUserId eq toUserId)
        }.map { toInvitation(it) }.singleOrNull()
    }

    suspend fun findByStatus(status: String): List<Invitation> = dbQuery {
        InvitationsTable.select { InvitationsTable.status eq status }
            .orderBy(InvitationsTable.createdAt to SortOrder.DESC)
            .map { toInvitation(it) }
    }

    suspend fun getPendingForUser(toUserId: Int): List<Invitation> = dbQuery {
        InvitationsTable.select {
            (InvitationsTable.toUserId eq toUserId) and (InvitationsTable.status eq "отправлено")
        }.orderBy(InvitationsTable.createdAt to SortOrder.DESC)
            .map { toInvitation(it) }
    }

    // ========== UPDATE ==========

    suspend fun updateStatus(id: Int, status: String): Boolean = dbQuery {
        InvitationsTable.update({ InvitationsTable.id eq id }) {
            it[InvitationsTable.status] = status
        } > 0
    }

    suspend fun update(id: Int, invitation: Invitation): Boolean = dbQuery {
        InvitationsTable.update({ InvitationsTable.id eq id }) {
            it[message] = invitation.message
            it[status] = invitation.status
            invitation.roleId?.let { r -> it[roleId] = r }
        } > 0
    }

    // ========== DELETE ==========

    suspend fun delete(id: Int): Boolean = dbQuery {
        InvitationsTable.deleteWhere { InvitationsTable.id eq id } > 0
    }

    // ========== HELPERS ==========

    private fun toInvitation(row: ResultRow): Invitation = Invitation(
        id = row[InvitationsTable.id],
        projectId = row[InvitationsTable.projectId],
        fromUserId = row[InvitationsTable.fromUserId],
        toUserId = row[InvitationsTable.toUserId],
        roleId = row[InvitationsTable.roleId],
        message = row[InvitationsTable.message],
        status = row[InvitationsTable.status],
        createdAt = row[InvitationsTable.createdAt],
        fromUserName = getUserName(row[InvitationsTable.fromUserId]),
        toUserName = getUserName(row[InvitationsTable.toUserId]),
        projectTitle = getProjectTitle(row[InvitationsTable.projectId])
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
