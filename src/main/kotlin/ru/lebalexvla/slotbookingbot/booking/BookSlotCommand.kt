package ru.lebalexvla.slotbookingbot.booking

import java.util.UUID

data class BookSlotCommand(
    val userId: UUID,
    val slotId: UUID,
    val proposedPlace: String? = null,
    val comment: String? = null
)