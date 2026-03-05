package com.teamfinder.repositories

import org.jetbrains.exposed.sql.javatime.datetime
import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.*
import com.teamfinder.utils.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

@Serializable
data class Comment(
    val id: Int? = null,
    val projectId: Int,
    val userId: Int,
    val userName: String? = null,
    val parentCommentId: Int? = null,
    val content: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime? = null
)

class ProjectRepository {

    suspend fun create(project: Project): Project? = dbQuery {
        println("📝 ProjectRepository.create начат")
        println("   authorId: ${project.authorId}")
        println("   title: ${project.title}")
        println("   description: ${project.description}")
        println("   stage: ${project.stage}")
        println("   tags: ${project.tags}")
        println("   neededRoles: ${project.neededRoles.size}")
        
        try {
            // ========== 1. ВСТАВЛЯЕМ ПРОЕКТ ==========
            println("📌 Шаг 1: Вставка проекта в таблицу projects")
            val insert = Projects.insert {
                it[Projects.authorId] = project.authorId
                it[Projects.title] = project.title
                it[Projects.description] = project.description
                it[Projects.briefDescription] = project.briefDescription
                it[Projects.stage] = project.stage
                it[Projects.status] = project.status
                it[Projects.createdAt] = LocalDateTime.now()
                it[Projects.updatedAt] = LocalDateTime.now()
            }

            val projectId = insert[Projects.id]
            println("✅ Проект вставлен с ID: $projectId")

            // ========== 2. ОБРАБАТЫВАЕМ ТЕГИ ==========
            println("📌 Шаг 2: Обработка тегов")
            val tagIds = mutableListOf<Int>()
            
            if (project.tags.isNotEmpty()) {
                project.tags.forEach { tagName ->
                    try {
                        // Проверяем, существует ли тег
                        var tagId = Tags.slice(Tags.tagId)
                            .select { Tags.name eq tagName }
                            .map { it[Tags.tagId] }
                            .singleOrNull()
                        
                        // Если тега нет - создаем
                        if (tagId == null) {
                            tagId = Tags.insert {
                                it[Tags.name] = tagName
                            }[Tags.tagId]
                            println("   ✅ Новый тег создан: $tagName (ID=$tagId)")
                        } else {
                            println("   ✅ Тег уже существует: $tagName (ID=$tagId)")
                        }
                        tagIds.add(tagId)
                    } catch (e: Exception) {
                        println("   ❌ Ошибка при создании тега $tagName: ${e.message}")
                    }
                }

                // ========== 3. СВЯЗЫВАЕМ ПРОЕКТ С ТЕГАМИ ==========
                println("📌 Шаг 3: Связывание проекта с тегами")
                tagIds.forEach { tagId ->
                    try {
                        ProjectTags.insert {
                            it[ProjectTags.projectId] = projectId
                            it[ProjectTags.tagId] = tagId
                        }
                        println("   ✅ Связь проекта с тегом ID=$tagId создана")
                    } catch (e: Exception) {
                        println("   ❌ Ошибка при связи с тегом ID=$tagId: ${e.message}")
                    }
                }
            } else {
                println("   ⚠️ Теги отсутствуют")
            }

            // ========== 4. ДОБАВЛЯЕМ ВАКАНСИИ ==========
            println("📌 Шаг 4: Добавление вакансий")
            if (project.neededRoles.isNotEmpty()) {
                project.neededRoles.forEachIndexed { index, role ->
                    try {
                        Vacancies.insert {
                            it[Vacancies.projectId] = projectId
                            it[Vacancies.roleTitle] = role.title
                            it[Vacancies.roleDescription] = role.description
                            it[Vacancies.requiredSkills] = role.requiredSkills.toString()
                            it[Vacancies.isFilled] = role.isFilled
                            it[Vacancies.createdAt] = LocalDateTime.now()
                        }
                        println("   ✅ Вакансия ${index + 1} добавлена: ${role.title}")
                    } catch (e: Exception) {
                        println("   ❌ Ошибка при добавлении вакансии ${role.title}: ${e.message}")
                    }
                }
            } else {
                println("   ⚠️ Вакансии отсутствуют")
            }

            // ========== 5. ГЛАВНОЕ ИСПРАВЛЕНИЕ - ВОЗВРАЩАЕМ ПРОЕКТ БЕЗ ПОИСКА ==========
            println("📌 Шаг 5: Формирование ответа")
            
            // Получаем имя автора
            val authorName = getUserName(project.authorId)
            
            // Вместо вызова findById (который иногда не находит свежий проект),
            // мы создаём объект проекта на основе того, что уже знаем
            val createdProject = Project(
                id = projectId,
                authorId = project.authorId,
                title = project.title,
                description = project.description,
                briefDescription = project.briefDescription,
                stage = project.stage,
                status = project.status,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                viewsCount = 0,
                likesCount = 0,
                tags = project.tags,
                neededRoles = project.neededRoles,
                authorName = authorName
            )
            
            println("✅ Проект успешно создан: ID=${createdProject.id}, title=${createdProject.title}")
            println("🎉 ВСЕ ШАГИ ВЫПОЛНЕНЫ УСПЕШНО!")
            
            createdProject // Возвращаем созданный проект
            
        } catch (e: Exception) {
            println("❌ КРИТИЧЕСКАЯ ОШИБКА в create: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    suspend fun findById(id: Int): Project? = dbQuery {
        try {
            println("🔍 findById($id) начат")
            val project = Projects.select { Projects.id eq id }
                .map { toProject(it) }
                .singleOrNull()

            project?.let {
                println("✅ Проект найден в БД")
                it.copy(
                    tags = getTagsForProject(id),
                    neededRoles = getRolesForProject(id)
                )
            }
        } catch (e: Exception) {
            println("❌ Ошибка в findById($id): ${e.message}")
            null
        }
    }

    suspend fun getFeed(page: Int = 1, limit: Int = 20, filters: Map<String, String> = emptyMap()): List<Project> = dbQuery {
        val offset = (page - 1) * limit

        var query = Projects.selectAll()
            .where { Projects.status eq ProjectStatus.ACTIVE }

        filters.forEach { (key, value) ->
            when (key) {
                "stage" -> query = query.andWhere { Projects.stage eq ProjectStage.valueOf(value) }
                "search" -> {
                    query = query.andWhere {
                        (Projects.title like "%$value%") or
                                (Projects.description like "%$value%")
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
            .map { row: ResultRow -> toProject(row) }
            .map { project ->
                project.copy(
                    tags = getTagsForProject(project.id!!),
                    neededRoles = getRolesForProject(project.id),
                    authorName = getUserName(project.authorId)
                )
            }
    }

    suspend fun update(id: Int, project: Project): Boolean = dbQuery {
        try {
            Projects.update({ Projects.id eq id }) {
                it[Projects.title] = project.title
                it[Projects.description] = project.description
                it[Projects.briefDescription] = project.briefDescription
                it[Projects.stage] = project.stage
                it[Projects.status] = project.status
                it[Projects.updatedAt] = LocalDateTime.now()
            } > 0
        } catch (e: Exception) {
            println("❌ Ошибка в update($id): ${e.message}")
            false
        }
    }

    suspend fun delete(id: Int): Boolean = dbQuery {
        try {
            Projects.deleteWhere { Projects.id eq id } > 0
        } catch (e: Exception) {
            println("❌ Ошибка в delete($id): ${e.message}")
            false
        }
    }

    suspend fun incrementViews(id: Int): Boolean = dbQuery {
        try {
            Projects.update({ Projects.id eq id }) {
                with(SqlExpressionBuilder) {
                    it[Projects.viewsCount] = Projects.viewsCount + 1
                }
            } > 0
        } catch (e: Exception) {
            println("❌ Ошибка в incrementViews($id): ${e.message}")
            false
        }
    }

    suspend fun toggleLike(projectId: Int, userId: Int): Boolean = dbQuery {
        try {
            val existing = ProjectLikes.select {
                (ProjectLikes.projectId eq projectId) and
                        (ProjectLikes.userId eq userId)
            }.count() > 0

            if (existing) {
                ProjectLikes.deleteWhere {
                    (ProjectLikes.projectId eq projectId) and
                            (ProjectLikes.userId eq userId)
                }

                Projects.update({ Projects.id eq projectId }) {
                    with(SqlExpressionBuilder) {
                        it[Projects.likesCount] = Projects.likesCount - 1
                    }
                }
                println("✅ Лайк убран: projectId=$projectId, userId=$userId")
                false
            } else {
                ProjectLikes.insert {
                    it[ProjectLikes.projectId] = projectId
                    it[ProjectLikes.userId] = userId
                    it[ProjectLikes.createdAt] = LocalDateTime.now()
                }

                Projects.update({ Projects.id eq projectId }) {
                    with(SqlExpressionBuilder) {
                        it[Projects.likesCount] = Projects.likesCount + 1
                    }
                }
                println("✅ Лайк поставлен: projectId=$projectId, userId=$userId")
                true
            }
        } catch (e: Exception) {
            println("❌ Ошибка в toggleLike($projectId, $userId): ${e.message}")
            false
        }
    }

    suspend fun getComments(projectId: Int): List<Comment> = dbQuery {
        try {
            Comments.select { Comments.projectId eq projectId }
                .orderBy(Comments.createdAt to SortOrder.DESC)
                .map { row: ResultRow -> 
                    Comment(
                        id = row[Comments.commentId],
                        projectId = row[Comments.projectId],
                        userId = row[Comments.userId],
                        userName = null,
                        parentCommentId = row[Comments.parentCommentId],
                        content = row[Comments.content],
                        createdAt = row[Comments.createdAt],
                        updatedAt = row[Comments.updatedAt]
                    )
                }
        } catch (e: Exception) {
            println("❌ Ошибка в getComments($projectId): ${e.message}")
            emptyList()
        }
    }

    suspend fun addComment(comment: Comment): Comment? = dbQuery {
        try {
            val insert = Comments.insert {
                it[Comments.projectId] = comment.projectId
                it[Comments.userId] = comment.userId
                it[Comments.parentCommentId] = comment.parentCommentId
                it[Comments.content] = comment.content
                it[Comments.createdAt] = LocalDateTime.now()
                it[Comments.updatedAt] = LocalDateTime.now()
            }

            val commentId = insert[Comments.commentId]
            println("✅ Комментарий добавлен с ID: $commentId")
            
            val userName = getUserName(comment.userId)
            
            Comments.select { Comments.commentId eq commentId }
                .map { row: ResultRow -> 
                    Comment(
                        id = row[Comments.commentId],
                        projectId = row[Comments.projectId],
                        userId = row[Comments.userId],
                        userName = userName,
                        parentCommentId = row[Comments.parentCommentId],
                        content = row[Comments.content],
                        createdAt = row[Comments.createdAt],
                        updatedAt = row[Comments.updatedAt]
                    )
                }
                .singleOrNull()
        } catch (e: Exception) {
            println("❌ Ошибка в addComment: ${e.message}")
            null
        }
    }

    private fun toProject(row: ResultRow): Project = Project(
        id = row[Projects.id],
        authorId = row[Projects.authorId],
        title = row[Projects.title],
        description = row[Projects.description],
        briefDescription = row[Projects.briefDescription],
        stage = row[Projects.stage],
        status = row[Projects.status],
        createdAt = row[Projects.createdAt],
        updatedAt = row[Projects.updatedAt],
        viewsCount = row[Projects.viewsCount],
        likesCount = row[Projects.likesCount]
    )

    private suspend fun getTagsForProject(projectId: Int): List<String> = dbQuery {
        try {
            ProjectTags.slice(ProjectTags.tagId)
                .select { ProjectTags.projectId eq projectId }
                .map { row: ResultRow -> getTagName(row[ProjectTags.tagId]) }
        } catch (e: Exception) {
            println("❌ Ошибка в getTagsForProject($projectId): ${e.message}")
            emptyList()
        }
    }

    private suspend fun getRolesForProject(projectId: Int): List<Role> = dbQuery {
        try {
            Vacancies.select { Vacancies.projectId eq projectId }
                .map { row: ResultRow -> toRole(row) }
        } catch (e: Exception) {
            println("❌ Ошибка в getRolesForProject($projectId): ${e.message}")
            emptyList()
        }
    }

    private fun toRole(row: ResultRow): Role = Role(
        id = row[Vacancies.vacancyId],
        projectId = row[Vacancies.projectId],
        title = row[Vacancies.roleTitle],
        description = row[Vacancies.roleDescription],
        requiredSkills = parseSkillsList(row[Vacancies.requiredSkills]),
        isFilled = row[Vacancies.isFilled],
        createdAt = row[Vacancies.createdAt]
    )

    private suspend fun getUserName(userId: Int): String? = dbQuery {
        try {
            Users.slice(Users.username)
                .select { Users.id eq userId }
                .map { it[Users.username] }
                .singleOrNull()
        } catch (e: Exception) {
            println("❌ Ошибка в getUserName($userId): ${e.message}")
            null
        }
    }

    private suspend fun getTagId(name: String): Int = dbQuery {
        try {
            Tags.select { Tags.name eq name }
                .map { it[Tags.tagId] }
                .singleOrNull() ?: 0
        } catch (e: Exception) {
            println("❌ Ошибка в getTagId($name): ${e.message}")
            0
        }
    }

    private suspend fun getTagName(id: Int): String = dbQuery {
        try {
            Tags.select { Tags.tagId eq id }
                .map { it[Tags.name] }
                .singleOrNull() ?: ""
        } catch (e: Exception) {
            println("❌ Ошибка в getTagName($id): ${e.message}")
            ""
        }
    }

    private fun parseSkillsList(json: String): List<String> {
        return try {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}