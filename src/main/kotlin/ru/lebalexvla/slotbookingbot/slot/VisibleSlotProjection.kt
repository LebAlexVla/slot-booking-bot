package ru.lebalexvla.slotbookingbot.slot

import java.time.Instant
import java.util.UUID

interface VisibleSlotProjection {
    val id: UUID
    val slotSetId: UUID
    val startAt: Instant
    val endAt: Instant
    val place: String?
    val description: String?
}