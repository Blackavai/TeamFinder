package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.FilesTable
import com.teamfinder.models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

data class FileRecord(
    val id: Int? = null,
    val userId: Int,
    val entityType: String,
    val entityId: Int,
    val fileName: String,
    val filePath: String,
    val uploadedAt: String? = null,
    val uploaderName: String? = null
)

class FileRepository {

    suspend fun create(
        userId: Int,
        entityType: String,
        entityId: Int,
        fileName: String,
        filePath: String
    ): FileRecord? = dbQuery {
        try {
            val insert = FilesTable.insert {
                it[FilesTable.userId] = userId
                it[FilesTable.entityType] = entityType
                it[FilesTable.entityId] = entityId
                it[FilesTable.fileName] = fileName
                it[FilesTable.filePath] = filePath
                it[FilesTable.uploadedAt] = LocalDateTime.now()
            }

            FileRecord(
                id = insert[FilesTable.id],
                userId = userId,
                entityType = entityType,
                entityId = entityId,
                fileName = fileName,
                filePath = filePath,
                uploadedAt = LocalDateTime.now().toString(),
                uploaderName = getUserName(userId)
            )
        } catch (e: Exception) {
            println("❌ Ошибка в create File: ${e.message}")
            null
        }
    }

    suspend fun findById(id: Int): FileRecord? = dbQuery {
        FilesTable.select { FilesTable.id eq id }
            .map { toRecord(it) }
            .singleOrNull()
    }

    suspend fun findByEntity(entityType: String, entityId: Int): List<FileRecord> = dbQuery {
        FilesTable.select {
            (FilesTable.entityType eq entityType) and (FilesTable.entityId eq entityId)
        }
            .orderBy(FilesTable.uploadedAt to SortOrder.DESC)
            .map { toRecord(it) }
    }

    suspend fun findByUser(userId: Int): List<FileRecord> = dbQuery {
        FilesTable.select { FilesTable.userId eq userId }
            .orderBy(FilesTable.uploadedAt to SortOrder.DESC)
            .map { toRecord(it) }
    }

    suspend fun delete(id: Int): Boolean = dbQuery {
        FilesTable.deleteWhere { FilesTable.id eq id } > 0
    }

    suspend fun deleteByEntity(entityType: String, entityId: Int): Boolean = dbQuery {
        FilesTable.deleteWhere {
            (FilesTable.entityType eq entityType) and (FilesTable.entityId eq entityId)
        } > 0
    }

    // ========== HELPERS ==========

    private fun toRecord(row: ResultRow): FileRecord = FileRecord(
        id = row[FilesTable.id],
        userId = row[FilesTable.userId],
        entityType = row[FilesTable.entityType],
        entityId = row[FilesTable.entityId],
        fileName = row[FilesTable.fileName],
        filePath = row[FilesTable.filePath],
        uploadedAt = row[FilesTable.uploadedAt].toString(),
        uploaderName = getUserName(row[FilesTable.userId])
    )

    private fun getUserName(userId: Int): String? {
        return Users.slice(Users.username)
            .select { Users.id eq userId }
            .map { it[Users.username] }
            .singleOrNull()
    }
}
