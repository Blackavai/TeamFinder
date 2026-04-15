package com.teamfinder.routes

import com.teamfinder.models.ErrorResponse
import com.teamfinder.models.Invitation
import com.teamfinder.models.MessageResponse
import com.teamfinder.repositories.InvitationRepository
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
data class CreateInvitationRequest(
    val projectId: Int,
    val toUserId: Int,
    val roleId: Int? = null,
    val message: String? = null
)

@Serializable
data class UpdateInvitationStatusRequest(
    val status: String
)

fun Route.invitationRouting(jwtConfig: JwtConfig) {
    val invitationRepository = InvitationRepository()
    val projectRepository = ProjectRepository()

    route("/invitations") {

        // ========== PROTECTED ==========
        authenticate("auth-jwt") {

            // Создать приглашение
            post("/") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val fromUserId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val text = call.receive<String>()
                    val request = Json.decodeFromString<CreateInvitationRequest>(text)

                    // Проверяем, что проект существует и пользователь — автор
                    val project = projectRepository.findById(request.projectId)
                        ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Project not found"))

                    if (project.authorId != fromUserId) {
                        return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only project author can send invitations"))
                    }

                    // Проверяем, не приглашали ли уже
                    val existing = invitationRepository.findByProjectAndUsers(
                        request.projectId, fromUserId, request.toUserId
                    )
                    if (existing != null) {
                        return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("Invitation already exists"))
                    }

                    val invitation = Invitation(
                        projectId = request.projectId,
                        fromUserId = fromUserId,
                        toUserId = request.toUserId,
                        roleId = request.roleId,
                        message = request.message,
                        status = "отправлено"
                    )

                    val created = invitationRepository.create(invitation)
                    if (created != null) {
                        call.respond(HttpStatusCode.Created, created)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to create invitation"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid data format: ${e.message}"))
                }
            }

            // Приглашения, полученные мной
            get("/received") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val invitations = invitationRepository.findByToUser(userId)
                    call.respond(HttpStatusCode.OK, invitations)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }

            // Ожидающие приглашения для меня
            get("/received/pending") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val invitations = invitationRepository.getPendingForUser(userId)
                    call.respond(HttpStatusCode.OK, invitations)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }

            // Приглашения, отправленные мной
            get("/sent") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val invitations = invitationRepository.findByFromUser(userId)
                    call.respond(HttpStatusCode.OK, invitations)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }

            // Все приглашения на проект (для автора)
            get("/project/{projectId}") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val currentUserId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val projectId = call.parameters["projectId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid project ID"))

                    val project = projectRepository.findById(projectId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Project not found"))

                    if (project.authorId != currentUserId) {
                        return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only project author can view invitations"))
                    }

                    val invitations = invitationRepository.findByProject(projectId)
                    call.respond(HttpStatusCode.OK, invitations)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }

            // Получить приглашение по ID
            get("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid ID"))

                    val invitation = invitationRepository.findById(id)
                        ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Invitation not found"))

                    call.respond(HttpStatusCode.OK, invitation)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }

            // Ответить на приглашение (принять/отклонить)
            put("/{id}/status") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid ID"))

                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val invitation = invitationRepository.findById(id)
                        ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Invitation not found"))

                    // Только адресат может ответить
                    if (invitation.toUserId != userId) {
                        return@put call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only the invited user can respond"))
                    }

                    val text = call.receive<String>()
                    val request = Json.decodeFromString<UpdateInvitationStatusRequest>(text)

                    val validStatuses = listOf("принято", "отклонено")
                    if (request.status !in validStatuses) {
                        return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid status. Allowed: ${validStatuses.joinToString(", ")}"))
                    }

                    val updated = invitationRepository.updateStatus(id, request.status)
                    if (updated) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Invitation status updated to '${request.status}'"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to update status"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid data format: ${e.message}"))
                }
            }

            // Обновить приглашение
            put("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid ID"))

                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val invitation = invitationRepository.findById(id)
                        ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Invitation not found"))

                    if (invitation.fromUserId != userId) {
                        return@put call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only sender can edit invitation"))
                    }

                    val text = call.receive<String>()
                    val updates = Json.decodeFromString<Map<String, String?>>(text)

                    val updatedInvitation = invitation.copy(
                        message = updates["message"] ?: invitation.message,
                        roleId = updates["roleId"]?.toIntOrNull() ?: invitation.roleId
                    )

                    val updated = invitationRepository.update(id, updatedInvitation)
                    if (updated) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Invitation updated"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to update invitation"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid data format: ${e.message}"))
                }
            }

            // Отменить приглашение
            delete("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid ID"))

                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val invitation = invitationRepository.findById(id)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Invitation not found"))

                    if (invitation.fromUserId != userId) {
                        return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only sender can cancel invitation"))
                    }

                    val deleted = invitationRepository.delete(id)
                    if (deleted) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Invitation cancelled"))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to cancel invitation"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }
        }
    }
}
