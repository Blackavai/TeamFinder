package com.teamfinder.plugins

import com.teamfinder.models.ChatMessage
import com.teamfinder.models.ChatType
import com.teamfinder.services.ChatService
import com.teamfinder.services.IncomingMessage
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration

fun Application.configureSockets(chatService: ChatService) {
    val logger = LoggerFactory.getLogger("WebSocket")

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(30)
        timeout = Duration.ofSeconds(60)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket("/ws/chat") {
            val userId = call.request.queryParameters["userId"]?.toIntOrNull()
            if (userId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "userId query parameter required"))
                return@webSocket
            }

            logger.info("WebSocket: user $userId connecting...")
            val sendChannel = chatService.connect(userId)

            // Запускаем корутину для отправки сообщений из ChatService клиенту
            val sendJob = launch {
                for (message in sendChannel) {
                    try {
                        send(Frame.Text(message))
                    } catch (e: Exception) {
                        logger.error("Failed to send message to user $userId: ${e.message}")
                        break
                    }
                }
            }

            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            logger.info("Received from user $userId: $text")

                            try {
                                val incoming = Json.decodeFromString<IncomingMessage>(text)

                                when (incoming.type) {
                                    "message" -> {
                                        val content = incoming.content ?: continue
                                        val chatType = ChatType.valueOf(incoming.chatType.uppercase())

                                        val savedMessage = chatService.sendMessage(
                                            senderId = userId,
                                            chatType = chatType,
                                            chatId = incoming.chatId,
                                            content = content
                                        )

                                        if (savedMessage == null) {
                                            send(Frame.Text(Json.encodeToString(mapOf(
                                                "type" to "error",
                                                "error" to "Failed to send message. Check if you are a participant of this chat."
                                            ))))
                                        }
                                    }

                                    "typing" -> {
                                        val chatType = ChatType.valueOf(incoming.chatType.uppercase())
                                        chatService.sendTyping(userId, chatType, incoming.chatId, true)
                                    }

                                    "read" -> {
                                        val chatType = ChatType.valueOf(incoming.chatType.uppercase())
                                        chatService.markMessagesAsRead(userId, chatType, incoming.chatId)
                                    }

                                    else -> {
                                        logger.warn("Unknown message type: ${incoming.type}")
                                    }
                                }
                            } catch (e: Exception) {
                                logger.error("Failed to parse message from user $userId: ${e.message}")
                                send(Frame.Text(Json.encodeToString(mapOf(
                                    "type" to "error",
                                    "error" to "Invalid message format"
                                ))))
                            }
                        }

                        is Frame.Close -> {
                            logger.info("WebSocket: user $userId closed connection")
                            break
                        }

                        else -> {
                            // Игнорируем бинарные фреймы, ping, pong
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("WebSocket error for user $userId: ${e.message}")
            } finally {
                chatService.disconnect(userId)
                sendJob.cancel()
                logger.info("WebSocket: user $userId disconnected")
            }
        }
    }
}
