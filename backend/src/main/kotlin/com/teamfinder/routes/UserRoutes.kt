package com.teamfinder.routes

import com.teamfinder.models.MessageResponse
import com.teamfinder.models.ErrorResponse
import com.teamfinder.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileResponse(
    val id: Int,
    val username: String,
    val email: String,
    val fullName: String? = null,
    val avatarUrl: String? = null,
    val about: String? = null,
    val createdAt: String? = null
)


fun Route.userRouting() {
    val userRepository = UserRepository()

    route("/users") {

        // Публичный эндпоинт
        get("/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный ID"))
                    return@get
                }

                val user = userRepository.findById(id)
                if (user != null) {
                    val response = UserProfileResponse(
                        id = user.id!!,
                        username = user.username,
                        email = user.email,
                        fullName = user.fullName,
                        avatarUrl = user.avatarUrl,
                        about = user.about,
                        createdAt = user.createdAt?.toString()
                    )
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Пользователь не найден"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Неизвестная ошибка"))
            }
        }

        // Защищенные эндпоинты
        authenticate("auth-jwt") {
            
            put("/{id}") {
                // TODO: реализовать обновление профиля
                call.respond(HttpStatusCode.OK, MessageResponse("Будет реализовано позже"))
            }

            get("/{id}/profile") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный ID"))
                    return@get
                }

                val profile = userRepository.getProfile(id)
                if (profile != null) {
                    call.respond(HttpStatusCode.OK, profile)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Профиль не найден"))
                }
            }

            get("/search") {
                val query = call.request.queryParameters["query"] ?: ""
                if (query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Пустой запрос"))
                    return@get
                }

                val users = userRepository.search(query, limit = 20)
                val response = users.map { user ->
                    UserProfileResponse(
                        id = user.id!!,
                        username = user.username,
                        email = user.email,
                        fullName = user.fullName,
                        avatarUrl = user.avatarUrl
                    )
                }
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }
}