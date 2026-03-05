package com.teamfinder.routes

import com.teamfinder.models.MessageResponse
import com.teamfinder.models.ErrorResponse
import com.teamfinder.models.Project
import com.teamfinder.repositories.ProjectRepository
import com.teamfinder.security.JwtConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString



@Serializable
data class ProjectResponse(
    val id: Int,
    val authorId: Int,
    val title: String,
    val description: String,
    val briefDescription: String,
    val stage: String,
    val status: String,
    val createdAt: String?,
    val updatedAt: String?,
    val viewsCount: Int,
    val likesCount: Int,
    val tags: List<String>,
    val neededRoles: List<RoleResponse>,
    val authorName: String?
)

@Serializable
data class RoleResponse(
    val id: Int?,
    val title: String,
    val description: String,
    val requiredSkills: List<String>,
    val isFilled: Boolean
)

@Serializable
data class CommentResponse(
    val id: Int?,
    val projectId: Int,
    val userId: Int,
    val userName: String?,
    val content: String,
    val createdAt: String?
)

@Serializable
data class LikeResponse(
    val liked: Boolean
)

fun Route.projectRouting(jwtConfig: JwtConfig) {
    
     println("=== PROJECT ROUTES LOADED ===")
     println("✅ ProjectRoutes.kt загружен")
    println("✅ JwtConfig: issuer=${jwtConfig.getIssuer()}")
    val projectRepository = ProjectRepository()

    route("/projects") {

        println("✅ Регистрируем маршруты проектов")

        // ТЕСТОВЫЙ МАРШРУТ
        get("/test") {
            call.respond(mapOf("status" to "ok", "message" to "Project routes are working"))
        }

        // ========== ПУБЛИЧНЫЕ МАРШРУТЫ ==========

        get("/") {
            try {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                val projects = projectRepository.getFeed(page, limit)
                call.respond(HttpStatusCode.OK, projects)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Ошибка сервера"))
            }
        }

        get("/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный ID проекта"))
                    return@get
                }

                val project = projectRepository.findById(id)
                if (project != null) {
                    projectRepository.incrementViews(id)
                    call.respond(HttpStatusCode.OK, project)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Проект не найден"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Ошибка сервера"))
            }
        }

        // ========== ЗАЩИЩЕННЫЕ МАРШРУТЫ ==========
        authenticate("auth-jwt") {

            println("📝 Регистрируем POST /projects")

            post("/") {
                try {
                    println("🔥 POST /projects ВЫЗВАН!")
                    
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()

                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Требуется авторизация"))
                        return@post
                    }

                    val text = call.receive<String>()
                    println("📥 Получен JSON: $text")
                    
                    val project = Json.decodeFromString<Project>(text)
                    val created = projectRepository.create(project.copy(authorId = userId))

                    if (created != null) {
                        call.respond(HttpStatusCode.Created, created)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Не удалось создать проект"))
                    }
                } catch (e: Exception) {
                    println("❌ Ошибка: ${e.message}")
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Неверный формат данных"))
                }
            }

            put("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный ID проекта"))
                        return@put
                    }

                    val text = call.receive<String>()
                    val project = Json.decodeFromString<Project>(text)
                    val updated = projectRepository.update(id, project)

                    if (updated) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Проект успешно обновлён"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Проект не найден"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Неверный формат данных"))
                }
            }

            delete("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный ID проекта"))
                        return@delete
                    }

                    val deleted = projectRepository.delete(id)

                    if (deleted) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Проект успешно удалён"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Проект не найден"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Ошибка сервера"))
                }
            }

            post("/{id}/like") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()

                    if (id == null || userId == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный запрос"))
                        return@post
                    }

                    val liked = projectRepository.toggleLike(id, userId)
                    call.respond(HttpStatusCode.OK, LikeResponse(liked))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Ошибка сервера"))
                }
            }

            post("/{id}/comments") {
                try {
                    val projectId = call.parameters["id"]?.toIntOrNull()
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()

                    if (projectId == null || userId == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный запрос"))
                        return@post
                    }

                    val text = call.receive<String>()
                    val request = Json.decodeFromString<Map<String, String>>(text)
                    val content = request["content"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Текст комментария обязателен")
                    )

                    val comment = projectRepository.addComment(
                        com.teamfinder.repositories.Comment(
                            projectId = projectId,
                            userId = userId,
                            content = content
                        )
                    )

                    if (comment != null) {
                        call.respond(HttpStatusCode.Created, comment)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Не удалось добавить комментарий"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Неверный формат данных"))
                }
            }

            get("/{id}/comments") {
                try {
                    val projectId = call.parameters["id"]?.toIntOrNull()
                    if (projectId == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный ID проекта"))
                        return@get
                    }

                    val comments = projectRepository.getComments(projectId)
                    call.respond(HttpStatusCode.OK, comments)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Ошибка сервера"))
                }
            }
        }
    }
}