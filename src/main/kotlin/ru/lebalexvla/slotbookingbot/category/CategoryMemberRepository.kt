package ru.lebalexvla.slotbookingbot.category

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.lebalexvla.slotbookingbot.contact.Contact
import java.util.UUID

interface CategoryMemberRepository : JpaRepository<CategoryMember, UUID> {
    fun existsByCategoryIdAndUserId(categoryId: UUID, userId: UUID): Boolean

    @EntityGraph(attributePaths = ["user"])
    fun findAllByCategoryIdOrderByAddedAtAscIdAsc(categoryId: UUID, pageable: Pageable): Slice<CategoryMember>

    fun deleteByCategoryIdAndUserId(categoryId: UUID, userId: UUID): Long

    @Query("""
        select c from Contact c join fetch c.contactUser
        where c.owner.id = :ownerId and not exists (
            select m.id from CategoryMember m
            where m.category.id = :categoryId and m.user.id = c.contactUser.id
        ) order by c.createdAt, c.id
    """)
    fun findCandidates(ownerId: UUID, categoryId: UUID, pageable: Pageable): Slice<Contact>
}
