
package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.FileDTO
import com.teamfinder.models.Files
import org.jetbrains.exposed.sql.insert
import java.time.LocalDateTime

class FileRepository {
    
    suspend fun createFileRecord(
        uploaderId: Int,
        entityType: String,
        entityId: Int,
        originalName: String,
        serverPath: String
    ): FileDTO = dbQuery {
        val insertStatement = Files.insert {
            it[userId] = uploaderId
            it[this.entityType] = entityType
            it[this.entityId] = entityId
            it[fileName] = originalName
            it[filePath] = serverPath
            it[uploadedAt] = LocalDateTime.now()
        }
        
        FileDTO(
            fileId = insertStatement.resultedValues!!.single()[Files.fileId],
            fileName = originalName,
            url = serverPath
        )
    }
}