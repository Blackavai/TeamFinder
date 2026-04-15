package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

class ProjectRepository {
    private fun toProject(row: ResultRow): Project = Project(
        id = row[Projects.id],
        authorId = row[Projects.authorId],
        title = row[Projects.title],
        description = row[Projects.description],
        status = row[Projects.status],
        createdAt = row[Projects.createdAt].toString()
    )

    suspend fun findById(id: Int): Project? = dbQuery {
        Projects.select { Projects.id eq id }.map { toProject(it) }.singleOrNull()
    }

    suspend fun create(project: Project): Project? = dbQuery {
        val insertStatement = Projects.insert {
            it[authorId] = project.authorId
            it[title] = project.title
            it[status] = project.status
            it[createdAt] = LocalDateTime.now()
        }
        
        insertStatement.resultedValues?.firstOrNull()?.let { toProject(it) }
    }

    suspend fun getFeed(page: Int, limit: Int) = dbQuery {
        Projects.selectAll().limit(limit, ((page - 1) * limit).toLong()).map { toProject(it) }
    }

    suspend fun update(id: Int, p: Project) = dbQuery {
        Projects.update({ Projects.id eq id }) { it[Projects.title] = p.title } > 0
    }

    suspend fun delete(id: Int) = dbQuery {
        Projects.deleteWhere { Projects.id eq id } > 0
    }

    suspend fun incrementViews(id: Int) = true
    suspend fun toggleLike(projectId: Int, userId: Int) = true
    suspend fun addComment(comment: Comment): Comment? = comment
    suspend fun getComments(projectId: Int): List<Comment> = emptyList()
}