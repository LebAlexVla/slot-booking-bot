package ru.lebalexvla.slotbookingbot.slot

import java.time.Instant
import java.util.UUID

data class PublishSlotSetCommand(
    val ownerId: UUID,
    val target: SlotSetTarget,
    val place: String?,
    val description: String?,
    val maxBookingsPerUser: Int,
    val slots: List<SlotInterval>
)

sealed interface SlotSetTarget {

    data class UserTarget(
        val userId: UUID
    ) : SlotSetTarget

    data class CategoryTarget(
        val categoryId: UUID
    ) : SlotSetTarget
}

data class SlotInterval(
    val startAt: Instant,
    val endAt: Instant
)