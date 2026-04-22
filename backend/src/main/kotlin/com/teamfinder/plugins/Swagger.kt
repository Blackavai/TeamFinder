package com.teamfinder.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.routing.*

fun Application.configureSwagger() {
    routing {
        // Swagger UI будет доступен по адресу http://localhost:8080/swagger
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        
        // Сама спецификация в JSON будет тут http://localhost:8080/openapi
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
    }
}