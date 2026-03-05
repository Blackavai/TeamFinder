package com.teamfinder.database

import com.teamfinder.models.*
import com.teamfinder.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    
    fun init(config: DatabaseConfig) {
        logger.info("Initializing database connection to: ${config.url}")
        
        Database.connect(hikari(config))
        
        // СОЗДАЕМ ТАБЛИЦЫ ЗДЕСЬ
        transaction {
            logger.info("Creating database tables if they don't exist...")
            
            SchemaUtils.createMissingTablesAndColumns(
                Users,           // из models/User.kt
                Profiles,        // из models/User.kt
                Projects,        // из models/Project.kt
                ProjectTags,     // из models/Project.kt
                Tags,            // из models/Project.kt
                Vacancies,       // из models/Project.kt
                ProjectLikes,    // из models/Project.kt
                Comments,        // из models/Project.kt
                Chats,           // из models/Chat.kt
                ChatParticipants,// из models/Chat.kt
                Messages         // из models/Chat.kt
            )
            
            logger.info("Database tables created/verified successfully")
        }
    }

    private fun hikari(config: DatabaseConfig): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = config.maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(hikariConfig)
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T = 
        newSuspendedTransaction(Dispatchers.IO) { block() }
}