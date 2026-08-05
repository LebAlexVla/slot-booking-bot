package ru.lebalexvla.slotbookingbot.contact

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContactRepository : JpaRepository<Contact, UUID> {

    fun existsByOwnerIdAndContactUserId(
        ownerId: UUID,
        contactUserId: UUID
    ): Boolean

    fun findAllByOwnerId(
        ownerId: UUID
    ): List<Contact>
}