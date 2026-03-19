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
        
        // 1. Получить пользователя по ID
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

        // 2. Получить список всех пользователей (С НОВОЙ ПАГИНАЦИЕЙ)
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
                            fullName = user.fullName,
                            avatarUrl = user.avatarUrl,
                            about = user.about,
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

        // ========== ЗАЩИЩЕННЫЕ МАРШРУТЫ (ТРЕБУЮТ ТОКЕН) ==========
        authenticate("auth-jwt") {

            // 3. ОБНОВЛЕНИЕ ПРОФИЛЯ (ИСПРАВЛЕНО!)
            put("/{id}") {
                try {
                    val id = call.parameters["id"]?.toIntOrNull()
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                    
                    // Проверяем, что пользователь редактирует свой профиль
                    if (id == null || userId == null || id != userId) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Нет прав на редактирование"))
                        return@put
                    }
                    
                    val updates = call.receive<Map<String, String>>()
                    
                    // Получаем текущего пользователя
                    val currentUser = userRepository.findById(id)
                        ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("Пользователь не найден"))
                    
                    // Обновляем только переданные поля
                    val updatedUser = currentUser.copy(
                        fullName = updates["fullName"] ?: currentUser.fullName,
                        about = updates["about"] ?: currentUser.about,
                        avatarUrl = updates["avatarUrl"] ?: currentUser.avatarUrl
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

            // 4. УДАЛЕНИЕ ПОЛЬЗОВАТЕЛЯ (НОВЫЙ МАРШРУТ)
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

            // 5. ПОЛУЧЕНИЕ РАСШИРЕННОГО ПРОФИЛЯ (уже было)
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

            // 6. ПОИСК ПОЛЬЗОВАТЕЛЕЙ С ПАГИНАЦИЕЙ (УЛУЧШЕНО)
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
                                fullName = user.fullName,
                                avatarUrl = user.avatarUrl
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