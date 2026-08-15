package ru.lebalexvla.slotbookingbot.booking

import java.time.Instant
import java.util.UUID

data class BookingCard(
    val id: UUID,
    val slotId: UUID,
    val ownerId: UUID,
    val bookerId: UUID,
    val ownerName: String,
    val bookerName: String,
    val startAt: Instant,
    val endAt: Instant,
    val status: BookingStatus,
    val place: String?,
    val proposedPlace: String?,
    val comment: String?,
    val description: String?
)

enum class BookingList { MINE, INCOMING }
