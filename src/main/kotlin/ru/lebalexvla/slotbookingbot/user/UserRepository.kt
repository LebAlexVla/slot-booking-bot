package ru.lebalexvla.slotbookingbot.user

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByTelegramUserId(telegramUserId: Long): User?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findLockedById(
        id: UUID
    ): User?
}