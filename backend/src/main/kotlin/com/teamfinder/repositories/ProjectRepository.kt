package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDate
import java.time.LocalDateTime

class ProjectRepository {

    // ========== PROJECTS ==========

    suspend fun create(project: Project): Project? = dbQuery {
        try {
            // 1. Вставляем проект
            val insert = Projects.insert {
                it[authorId] = project.authorId
                it[title] = project.title
                it[description] = project.description
                it[status] = project.status
                it[isActive] = project.isActive
                project.deadline?.let { d -> it[deadline] = d }
                project.industry?.let { i -> it[industry] = i }
                it[createdAt] = LocalDateTime.now()
            }

            val projectId = insert[Projects.id]

            // 2. Теги
            project.tags.forEach { tagName ->
                val tagId = Tags.slice(Tags.tagId)
                    .select { Tags.name eq tagName }
                    .map { it[Tags.tagId] }
                    .singleOrNull()
                    ?: Tags.insert { it[name] = tagName }[tagId]

                ProjectTags.insert {
                    it[ProjectTags.projectId] = projectId
                    it[ProjectTags.tagId] = tagId
                }
            }

            // 3. Роли
            project.roles.forEach { role ->
                ProjectRolesTable.insert {
                    it[projectId] = projectId
                    it[roleName] = role.roleName
                    it[requiredSkills] = Json.encodeToString(ListSerializer(String.serializer()), role.requiredSkills)
                    it[spotsTotal] = role.spotsTotal
                    it[spotsFilled] = role.spotsFilled
                }
            }

            // 4. Возвращаем созданный проект
            Project(
                id = projectId,
                authorId = project.authorId,
                title = project.title,
                description = project.description,
                status = project.status,
                deadline = project.deadline,
                industry = project.industry,
                createdAt = LocalDateTime.now(),
                isActive = true,
                tags = project.tags,
                roles = project.roles,
                authorName = getUserName(project.authorId)
            )
        } catch (e: Exception) {
            println("❌ Ошибка в create: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    suspend fun findById(id: Int): Project? = dbQuery {
        Projects.select { Projects.id eq id }
            .map { toProject(it) }
            .singleOrNull()
            ?.let { project ->
                project.copy(
                    tags = getTagsForProject(id),
                    roles = getRolesForProject(id),
                    authorName = getUserName(project.authorId)
                )
            }
    }

    suspend fun getFeed(page: Int = 1, limit: Int = 20, filters: Map<String, String> = emptyMap()): List<Project> = dbQuery {
        val offset = (page - 1) * limit

        var query = Projects.selectAll().where { Projects.isActive eq true }

        filters.forEach { (key, value) ->
            when (key) {
                "status" -> query = query.andWhere { Projects.status eq value }
                "industry" -> query = query.andWhere { Projects.industry eq value }
                "search" -> {
                    query = query.andWhere {
                        (Projects.title like "%$value%") or (Projects.description like "%$value%")
                    }
                }
                "tag" -> {
                    val projectIds = ProjectTags.slice(ProjectTags.projectId)
                        .select { ProjectTags.tagId eq getTagId(value) }
                        .map { it[ProjectTags.projectId] }
                    query = query.andWhere { Projects.id inList projectIds }
                }
            }
        }

        query.limit(limit, offset.toLong())
            .orderBy(Projects.createdAt to SortOrder.DESC)
            .map { toProject(it) }
            .map { project ->
                project.copy(
                    tags = getTagsForProject(project.id!!),
                    roles = getRolesForProject(project.id),
                    authorName = getUserName(project.authorId)
                )
            }
    }

    suspend fun update(id: Int, project: Project): Boolean = dbQuery {
        Projects.update({ Projects.id eq id }) {
            it[title] = project.title
            it[description] = project.description
            it[status] = project.status
            project.deadline?.let { d -> it[deadline] = d }
            project.industry?.let { i -> it[industry] = i }
            it[isActive] = project.isActive
        } > 0
    }

    suspend fun delete(id: Int): Boolean = dbQuery {
        Projects.update({ Projects.id eq id }) {
            it[isActive] = false
        } > 0
    }

    // ========== ROLES ==========

    suspend fun getRolesForProject(projectId: Int): List<ProjectRole> = dbQuery {
        ProjectRolesTable.select { ProjectRolesTable.projectId eq projectId }
            .map { row ->
                ProjectRole(
                    id = row[ProjectRolesTable.id],
                    projectId = row[ProjectRolesTable.projectId],
                    roleName = row[ProjectRolesTable.roleName],
                    requiredSkills = parseJsonList(row[ProjectRolesTable.requiredSkills]),
                    spotsTotal = row[ProjectRolesTable.spotsTotal],
                    spotsFilled = row[ProjectRolesTable.spotsFilled]
                )
            }
    }

    // ========== TAGS ==========

    private suspend fun getTagsForProject(projectId: Int): List<String> = dbQuery {
        ProjectTags.slice(ProjectTags.tagId)
            .select { ProjectTags.projectId eq projectId }
            .map { getTagName(it[ProjectTags.tagId]) }
    }

    private suspend fun getTagName(id: Int): String = dbQuery {
        Tags.select { Tags.tagId eq id }
            .map { it[Tags.name] }
            .singleOrNull() ?: ""
    }

    private suspend fun getTagId(name: String): Int = dbQuery {
        Tags.select { Tags.name eq name }
            .map { it[Tags.tagId] }
            .singleOrNull() ?: 0
    }

    // ========== COMMENTS (убраны — в новой схеме нет таблицы comments) ==========
    // Если нужны комментарии — добавьте таблицу Comments в БД

    // ========== HELPER ==========

    private fun toProject(row: ResultRow): Project = Project(
        id = row[Projects.id],
        authorId = row[Projects.authorId],
        title = row[Projects.title],
        description = row[Projects.description],
        status = row[Projects.status],
        deadline = row[Projects.deadline],
        industry = row[Projects.industry],
        createdAt = row[Projects.createdAt],
        isActive = row[Projects.isActive]
    )

    private suspend fun getUserName(userId: Int): String? = dbQuery {
        Users.slice(Users.username)
            .select { Users.id eq userId }
            .map { it[Users.username] }
            .singleOrNull()
    }

    private fun parseJsonList(json: String): List<String> {
        return try {
            Json.decodeFromString<List<String>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
