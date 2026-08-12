package ru.lebalexvla.slotbookingbot.slot.draft

import ru.lebalexvla.slotbookingbot.slot.SlotInterval
import ru.lebalexvla.slotbookingbot.slot.SlotSetTarget
import java.time.Instant
import java.util.UUID

data class PublicationDraft(
    val id: UUID,
    val revision: Int,
    val step: PublicationStep,
    val timeZone: String,
    val expiresAt: Instant,
    val target: SlotSetTarget?,
    val targetLabel: String?,
    val intervals: List<SlotInterval>,
    val place: String?,
    val description: String?,
    val maxBookingsPerUser: Int?,
    val publishedSetId: UUID?,
    val lastUpdateId: Int?
) {
    val active: Boolean get() = step != PublicationStep.PUBLISHED && step != PublicationStep.CANCELED
}

sealed interface DraftInput {
    data class Intervals(val value: List<SlotInterval>) : DraftInput
    data class Place(val value: String?) : DraftInput
    data class Description(val value: String?) : DraftInput
    data class Limit(val value: Int) : DraftInput
}
