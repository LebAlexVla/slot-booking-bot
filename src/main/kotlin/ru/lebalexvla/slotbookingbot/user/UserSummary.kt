package ru.lebalexvla.slotbookingbot.user

import java.util.UUID

data class UserSummary(val id: UUID, val name: String, val username: String?)

fun User.toSummary() = UserSummary(
    id = checkNotNull(id),
    name = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { "Пользователь" },
    username = username
)
