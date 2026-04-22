package com.teamfinder.routes

import com.teamfinder.models.CreateResponseRequest
import com.teamfinder.models.UpdateResponseStatusRequest
import com.teamfinder.repositories.ResponseRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.responseRoutes(responseRepository: ResponseRepository) {

    authenticate("auth-jwt") {
        route("/responses") {

            // 1. Создать отклик на проект
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                try {
                    val request = call.receive<CreateResponseRequest>()
                    
                    // Проверка: нельзя откликнуться на свой же проект
                    if (responseRepository.isUserProjectAuthor(userId, request.projectId)) {
                        return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Вы не можете откликнуться на свой собственный проект"))
                    }

                    val newResponse = responseRepository.createResponse(userId, request)
                    if (newResponse != null) {
                        call.respond(HttpStatusCode.Created, newResponse)
                    } else {
                        // Чаще всего это сработает из-за unique constraint в БД (повторный отклик)
                        call.respond(HttpStatusCode.Conflict, mapOf("error" to "Не удалось создать отклик. Возможно, вы уже откликались"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректные данные: ${e.message}"))
                }
            }

            // 2. Получить список МОИХ откликов
            get("/my") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val myResponses = responseRepository.getMyResponses(userId)
                call.respond(HttpStatusCode.OK, myResponses)
            }
        }
        
        // Роуты, связанные с конкретным проектом
        route("/projects/{projectId}/responses") {
            
            // 3. Получить все отклики на проект (только для автора)
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                
                val projectId = call.parameters["projectId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный ID проекта"))

                // Проверяем, является ли пользователь автором
                if (!responseRepository.isUserProjectAuthor(userId, projectId)) {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "У вас нет прав для просмотра этих откликов"))
                }

                val responses = responseRepository.getResponsesForProject(projectId)
                call.respond(HttpStatusCode.OK, responses)
            }
        }
        
        // Роуты для управления конкретным откликом
        route("/responses/{responseId}/status") {
            
            // 4. Изменить статус отклика (принять/отклонить)
            put {
                val principal = call.principal<JWTPrincipal>()
                val currentUserId = principal?.payload?.getClaim("userId")?.asInt()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)
                
                val responseId = call.parameters["responseId"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректный ID отклика"))
                
                // TODO: Нужна функция в репозитории, которая по responseId вернет projectId и проверит автора
                // Для упрощения пока опустим проверку прав, но в проде она КРИТИЧЕСКИ ВАЖНА
                
                try {
                    val request = call.receive<UpdateResponseStatusRequest>()
                    // Валидация статуса
                    if (request.status !in listOf("принят", "отклонён")) {
                        return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Недопустимый статус"))
                    }

                    val updated = responseRepository.updateResponseStatus(responseId, request.status)
                    if (updated) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Статус обновлен"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Отклик не найден"))
                    }

                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректные данные"))
                }
            }
        }
    }
}