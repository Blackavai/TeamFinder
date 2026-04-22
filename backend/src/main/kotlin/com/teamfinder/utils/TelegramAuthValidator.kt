package com.teamfinder.utils

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TelegramAuthValidator {
    // Вставь сюда токен своего бота от BotFather (в проде лучше брать из application.conf)
    private const val BOT_TOKEN = "ТВОЙ_ТОКЕН_БОТА" 

    fun verify(data: Map<String, String>, expectedHash: String): Boolean {
        // 1. Убираем hash и сортируем ключи по алфавиту
        val dataCheckString = data.filterKeys { it != "hash" }
            .toSortedMap()
            .map { "${it.key}=${it.value}" }
            .joinToString("\n")

        // 2. Генерируем секретный ключ: SHA256(bot_token)
        val secretKey = MessageDigest.getInstance("SHA-256").digest(BOT_TOKEN.toByteArray())

        // 3. Вычисляем HMAC-SHA256
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey, "HmacSHA256"))
        val calculatedHash = mac.doFinal(dataCheckString.toByteArray())
            .joinToString("") { "%02x".format(it) } // переводим в HEX

        // 4. Сравниваем
        return calculatedHash == expectedHash
    }
}