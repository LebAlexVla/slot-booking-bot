package ru.lebalexvla.slotbookingbot.slot

import java.time.Instant
import java.util.UUID

data class SlotCard(
    val id: UUID,
    val slotSetId: UUID,
    val startAt: Instant,
    val endAt: Instant,
    val place: String?,
    val description: String?,
    val ownerName: String,
    val targetName: String,
    val targetArchived: Boolean,
    val status: SlotStatus,
    val maxBookingsPerUser: Int
)
