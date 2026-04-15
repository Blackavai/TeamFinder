package com.teamfinder.routes

import com.teamfinder.security.JwtConfig
import com.teamfinder.services.AuthService
import com.teamfinder.services.AuthResult
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val firstName: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class UserResponse(
    val id: Int,
    val username: String,
    val email: String,
    val fullName: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse
)

fun Route.authRouting(jwtConfig: JwtConfig) {
    val authService = AuthService(jwtConfig)

    route("/auth") {

        post("/register") {
            try {
                val text = call.receive<String>()
                println("📥 Получен запрос: $text")
                
                val request = Json.decodeFromString<RegisterRequest>(text)
                
                val result = authService.register(
                    username = request.username,
                    email = request.email,
                    firstName = request.firstName,
                    password = request.password
                )
                
                when (result) {
                    is AuthResult.Success -> {
                        val response = AuthResponse(
                            accessToken = result.accessToken,
                            refreshToken = result.refreshToken,
                            user = UserResponse(
                                id = result.user.id!!,
                                username = result.user.username,
                                email = result.user.email,
                                fullName = result.user.fullName,
                                avatarUrl = result.user.avatarUrl
                            )
                        )
                        call.respond(HttpStatusCode.Created, response)
                    }
                    is AuthResult.Error -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("ошибка" to result.message))
                    }
                }
                
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("ошибка" to (e.message ?: "Неизвестная ошибка")))
            }
        }

        post("/login") {
            try {
                val text = call.receive<String>()
                
                val request = Json.decodeFromString<LoginRequest>(text)
                
                val result = authService.login(
                    email = request.email,
                    password = request.password
                )
                
                when (result) {
                    is AuthResult.Success -> {
                        val response = AuthResponse(
                            accessToken = result.accessToken,
                            refreshToken = result.refreshToken,
                            user = UserResponse(
                                id = result.user.id!!,
                                username = result.user.username,
                                email = result.user.email,
                                fullName = result.user.fullName,
                                avatarUrl = result.user.avatarUrl
                            )
                        )
                        call.respond(HttpStatusCode.OK, response)
                    }
                    is AuthResult.Error -> {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("ошибка" to result.message))
                    }
                }
                
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("ошибка" to (e.message ?: "Неизвестная ошибка")))
            }
        }

        post("/refresh") {
            try {
                val text = call.receive<String>()
                
                val request = Json.decodeFromString<RefreshRequest>(text)
                
                val result = authService.refreshToken(request.refreshToken)
                
                when (result) {
                    is AuthResult.Success -> {
                        val response = AuthResponse(
                            accessToken = result.accessToken,
                            refreshToken = result.refreshToken,
                            user = UserResponse(
                                id = result.user.id!!,
                                username = result.user.username,
                                email = result.user.email,
                                fullName = result.user.fullName,
                                avatarUrl = result.user.avatarUrl
                            )
                        )
                        call.respond(HttpStatusCode.OK, response)
                    }
                    is AuthResult.Error -> {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("ошибка" to result.message))
                    }
                }
                
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("ошибка" to (e.message ?: "Неизвестная ошибка")))
            }
        }
    }
}