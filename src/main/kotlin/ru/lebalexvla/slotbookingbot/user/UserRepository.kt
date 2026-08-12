package ru.lebalexvla.slotbookingbot.user

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    @Query(value = "SELECT * FROM users WHERE id = :id FOR NO KEY UPDATE", nativeQuery = true)
    fun findForPublicationById(id: UUID): User?

    fun findByTelegramUserId(telegramUserId: Long): User?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findLockedById(
        id: UUID
    ): User?
}
