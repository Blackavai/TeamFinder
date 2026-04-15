package com.teamfinder.routes

import com.teamfinder.models.ErrorResponse
import com.teamfinder.models.MessageResponse
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
    val email: String? = null,
    val firstName: String,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    val goals: String? = null,
    val skills: List<String> = emptyList(),
    val rating: String? = null,
    val createdAt: String? = null
)

@Serializable
data class UsersListResponse(
    val data: List<UserProfileResponse>,
    val pagination: PaginationInfo
)

@Serializable
data class PaginationInfo(
    val currentPage: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int
)

fun Route.userRouting() {
    val userRepository = UserRepository()

    route("/users") {

        // ========== ПУБЛИЧНЫЕ МАРШРУТЫ ==========

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
                        firstName = user.firstName,
                        lastName = user.lastName,
                        avatarUrl = user.avatarUrl,
                        goals = user.goals,
                        skills = user.skills.map { it.name },
                        rating = user.rating?.toString(),
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

        get("/") {
            try {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                val users = userRepository.getAll(page, limit)
                val total = userRepository.count()

                val response = UsersListResponse(
                    data = users.map { user ->
                        UserProfileResponse(
                            id = user.id!!,
                            username = user.username,
                            email = user.email,
                            firstName = user.firstName,
                            lastName = user.lastName,
                            avatarUrl = user.avatarUrl,
                            goals = user.goals,
                            skills = user.skills.map { it.name },
                            rating = user.rating?.toString(),
                            createdAt = user.createdAt?.toString()
                        )
                    },
                    pagination = PaginationInfo(
                        currentPage = page,
                        pageSize = limit,
                        totalItems = total,
                        totalPages = (total + limit - 1) / limit
                    )
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Ошибка получения списка"))
            }
        }

        // ========== ЗАЩИЩЕННЫЕ МАРШРУТЫ ==========
        authenticate("auth-jwt") {

            put("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()

                    if (id == null || userId == null || id != userId) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Нет прав на редактирование"))
                        return@put
                    }

                    val updates = call.receive<Map<String, String>>()

                    val currentUser = userRepository.findById(id)
                        ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Пользователь не найден"))

                    val updatedUser = currentUser.copy(
                        firstName = updates["firstName"] ?: currentUser.firstName,
                        lastName = updates["lastName"] ?: currentUser.lastName,
                        avatarUrl = updates["avatarUrl"] ?: currentUser.avatarUrl,
                        goals = updates["goals"] ?: currentUser.goals,
                        portfolioUrl = updates["portfolioUrl"] ?: currentUser.portfolioUrl
                    )

                    val success = userRepository.update(id, updatedUser)

                    if (success) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Профиль успешно обновлен"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Не удалось обновить профиль"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Ошибка обновления"))
                }
            }

            delete("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()

                    if (id == null || userId == null || id != userId) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Нет прав на удаление"))
                        return@delete
                    }

                    val deleted = userRepository.delete(id)
                    if (deleted) {
                        call.respond(HttpStatusCode.OK, MessageResponse("Пользователь успешно удален"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Пользователь не найден"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Ошибка удаления"))
                }
            }

            get("/search") {
                try {
                    val query = call.request.queryParameters["query"] ?: ""
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                    if (query.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Пустой запрос"))
                        return@get
                    }

                    val users = userRepository.search(query, page, limit)
                    val total = userRepository.count(query)

                    val response = UsersListResponse(
                        data = users.map { user ->
                            UserProfileResponse(
                                id = user.id!!,
                                username = user.username,
                                email = user.email,
                                firstName = user.firstName,
                                lastName = user.lastName,
                                avatarUrl = user.avatarUrl,
                                skills = user.skills.map { it.name },
                                rating = user.rating?.toString()
                            )
                        },
                        pagination = PaginationInfo(
                            currentPage = page,
                            pageSize = limit,
                            totalItems = total,
                            totalPages = (total + limit - 1) / limit
                        )
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Ошибка поиска"))
                }
            }
        }
    }
}
