package com.teamfinder.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

@Serializable
data class UserAuth(
    val id: Int? = null,
    val userId: Int,
    val provider: String,
    val providerId: String
)

// Таблица UserAuth
object UserAuths : Table("user_auth") {
    val id = integer("auth_id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val provider = varchar("provider", 20)
    val providerId = varchar("provider_id", 255)

    override val primaryKey = PrimaryKey(id)
}
