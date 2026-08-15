package ru.lebalexvla.slotbookingbot.booking.request

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.LockModeType
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import ru.lebalexvla.slotbookingbot.slot.Slot
import java.time.Instant
import java.util.UUID

enum class BookingRequestStep { COMMENT, PLACE, REVIEW, SUBMITTED, CANCELED }

@Entity
@Table(name = "booking_request_drafts")
class BookingRequestDraft(
    @Id var userId: UUID,
    var sessionId: UUID = UUID.randomUUID(),
    var revision: Int = 0,
    @Enumerated(EnumType.STRING) var step: BookingRequestStep = BookingRequestStep.COMMENT,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "slot_id") var slot: Slot,
    var expiresAt: Instant,
    var comment: String? = null,
    var proposedPlace: String? = null,
    var bookingId: UUID? = null,
    var lastUpdateId: Int? = null
)

interface BookingRequestDraftRepository : JpaRepository<BookingRequestDraft, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findLockedByUserId(userId: UUID): BookingRequestDraft?
}
