package com.teamfinder.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<AuthenticationException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Не авторизован"))
        }
        exception<Throwable> { call, cause ->
            // В проде можно логировать ошибку (cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Внутренняя ошибка сервера"))
        }
    }
}

// Вспомогательный класс исключения
class AuthenticationException : RuntimeException()