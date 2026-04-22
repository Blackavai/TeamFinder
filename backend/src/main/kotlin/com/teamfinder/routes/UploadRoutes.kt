package com.teamfinder.routes

import com.teamfinder.repositories.FileRepository
import com.teamfinder.repositories.UserRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.util.*

fun Route.uploadRoutes(userRepository: UserRepository, fileRepository: FileRepository) {

    authenticate("auth-jwt") {
        post("/upload") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asInt()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            var entityType = ""
            var entityId: Int? = null
            var response: Any? = null

            // 1. Получаем multipart-данные
            val multipartData = call.receiveMultipart()
            multipartData.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        // Читаем текстовые поля
                        when (part.name) {
                            "entityType" -> entityType = part.value
                            "entityId" -> entityId = part.value.toIntOrNull()
                        }
                    }
                    is PartData.FileItem -> {
                        val originalFileName = part.originalFileName ?: "unknown"
                        val fileExtension = File(originalFileName).extension
                        // ВАЖНО: Генерируем уникальное имя, чтобы избежать конфликтов и атак
                        val uniqueFileName = "${UUID.randomUUID()}.$fileExtension"
                        
                        val uploadDir = File("uploads")
                        uploadDir.mkdirs() // Создаем папку, если ее нет
                        val serverFile = File(uploadDir, uniqueFileName)

                        // 2. Сохраняем файл на диск
                        part.streamProvider().use { input ->
                            serverFile.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val publicUrl = "/static/$uniqueFileName"

                        // 3. Обрабатываем в зависимости от типа сущности
                        when (entityType) {
                            "avatar" -> {
                                userRepository.updateAvatarUrl(userId, publicUrl)
                                response = mapOf("avatarUrl" to publicUrl)
                            }
                            "project" -> {
                                if (entityId != null) {
                                    val fileDto = fileRepository.createFileRecord(
                                        uploaderId = userId,
                                        entityType = "project",
                                        entityId = entityId!!,
                                        originalName = originalFileName,
                                        serverPath = publicUrl
                                    )
                                    response = fileDto
                                }
                            }
                        }
                    }
                    else -> {}
                }
                part.dispose() // Очищаем временные данные
            }

            if (response != null) {
                call.respond(HttpStatusCode.OK, response!!)
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Некорректные данные для загрузки"))
            }
        }
    }
}