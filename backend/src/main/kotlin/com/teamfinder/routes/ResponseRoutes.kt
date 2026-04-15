package com.teamfinder.routes

import com.teamfinder.models.ErrorResponse
import com.teamfinder.models.MessageResponse
import com.teamfinder.models.Response
import com.teamfinder.repositories.ProjectRepository
import com.teamfinder.repositories.ResponseRepository
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
data class CreateResponseRequest(
    val projectId: Int,
    val roleId: Int? = null,
    val message: String? = null
)

@Serializable
data class UpdateResponseStatusRequest(
    val status: String
)

fun Route.responseRouting(jwtConfig: JwtConfig) {
    val responseRepository = ResponseRepository()
    val projectRepository = ProjectRepository()

    route("/responses") {

        // ========== PUBLIC ==========

        // Все отклики на проект (для автора проекта)
        get("/project/{projectId}") {
            try {
                val projectId = call.parameters["projectId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid project ID"))

                val responses = responseRepository.findByProject(projectId)
                call.respond(HttpStatusCode.OK, responses)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
            }
        }

        // ========== PROTECTED ==========
        authenticate("auth-jwt") {

            // Создать отклик
            post("/") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val text = call.receive<String>()
                    val request = Json.decodeFromString<CreateResponseRequest>(text)

                    // Проверяем, что проект существует
                    val project = projectRepository.findById(request.projectId)
                        ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Project not found"))

                    // Проверяем, не откликался ли уже
                    val existing = responseRepository.findByProjectAndUser(request.projectId, userId)
                    if (existing != null) {
                        return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("You already responded to this project"))
                    }

                    val response = Response(
                        projectId = request.projectId,
                        userId = userId,
                        roleId = request.roleId,
                        message = request.message,
                        status = "рассматривается"
                    )

                    val created = responseRepository.create(response)
                    if (created != null) {
                        call.respond(HttpStatusCode.Created, created)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to create response"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid data format: ${e.message}"))
                }
            }

            // Мои отклики
            get("/my") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val responses = responseRepository.findByUser(userId)
                    call.respond(HttpStatusCode.OK, responses)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }

            // Получить отклик по ID
            get("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid ID"))

                    val response = responseRepository.findById(id)
                        ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Response not found"))

                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }

            // Обновить статус отклика (только автор проекта)
            put("/{id}/status") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid ID"))

                    val principal = call.principal<JWTPrincipal>()
                    val currentUserId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val response = responseRepository.findById(id)
                        ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Response not found"))

                    // Проверяем, что текущий пользователь — автор проекта
                    val project = projectRepository.findById(response.projectId)
                        ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Project not found"))

                    if (project.authorId != currentUserId) {
                        return@put call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only project author can change response status"))
                    }

                    val text = call.receive<String>()
                    val request = Json.decodeFromString<UpdateResponseStatusRequest>(text)

                    val validStatuses = listOf("рассматривается", "принят", "отклонён")
                    if (request.status !in validStatuses) {
                        return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid status. Allowed: ${validStatuses.joinToString(", ")}"))
                    }

                    val updated = responseRepository.updateStatus(id, request.status)
                    if (updated) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Response status updated to '${request.status}'"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to update status"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid data format: ${e.message}"))
                }
            }

            // Обновить свой отклик
            put("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid ID"))

                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val response = responseRepository.findById(id)
                        ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Response not found"))

                    if (response.userId != userId) {
                        return@put call.respond(HttpStatusCode.Forbidden, ErrorResponse("You can only edit your own responses"))
                    }

                    val text = call.receive<String>()
                    val updates = Json.decodeFromString<Map<String, String?>>(text)

                    val updatedResponse = response.copy(
                        message = updates["message"] ?: response.message,
                        roleId = updates["roleId"]?.toIntOrNull() ?: response.roleId
                    )

                    val updated = responseRepository.update(id, updatedResponse)
                    if (updated) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Response updated"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to update response"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid data format: ${e.message}"))
                }
            }

            // Отменить отклик
            delete("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid ID"))

                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val response = responseRepository.findById(id)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Response not found"))

                    if (response.userId != userId) {
                        return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("You can only delete your own responses"))
                    }

                    val deleted = responseRepository.delete(id)
                    if (deleted) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Response deleted"))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete response"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }
        }
    }
}
