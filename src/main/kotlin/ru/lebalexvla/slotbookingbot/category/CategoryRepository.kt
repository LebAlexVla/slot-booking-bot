package ru.lebalexvla.slotbookingbot.category

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

interface CategoryRepository : JpaRepository<Category, UUID> {
    fun existsByOwnerIdAndNameAndArchivedAtIsNull(ownerId: UUID, name: String): Boolean

    fun findAllByOwnerIdAndArchivedAtIsNullOrderByCreatedAtAscIdAsc(
        ownerId: UUID, pageable: Pageable
    ): Slice<Category>

    fun findByIdAndOwnerIdAndArchivedAtIsNull(id: UUID, ownerId: UUID): Category?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findLockedByIdAndOwnerId(id: UUID, ownerId: UUID): Category?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findLockedByIdAndOwnerIdAndArchivedAtIsNull(id: UUID, ownerId: UUID): Category?
}
