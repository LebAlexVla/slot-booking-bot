package ru.lebalexvla.slotbookingbot.booking.request

import java.time.Instant
import java.util.UUID

data class BookingRequest(
    val id: UUID,
    val revision: Int,
    val step: BookingRequestStep,
    val expiresAt: Instant,
    val slotId: UUID,
    val startAt: Instant,
    val endAt: Instant,
    val ownerName: String,
    val place: String?,
    val comment: String?,
    val proposedPlace: String?,
    val bookingId: UUID?,
    val lastUpdateId: Int?
) {
    fun activeAt(now: Instant) =
        step != BookingRequestStep.SUBMITTED && step != BookingRequestStep.CANCELED && expiresAt.isAfter(now)
}
