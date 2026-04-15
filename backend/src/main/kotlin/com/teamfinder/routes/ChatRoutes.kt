package com.teamfinder.routes

import com.teamfinder.models.ChatInfo
import com.teamfinder.models.ChatMessage
import com.teamfinder.models.ChatType
import com.teamfinder.models.ErrorResponse
import com.teamfinder.security.JwtConfig
import com.teamfinder.services.ChatService
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
data class ChatInfoResponse(
    val chatType: String,
    val chatId: Int,
    val participants: List<Int>,
    val lastMessage: ChatMessage?,
    val unreadCount: Int = 0
)

@Serializable
data class SendMessageRequest(
    val chatType: String,
    val chatId: Int,
    val content: String
)

@Serializable
data class GetMessageHistoryRequest(
    val limit: Int = 50,
    val offset: Int = 0
)

fun Route.chatRouting(jwtConfig: JwtConfig, chatService: ChatService) {
    route("/chats") {
        authenticate("auth-jwt") {

            // ========== СПИСОК ЧАТОВ ==========
            get("/") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val chats = chatService.getUserChats(userId)
                    val response = chats.map { chat ->
                        ChatInfoResponse(
                            chatType = chat.chatType.name,
                            chatId = chat.chatId,
                            participants = chat.participants,
                            lastMessage = chat.lastMessage
                        )
                    }
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }

            // ========== ИНФОРМАЦИЯ О ЧАТЕ ==========
            get("/{chatType}/{chatId}") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val chatTypeStr = call.parameters["chatType"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("chatType required"))
                    val chatId = call.parameters["chatId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chatId"))

                    val chatType = try {
                        ChatType.valueOf(chatTypeStr.uppercase())
                    } catch (e: IllegalArgumentException) {
                        return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chatType. Allowed: TEAM, RESPONSE"))
                    }

                    val chatInfo = chatService.getChatInfo(chatType, chatId, userId)
                        ?: return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("You are not a participant of this chat"))

                    call.respond(HttpStatusCode.OK, ChatInfoResponse(
                        chatType = chatInfo.chatType.name,
                        chatId = chatInfo.chatId,
                        participants = chatInfo.participants,
                        lastMessage = chatInfo.lastMessage
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }

            // ========== ИСТОРИЯ СООБЩЕНИЙ ==========
            get("/{chatType}/{chatId}/messages") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val chatTypeStr = call.parameters["chatType"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("chatType required"))
                    val chatId = call.parameters["chatId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chatId"))

                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                    val chatType = try {
                        ChatType.valueOf(chatTypeStr.uppercase())
                    } catch (e: IllegalArgumentException) {
                        return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chatType. Allowed: TEAM, RESPONSE"))
                    }

                    val messages = chatService.getMessages(chatType, chatId, userId, limit, offset)
                        ?: return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("You are not a participant of this chat"))

                    call.respond(HttpStatusCode.OK, messages)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }

            // ========== ОТПРАВИТЬ СООБЩЕНИЕ (REST-альтернатива WebSocket) ==========
            post("/{chatType}/{chatId}/messages") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val chatTypeStr = call.parameters["chatType"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("chatType required"))
                    val chatId = call.parameters["chatId"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chatId"))

                    val chatType = try {
                        ChatType.valueOf(chatTypeStr.uppercase())
                    } catch (e: IllegalArgumentException) {
                        return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chatType. Allowed: TEAM, RESPONSE"))
                    }

                    val text = call.receive<String>()
                    val request = Json.decodeFromString<SendMessageRequest>(text)

                    val message = chatService.sendMessage(userId, chatType, chatId, request.content)
                        ?: return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("You are not a participant of this chat"))

                    call.respond(HttpStatusCode.Created, message)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid data format: ${e.message}"))
                }
            }

            // ========== ОТМЕТИТЬ КАК ПРОЧИТАННОЕ ==========
            post("/{chatType}/{chatId}/read") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val chatTypeStr = call.parameters["chatType"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("chatType required"))
                    val chatId = call.parameters["chatId"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chatId"))

                    val chatType = try {
                        ChatType.valueOf(chatTypeStr.uppercase())
                    } catch (e: IllegalArgumentException) {
                        return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid chatType. Allowed: TEAM, RESPONSE"))
                    }

                    chatService.markMessagesAsRead(userId, chatType, chatId)
                    call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }
        }
    }
}
