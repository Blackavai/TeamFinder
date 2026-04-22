package com.teamfinder.routes

import com.teamfinder.models.CreateProjectRequest
import com.teamfinder.repositories.ProjectRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.projectRoutes(projectRepository: ProjectRepository) {
    route("/projects") {
        
        // 1. Получить ленту проектов с фильтрацией (публичный доступ)
        get {
            // Извлекаем query-параметры из URL
            val tags = call.request.queryParameters["tags"]?.split(",")?.filter { it.isNotBlank() }
            val industry = call.request.queryParameters["industry"]
            val status = call.request.queryParameters["status"]
            
            val projects = projectRepository.searchProjects(
                tags = tags,
                industry = industry,
                status = status
            )
            call.respond(HttpStatusCode.OK, projects)
        }

        // 2. Получить конкретный проект по ID (публичный доступ)
        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный ID проекта"))
                return@get
            }
            
            val project = projectRepository.getProjectById(id)
            if (project != null) {
                call.respond(HttpStatusCode.OK, project)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Проект не найден"))
            }
        }

        // 3. Создать новый проект (только для авторизованных)
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                try {
                    val request = call.receive<CreateProjectRequest>()
                    val newProject = projectRepository.createProject(userId, request)
                    
                    if (newProject != null) {
                        call.respond(HttpStatusCode.Created, newProject)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Ошибка создания проекта"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Неверный формат данных: ${e.message}"))
                }
            }
        }
    }
}