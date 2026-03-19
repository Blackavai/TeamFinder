package com.teamfinder.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.uploadRouting() {
    route("/uploads") {
        post {
            call.respond(mapOf("message" to "Upload endpoint"))
        }
    }
}