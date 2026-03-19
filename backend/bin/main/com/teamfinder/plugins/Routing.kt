package com.teamfinder.plugins

import com.teamfinder.routes.*
import com.teamfinder.security.JwtConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.swagger.*

fun Application.configureRouting(jwtConfig: JwtConfig) {
    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
        
        // Сначала регистрируем auth (они публичные)
        authRouting(jwtConfig)
        
        // Затем проекты (смешанные - публичные и защищенные)
        projectRouting(jwtConfig)
        
        // Пользователи (пока закомментируем для теста)
        userRouting()

        uploadRouting()

        chatRouting()

        swaggerUI(path = "swagger", swaggerFile = "openapi.json")
    }
}