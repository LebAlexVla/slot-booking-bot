package ru.lebalexvla.slotbookingbot.category

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CategoryRepository : JpaRepository<Category, UUID> {

    fun existsByOwnerIdAndName(
        ownerId: UUID,
        name: String
    ): Boolean

    fun findAllByOwnerId(
        ownerId: UUID
    ): List<Category>

    fun findByIdAndOwnerId(
        id: UUID,
        ownerId: UUID
    ): Category?
}