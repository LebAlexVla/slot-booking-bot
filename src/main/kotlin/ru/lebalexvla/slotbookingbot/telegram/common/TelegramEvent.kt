package ru.lebalexvla.slotbookingbot.telegram.common

import java.util.UUID

sealed interface TelegramEvent {

    val actor: TelegramActorProfile
    val updateId: Int?

    data class Command(
        override val actor: TelegramActorProfile,
        val name: String,
        val arguments: String?,
        override val updateId: Int? = null
    ) : TelegramEvent

    data class Text(
        override val actor: TelegramActorProfile,
        val text: String,
        override val updateId: Int? = null
    ) : TelegramEvent

    data class Callback(
        override val actor: TelegramActorProfile,
        val callbackQueryId: String,
        val data: String?,
        override val updateId: Int? = null
    ) : TelegramEvent
}

data class TelegramActorProfile(
    val telegramUserId: Long,
    val chatId: Long,
    val username: String?,
    val firstName: String,
    val lastName: String?
)

data class TelegramActor(
    val userId: UUID,
    val chatId: Long
)
