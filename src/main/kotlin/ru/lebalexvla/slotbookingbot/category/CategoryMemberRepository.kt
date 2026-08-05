package ru.lebalexvla.slotbookingbot.category

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CategoryMemberRepository :
    JpaRepository<CategoryMember, UUID> {

    fun existsByCategoryIdAndUserId(
        categoryId: UUID,
        userId: UUID
    ): Boolean

    fun findAllByCategoryId(
        categoryId: UUID
    ): List<CategoryMember>
}