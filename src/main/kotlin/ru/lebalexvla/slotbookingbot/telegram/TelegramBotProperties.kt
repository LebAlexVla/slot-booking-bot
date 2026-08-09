package ru.lebalexvla.slotbookingbot.telegram

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "telegram.bot")
data class TelegramBotProperties(
    val token: String,
    val enabled: Boolean = true
)
