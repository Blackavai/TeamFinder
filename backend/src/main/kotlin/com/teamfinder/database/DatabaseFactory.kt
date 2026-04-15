package com.teamfinder.database

import com.teamfinder.config.DatabaseConfig
import com.teamfinder.models.*
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

        transaction {
            logger.info("Creating database tables if they don't exist...")

            SchemaUtils.createMissingTablesAndColumns(
                // Пользователи
                Users,

                // Авторизация через соцсети
                UserAuths,

                // Теги
                Tags,

                // Проекты
                Projects,
                ProjectTags,
                ProjectRolesTable,

                // Файлы
                FilesTable,

                // Отклики
                ResponsesTable,

                // Приглашения
                InvitationsTable,

                // Сообщения
                MessagesTable
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
