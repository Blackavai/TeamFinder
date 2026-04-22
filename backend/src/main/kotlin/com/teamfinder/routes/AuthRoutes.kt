package com.teamfinder.routes

import com.teamfinder.models.LoginRequest
import com.teamfinder.models.RegisterUserRequest
import com.teamfinder.services.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.teamfinder.models.TelegramAuthRequest

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        post("/register") {
            try {
                val request = call.receive<RegisterUserRequest>()
                val token = authService.register(request)
                call.respond(HttpStatusCode.Created, mapOf("token" to token))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        post("/login") {
            try {
                val request = call.receive<LoginRequest>()
                val token = authService.login(request)
                call.respond(HttpStatusCode.OK, mapOf("token" to token))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to e.message))
            }
        }

        post("/telegram") {
            try {
                val request = call.receive<TelegramAuthRequest>()
                val token = authService.telegramAuth(request)
                call.respond(HttpStatusCode.OK, mapOf("token" to token))
            } catch (e: Exception) {
                // Если что-то пошло не так (например, хэш неверный)
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (e.message ?: "Ошибка авторизации TG")))
            }
        }
    }
}