package ru.lebalexvla.slotbookingbot.user

data class RegisterOrUpdateUserCommand(
    val telegramUserId: Long,
    val telegramChatId: Long,
    val username: String?,
    val firstName: String,
    val lastName: String?
)