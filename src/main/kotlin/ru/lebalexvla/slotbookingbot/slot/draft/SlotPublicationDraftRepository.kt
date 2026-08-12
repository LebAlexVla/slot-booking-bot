package ru.lebalexvla.slotbookingbot.slot.draft

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

interface SlotPublicationDraftRepository : JpaRepository<SlotPublicationDraft, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findLockedByOwnerId(ownerId: UUID): SlotPublicationDraft?
}
