package com.teamfinder.routes

import com.teamfinder.models.UpdateProfileRequest
import com.teamfinder.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(userRepository: UserRepository) {
    // Закрываем роуты JWT авторизацией
    authenticate("auth-jwt") {
        route("/users/me") {
            
            // Получить свой профиль
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt() 
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val user = userRepository.findUserById(userId)
                if (user != null) {
                    call.respond(HttpStatusCode.OK, user)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Пользователь не найден"))
                }
            }

            // Обновить свой профиль (навыки, био и т.д.)
            put {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt() 
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)

                val request = call.receive<UpdateProfileRequest>()
                val updated = userRepository.updateUserProfile(userId, request)

                if (updated) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Профиль обновлен"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Ошибка обновления"))
                }
            }
        }
    }
}