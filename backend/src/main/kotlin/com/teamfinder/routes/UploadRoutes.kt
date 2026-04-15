package com.teamfinder.routes

import com.teamfinder.models.ErrorResponse
import com.teamfinder.repositories.FileRepository
import com.teamfinder.security.JwtConfig
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

@kotlinx.serialization.Serializable
data class UploadRequest(
    val entityType: String,   // "project", "message", "profile"
    val entityId: Int
)

fun Route.uploadRouting(jwtConfig: JwtConfig) {
    val fileRepository = FileRepository()

    authenticate("auth-jwt") {
        route("/uploads") {

            post {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    // Читаем entityType и entityId из multipart form data
                    var entityType: String? = null
                    var entityId: Int? = null
                    var fileName: String? = null

                    val multipart = call.receiveMultipart()
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                when (part.name) {
                                    "entityType" -> entityType = part.value
                                    "entityId" -> entityId = part.value.toIntOrNull()
                                }
                            }
                            is PartData.FileItem -> {
                                val originalName = part.originalFileName ?: "file"
                                val extension = originalName.substringAfterLast('.', "bin").lowercase()

                                val allowedExtensions = listOf("jpg", "jpeg", "png", "gif", "doc", "docx", "pdf", "txt")
                                if (extension !in allowedExtensions) {
                                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unsupported file format: .$extension"))
                                    return@forEachPart
                                }

                                fileName = "${UUID.randomUUID()}.$extension"

                                val folder = File("uploads")
                                if (!folder.exists()) folder.mkdirs()

                                val file = File(folder, fileName!!)
                                part.streamProvider().use { input ->
                                    file.outputStream().buffered().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                        part.dispose()
                    }

                    if (fileName == null) {
                        return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("No file found in request"))
                    }

                    val et = entityType ?: "general"
                    val eid = entityId ?: 0

                    // Сохраняем запись в БД
                    val fileRecord = fileRepository.create(
                        userId = userId,
                        entityType = et,
                        entityId = eid,
                        fileName = fileName!!,
                        filePath = "/static/$fileName"
                    )

                    call.respond(HttpStatusCode.Created, mapOf(
                        "message" to "File uploaded successfully",
                        "file" to fileRecord
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to upload file: ${e.message}"))
                }
            }

            // Получить файлы по сущности
            get("/{entityType}/{entityId}") {
                try {
                    val entityType = call.parameters["entityType"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("entityType required"))
                    val entityId = call.parameters["entityId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid entityId"))

                    val files = fileRepository.findByEntity(entityType, entityId)
                    call.respond(HttpStatusCode.OK, files)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }

            // Удалить файл
            delete("/{id}") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))

                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid ID"))

                    val file = fileRepository.findById(id)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("File not found"))

                    if (file.userId != userId) {
                        return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("You can only delete your own files"))
                    }

                    // Удаляем файл с диска
                    val physicalFile = File("uploads", file.fileName)
                    if (physicalFile.exists()) physicalFile.delete()

                    // Удаляем запись из БД
                    val deleted = fileRepository.delete(id)
                    if (deleted) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "File deleted"))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete file"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Server error"))
                }
            }
        }
    }
}
