package ru.lebalexvla.slotbookingbot.slot

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SlotSetRepository : JpaRepository<SlotSet, UUID> {

    fun findByIdAndOwnerId(
        id: UUID,
        ownerId: UUID
    ): SlotSet?
}