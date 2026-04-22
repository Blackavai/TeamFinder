package com.teamfinder.repositories

import com.teamfinder.database.DatabaseFactory.dbQuery
import com.teamfinder.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDate
import java.time.LocalDateTime

class ProjectRepository {

    // 1. Создание проекта со всеми связями
    suspend fun createProject(authorId: Int, request: CreateProjectRequest): ProjectDTO? = dbQuery {
        
        // Шаг 1: Создаем сам проект
        val insertProject = Projects.insert {
            it[this.authorId] = authorId
            it[title] = request.title
            it[description] = request.description
            it[deadline] = request.deadline?.let { d -> LocalDate.parse(d) }
            it[industry] = request.industry
            it[createdAt] = LocalDateTime.now()
        }
        
        val newProjectId = insertProject.resultedValues?.singleOrNull()?.get(Projects.projectId)
            ?: return@dbQuery null

        // Шаг 2: Обрабатываем и привязываем теги
        val tagIds = request.tags.map { tagName ->
            val normalizedTag = tagName.lowercase().trim() // Защита от дублей из-за регистра
            
            // Ищем тег в БД
            val existingTagId = Tags.select { Tags.name eq normalizedTag }.singleOrNull()?.get(Tags.tagId)
            
            if (existingTagId != null) {
                existingTagId // Если есть, берем его ID
            } else {
                // Если нет, создаем новый
                Tags.insert { 
                    it[name] = normalizedTag
                    it[category] = "skill" // По умолчанию
                }.resultedValues?.single()?.get(Tags.tagId)!!
            }
        }

        // Привязываем теги к проекту
        tagIds.forEach { tId ->
            ProjectTags.insert {
                it[projectId] = newProjectId
                it[tagId] = tId
            }
        }

        // Шаг 3: Создаем роли
        request.roles.forEach { roleRequest ->
            ProjectRoles.insert {
                it[projectId] = newProjectId
                it[roleName] = roleRequest.roleName
                it[requiredSkills] = roleRequest.requiredSkills
                it[spotsTotal] = roleRequest.spotsTotal
            }
        }

        // Возвращаем свежесозданный проект (вызываем нашу же функцию получения)
        getProjectByIdInternal(newProjectId)
    }

    // 2. Получение проекта по ID
    suspend fun getProjectById(projectId: Int): ProjectDTO? = dbQuery {
        getProjectByIdInternal(projectId)
    }

    // 3. Получение всех активных проектов (Лента)
    suspend fun getAllProjects(): List<ProjectDTO> = dbQuery {
        // Берем все проекты, сортируем по дате создания (свежие сверху)
        Projects.select { Projects.isActive eq true }
            .orderBy(Projects.createdAt to SortOrder.DESC)
            .map { row -> 
                // Собираем полную DTO для каждого проекта
                getProjectByIdInternal(row[Projects.projectId])!! 
            }
    }

    // 4. Поиск и фильтрация проектов
    suspend fun searchProjects(
        tags: List<String>? = null, // Список тегов для фильтрации
        industry: String? = null,    // Сфера деятельности
        status: String? = null       // Статус проекта
    ): List<ProjectDTO> = dbQuery {
        
        // Начинаем с базового запроса
        val query = Projects.selectAll()

        // Динамически добавляем условия

        // Фильтр по сфере
        industry?.let {
            query.andWhere { Projects.industry.lowerCase() eq it.lowercase() }
        }

        // Фильтр по статусу
        status?.let {
            query.andWhere { Projects.status.lowerCase() eq it.lowercase() }
        }

        // Фильтр по тегам (самый сложный)
        tags?.let { tagList ->
            if (tagList.isNotEmpty()) {
                val normalizedTags = tagList.map { it.lowercase().trim() }
                
                // Подзапрос для поиска ID проектов, которые содержат ВСЕ указанные теги
                val projectIdsWithAllTags = ProjectTags
                    .innerJoin(Tags)
                    .slice(ProjectTags.projectId)
                    .select { Tags.name.lowerCase() inList normalizedTags }
                    .groupBy(ProjectTags.projectId)
                    .having { count(Tags.tagId) eq normalizedTags.size } // Магия здесь: убеждаемся, что найдено ровно столько тегов, сколько искали
                    .map { it[ProjectTags.projectId] }
                
                query.andWhere { Projects.projectId inList projectIdsWithAllTags }
            }
        }
        
        // Добавляем сортировку и выполняем запрос
        query.orderBy(Projects.createdAt to SortOrder.DESC)
            .map { row ->
                // Используем наш существующий метод для сборки DTO
                getProjectByIdInternal(row[Projects.projectId])!!
            }
    }

    // Внутренняя функция для сборки ProjectDTO (чтобы использовать внутри транзакции dbQuery)
    private fun getProjectByIdInternal(id: Int): ProjectDTO? {
        val projectRow = Projects.select { Projects.projectId eq id }.singleOrNull() ?: return null

        // Достаем названия тегов через JOIN таблиц ProjectTags и Tags
        val tags = (ProjectTags innerJoin Tags)
            .select { ProjectTags.projectId eq id }
            .map { it[Tags.name] }

        // Достаем роли
        val roles = ProjectRoles.select { ProjectRoles.projectId eq id }.map {
            RoleDTO(
                roleId = it[ProjectRoles.roleId],
                roleName = it[ProjectRoles.roleName],
                requiredSkills = it[ProjectRoles.requiredSkills],
                spotsTotal = it[ProjectRoles.spotsTotal],
                spotsFilled = it[ProjectRoles.spotsFilled]
            )
        }

        return ProjectDTO(
            projectId = projectRow[Projects.projectId],
            authorId = projectRow[Projects.authorId],
            title = projectRow[Projects.title],
            description = projectRow[Projects.description],
            status = projectRow[Projects.status],
            deadline = projectRow[Projects.deadline]?.toString(),
            industry = projectRow[Projects.industry],
            isActive = projectRow[Projects.isActive],
            tags = tags,
            roles = roles
        )
    }
}