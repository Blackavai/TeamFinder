package com.teamfinder.routes

import com.teamfinder.repositories.MessageRepository
import com.teamfinder.services.ChatService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Duration

fun Route.chatRoutes(chatService: ChatService, messageRepository: MessageRepository) {
    
    authenticate("auth-jwt") {
        
        // Эндпоинт для загрузки истории чата
        get("/chat/{chatType}/{chatId}/history") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asInt()
                ?: return@get call.respond(401)
            
            val chatType = call.parameters["chatType"] ?: return@get call.respond(400)
            val chatId = call.parameters["chatId"]?.toIntOrNull() ?: return@get call.respond(400)
            
            // TODO: ВАЖНО! Добавить проверку, имеет ли userId доступ к этому чату
            
            val history = messageRepository.getMessagesForChat(chatId, chatType)
            call.respond(history)
        }
        
        // Основной эндпоинт для WebSocket-соединения
        webSocket("/chat") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asInt()
                ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            val username = principal.payload.getClaim("username").asString() // Предполагаем, что имя есть в токене

            // Первое сообщение от клиента должно быть "join" сообщением, чтобы мы знали, к какой комнате его подключить
            val joinFrame = incoming.receive() as? Frame.Text
            if (joinFrame == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Expected join message"))
                return@webSocket
            }

            // Пример join-сообщения: "team-123"
            val chatIdentifier = joinFrame.readText()
            
            // TODO: Проверить, имеет ли юзер право на этот чат!
            
            try {
                chatService.onJoin(chatIdentifier, this)
                // Читаем входящие сообщения
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        chatService.onMessageReceived(userId, username, text)
                    }
                }
            } catch (e: Exception) {
                println("WebSocket error: ${e.localizedMessage}")
            } finally {
                // Обязательно убираем сессию при отключении
                chatService.onLeave(chatIdentifier, this)
            }
        }
    }
}