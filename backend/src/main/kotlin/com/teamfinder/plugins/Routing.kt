package com.teamfinder.plugins

import com.teamfinder.routes.*
import com.teamfinder.security.JwtConfig
import com.teamfinder.services.ChatService
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(jwtConfig: JwtConfig) {
    val chatService = ChatService()

    routing {
        // Проверка работоспособности сервера
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        // Раздача статических файлов из папки uploads
        static("/static") {
            files("uploads")
        }

        // ========== PUBLIC & MIXED ROUTES ==========
        authRouting(jwtConfig)
        projectRouting(jwtConfig)
        userRouting()
        uploadRouting(jwtConfig)

        // ========== RESPONSES & INVITATIONS ==========
        responseRouting(jwtConfig)
        invitationRouting(jwtConfig)

        // ========== CHAT ==========
        chatRouting(jwtConfig, chatService)
    }

    // WebSockets нужно настраивать отдельно (внутри install(WebSockets))
    configureSockets(chatService)
}
