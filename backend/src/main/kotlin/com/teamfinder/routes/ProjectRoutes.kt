package com.teamfinder.routes

import com.teamfinder.models.* // Важно: импортируем всё из моделей, включая Comment
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

// --- ОБНОВЛЕННЫЕ ОТВЕТЫ (DTO) ПОД НОВУЮ БД ---

@Serializable
data class ProjectResponse(
    val id: Int,
    val authorId: Int,
    val title: String,
    val description: String?,
    val status: String,
    val industry: String?,
    val createdAt: String?,
    val authorName: String?,
    val roles: List<Role> = emptyList()
)

@Serializable
data class LikeResponse(val liked: Boolean)

fun Route.projectRouting(jwtConfig: JwtConfig) {
    val projectRepository = ProjectRepository()

    route("/projects") {

        // Test Endpoint
        get("/test") {
            call.respond(mapOf("status" to "ok", "message" to "Project routes are working"))
        }

        // ========== PUBLIC ROUTES ==========

        get("/") {
            try {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val projects = projectRepository.getFeed(page, limit)
                
                // Мапим в ProjectResponse
                val response = projects.map { it.toResponse() }
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Server error occurred"))
            }
        }

        get("/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull() 
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid ID"))

                val project = projectRepository.findById(id)
                if (project != null) {
                    projectRepository.incrementViews(id)
                    call.respond(HttpStatusCode.OK, project.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Project not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Server error"))
            }
        }

        // ========== PROTECTED ROUTES ==========
        authenticate("auth-jwt") {

            post("/") {
                try {
                    val userId = call.principal<JWTPrincipal>()?.payload?.subject?.toIntOrNull() 
                        ?: return@post call.respond(HttpStatusCode.Unauthorized)

                    val project = call.receive<Project>()
                    val created = projectRepository.create(project.copy(authorId = userId))

                    if (created != null) {
                        call.respond(HttpStatusCode.Created, created)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to create project"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid format"))
                }
            }

            put("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val userId = call.principal<JWTPrincipal>()?.payload?.subject?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.Unauthorized)

                    val existing = projectRepository.findById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
                    if (existing.authorId != userId) {
                        return@put call.respond(HttpStatusCode.Forbidden, ErrorResponse("Not your project"))
                    }

                    val update = call.receive<Project>()
                    if (projectRepository.update(id, update)) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Updated successfully"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Update failed"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid format"))
                }
            }

            delete("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val userId = call.principal<JWTPrincipal>()?.payload?.subject?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                val project = projectRepository.findById(id) ?: return@delete call.respond(HttpStatusCode.NotFound)
                if (project.authorId != userId) {
                    return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
                }

                if (projectRepository.delete(id)) {
                    call.respond(HttpStatusCode.OK, MessageResponse("Deleted successfully"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Delete failed"))
                }
            }

            post("/{id}/like") {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val userId = call.principal<JWTPrincipal>()?.payload?.subject?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val liked = projectRepository.toggleLike(id, userId)
                call.respond(HttpStatusCode.OK, LikeResponse(liked))
            }

            post("/{id}/comments") {
                try {
                    val projectId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val userId = call.principal<JWTPrincipal>()?.payload?.subject?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val req = call.receive<Map<String, String>>()
                    val content = req["content"] ?: return@post call.respond(HttpStatusCode.BadRequest)

                    // Используем Comment из Models.kt
                    val comment = projectRepository.addComment(
                        Comment(projectId = projectId, userId = userId, content = content)
                    )
                    call.respond(HttpStatusCode.Created, comment!!)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Format error"))
                }
            }

            get("/{id}/comments") {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                val comments = projectRepository.getComments(id)
                call.respond(HttpStatusCode.OK, comments)
            }
        }
    }
}

// Помощник для маппинга
fun Project.toResponse() = ProjectResponse(
    id = this.id!!,
    authorId = this.authorId,
    title = this.title,
    description = this.description,
    status = this.status,
    industry = this.industry,
    createdAt = this.createdAt,
    authorName = this.authorName,
    roles = this.roles
)