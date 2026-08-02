package ru.lebalexvla.slotbookingbot.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByTelegramUserId(telegramUserId: Long): User?
}