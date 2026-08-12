package ru.lebalexvla.slotbookingbot.contact

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ContactRepository : JpaRepository<Contact, UUID> {
    fun existsByOwnerIdAndContactUserId(ownerId: UUID, contactUserId: UUID): Boolean

    @Modifying
    @Query(value = """
        insert into contacts (id, owner_id, contact_user_id)
        values (:id, :ownerId, :contactUserId)
        on conflict (owner_id, contact_user_id) do nothing
    """, nativeQuery = true)
    fun insertIfAbsent(id: UUID, ownerId: UUID, contactUserId: UUID): Int

    @EntityGraph(attributePaths = ["contactUser"])
    fun findAllByOwnerIdOrderByCreatedAtAscIdAsc(ownerId: UUID, pageable: Pageable): Slice<Contact>
}
