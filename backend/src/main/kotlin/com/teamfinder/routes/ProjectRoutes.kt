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
                    projectRepository.incrementViews(id)
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
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid data format"))
                }
            }

            // UPDATED PUT (WITH OWNERSHIP CHECK)
            put("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull() 
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid project ID"))
                    
                    val principal = call.principal<JWTPrincipal>()
                    val currentUserId = principal?.payload?.subject?.toIntOrNull() 
                        ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    // 1. Fetch project from DB to verify authorship
                    val existingProject = projectRepository.findById(id) 
                        ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Project not found"))

                    // 2. SECURITY CHECK: Compare authorId with current user ID
                    if (existingProject.authorId != currentUserId) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied: You are not the author of this project"))
                        return@put
                    }

                    // 3. Update the data
                    val text = call.receive<String>()
                    val projectData = Json.decodeFromString<Project>(text)
                    val updated = projectRepository.update(id, projectData)

                    if (updated) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Project updated successfully"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to update project"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid data format"))
                }
            }

            // UPDATED DELETE (WITH OWNERSHIP CHECK)
            delete("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull() 
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid project ID"))
                    
                    val principal = call.principal<JWTPrincipal>()
                    val currentUserId = principal?.payload?.subject?.toIntOrNull() 
                        ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    // 1. Fetch project from DB
                    val project = projectRepository.findById(id) 
                        ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Project not found"))

                    // 2. SECURITY CHECK
                    if (project.authorId != currentUserId) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied: You cannot delete someone else's project"))
                        return@delete
                    }

                    // 3. Delete from DB
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

            post("/{id}/like") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val userId = call.principal<JWTPrincipal>()?.payload?.subject?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    
                    val liked = projectRepository.toggleLike(id, userId)
                    call.respond(HttpStatusCode.OK, LikeResponse(liked))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Action failed"))
                }
            }

            post("/{id}/comments") {
                try {
                    val projectId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val userId = call.principal<JWTPrincipal>()?.payload?.subject?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.Unauthorized)

                    val text = call.receive<String>()
                    val request = Json.decodeFromString<Map<String, String>>(text)
                    val content = request["content"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Comment content is required"))

                    val comment = projectRepository.addComment(
                        com.teamfinder.repositories.Comment(projectId = projectId, userId = userId, content = content)
                    )
                    
                    if (comment != null) {
                        call.respond(HttpStatusCode.Created, comment)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to add comment"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid data format"))
                }
            }

            get("/{id}/comments") {
                val projectId = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                val comments = projectRepository.getComments(projectId)
                call.respond(HttpStatusCode.OK, comments)
            }
        }
    }
}