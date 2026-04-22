package com.teamfinder.plugins

import com.teamfinder.repositories.UserRepository
import com.teamfinder.routes.*
import com.teamfinder.security.JwtConfig
import com.teamfinder.services.AuthService
import io.ktor.server.application.*
import io.ktor.server.http.content.* // Для статики
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import com.teamfinder.repositories.*
import com.teamfinder.services.*
import com.teamfinder.models.*

fun Application.configureRouting(jwtConfig: JwtConfig) {
    
    // Инициализируем зависимости (если ты используешь Koin/Dagger, то это делается иначе, но для начала сойдет так)
    val userRepository = UserRepository()
    val authService = AuthService(userRepository, jwtConfig)
    val projectRepository = ProjectRepository()
    val responseRepository = ResponseRepository()
    val messageRepository = MessageRepository() 
    val chatService = ChatService(messageRepository) 
    val fileRepository = FileRepository()

    routing {
        // Проверка работоспособности сервера
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        // 2. РАЗДАЧА ФАЙЛОВ в Ktor 2.x
        // Теперь если файл лежит в uploads/abc.jpg, он будет доступен как http://localhost:8080/static/abc.jpg
        staticFiles("/static", File("uploads"))
        
        // 3. ПОДКЛЮЧЕНИЕ РОУТОВ
        authRoutes(authService)       // Вызываем функцию из AuthRoutes.kt
        userRoutes(userRepository)    // Вызываем функцию из UserRoutes.kt

        projectRoutes(projectRepository)  // Раскомментируешь, когда напишем
        responseRoutes(responseRepository)
        chatRoutes(chatService, messageRepository)
        uploadRoutes(userRepository, fileRepository)
        // uploadRouting()            // Раскомментируешь, когда настроим загрузку
    }
}