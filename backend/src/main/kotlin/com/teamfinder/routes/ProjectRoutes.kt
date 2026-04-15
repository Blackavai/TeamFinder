package com.teamfinder.routes

import com.teamfinder.models.ErrorResponse
import com.teamfinder.models.MessageResponse
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

@Serializable
data class ProjectResponse(
    val id: Int,
    val authorId: Int,
    val title: String,
    val description: String?,
    val status: String,
    val deadline: String?,
    val industry: String?,
    val createdAt: String?,
    val isActive: Boolean,
    val tags: List<String>,
    val roles: List<RoleResponse>,
    val authorName: String?
)

@Serializable
data class RoleResponse(
    val id: Int?,
    val roleName: String,
    val requiredSkills: List<String>,
    val spotsTotal: Int,
    val spotsFilled: Int
)

fun Route.projectRouting(jwtConfig: JwtConfig) {
    val projectRepository = ProjectRepository()

    route("/projects") {

        get("/test") {
            call.respond(mapOf("status" to "ok", "message" to "Project routes are working"))
        }

        // ========== PUBLIC ROUTES ==========

        get("/") {
            try {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val projects = projectRepository.getFeed(page, limit)
                call.respond(HttpStatusCode.OK, projects)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Server error occurred"))
            }
        }

        get("/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid project ID"))

                val project = projectRepository.findById(id)
                if (project != null) {
                    call.respond(HttpStatusCode.OK, project)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Project not found"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Server error occurred"))
            }
        }

        // ========== PROTECTED ROUTES ==========
        authenticate("auth-jwt") {

            post("/") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val text = call.receive<String>()
                    val project = Json.decodeFromString<Project>(text)
                    val created = projectRepository.create(project.copy(authorId = userId))

                    if (created != null) {
                        call.respond(HttpStatusCode.Created, created)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to create project"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid data format: ${e.message}"))
                }
            }

            put("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid project ID"))

                    val principal = call.principal<JWTPrincipal>()
                    val currentUserId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val existingProject = projectRepository.findById(id)
                        ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Project not found"))

                    if (existingProject.authorId != currentUserId) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied: You are not the author"))
                        return@put
                    }

                    val text = call.receive<String>()
                    val projectData = Json.decodeFromString<Project>(text)
                    val updated = projectRepository.update(id, projectData)

                    if (updated) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Project updated successfully"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to update project"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid data format: ${e.message}"))
                }
            }

            delete("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid project ID"))

                    val principal = call.principal<JWTPrincipal>()
                    val currentUserId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val project = projectRepository.findById(id)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Project not found"))

                    if (project.authorId != currentUserId) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied: You cannot delete someone else's project"))
                        return@delete
                    }

                    val deleted = projectRepository.delete(id)
                    if (deleted) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Project deleted successfully"))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete project"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Server error occurred"))
                }
            }
        }
    }
}
