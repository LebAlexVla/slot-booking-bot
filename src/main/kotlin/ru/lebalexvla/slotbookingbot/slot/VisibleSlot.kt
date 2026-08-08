package ru.lebalexvla.slotbookingbot.slot

import java.time.Instant
import java.util.UUID

data class VisibleSlot(
    val id: UUID,
    val slotSetId: UUID,
    val startAt: Instant,
    val endAt: Instant,
    val place: String?,
    val description: String?
)