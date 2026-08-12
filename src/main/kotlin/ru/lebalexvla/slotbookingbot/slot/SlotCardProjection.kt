package ru.lebalexvla.slotbookingbot.slot

import java.time.Instant
import java.util.UUID

interface SlotCardProjection {
    val id: UUID
    val slotSetId: UUID
    val startAt: Instant
    val endAt: Instant
    val place: String?
    val description: String?
    val ownerName: String
    val targetName: String
    val targetArchived: Boolean
    val status: String
    val maxBookingsPerUser: Int

    fun toCard() = SlotCard(
        id, slotSetId, startAt, endAt, place, description, ownerName, targetName,
        targetArchived, SlotStatus.valueOf(status), maxBookingsPerUser
    )
}
