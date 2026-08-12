package ru.lebalexvla.slotbookingbot.slot.draft

import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import ru.lebalexvla.slotbookingbot.slot.SlotSetTargetType
import java.time.Instant
import java.util.UUID

enum class PublicationStep { RECIPIENT, INTERVALS, PLACE, DESCRIPTION, LIMIT, REVIEW, PUBLISHED, CANCELED }

@Entity
@Table(name = "slot_publication_drafts")
class SlotPublicationDraft(
    @Id var ownerId: UUID,
    var sessionId: UUID = UUID.randomUUID(),
    var revision: Int = 0,
    @Enumerated(EnumType.STRING) var step: PublicationStep = PublicationStep.RECIPIENT,
    var timeZone: String,
    var expiresAt: Instant,
    @Enumerated(EnumType.STRING) var targetType: SlotSetTargetType? = null,
    var targetUserId: UUID? = null,
    var targetCategoryId: UUID? = null,
    var targetLabel: String? = null,
    var place: String? = null,
    var description: String? = null,
    var maxBookingsPerUser: Int? = null,
    var publishedSetId: UUID? = null,
    var lastUpdateId: Int? = null,
    @ElementCollection
    @CollectionTable(name = "slot_publication_intervals", joinColumns = [JoinColumn(name = "owner_id")])
    @OrderColumn(name = "position")
    var intervals: MutableList<DraftInterval> = mutableListOf()
)

@Embeddable
class DraftInterval(var startAt: Instant, var endAt: Instant)
