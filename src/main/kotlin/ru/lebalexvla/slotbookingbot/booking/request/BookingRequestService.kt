package ru.lebalexvla.slotbookingbot.booking.request

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.lebalexvla.slotbookingbot.booking.BookSlotCommand
import ru.lebalexvla.slotbookingbot.booking.BookingPolicy
import ru.lebalexvla.slotbookingbot.booking.BookingService
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.slot.SlotRepository
import ru.lebalexvla.slotbookingbot.slot.SlotService
import ru.lebalexvla.slotbookingbot.user.UserRepository
import ru.lebalexvla.slotbookingbot.user.toSummary
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Service
class BookingRequestService(
    private val drafts: BookingRequestDraftRepository,
    private val users: UserRepository,
    private val slots: SlotRepository,
    private val slotService: SlotService,
    private val bookings: BookingService,
    private val clock: Clock
) {
    @Transactional
    fun start(userId: UUID, slotId: UUID, updateId: Int?): BookingRequest {
        lockUser(userId)
        val previous = drafts.findLockedByUserId(userId)
        if (previous != null && previous.expiresAt.isAfter(clock.instant())) {
            if (previous.repeated(updateId)) return previous.toView()
            if (previous.toView().activeAt(clock.instant())) {
                if (previous.slot.id != slotId) throw BusinessException(BusinessError.BOOKING_IN_PROGRESS)
                return previous.toView()
            }
        }
        slotService.getVisibleSlot(userId, slotId)
        val draft = previous ?: BookingRequestDraft(
            userId = userId, slot = slots.getReferenceById(slotId), expiresAt = expiry()
        )
        draft.sessionId = UUID.randomUUID()
        draft.revision = 0
        draft.step = BookingRequestStep.COMMENT
        draft.slot = slots.getReferenceById(slotId)
        draft.expiresAt = expiry()
        draft.comment = null
        draft.proposedPlace = null
        draft.bookingId = null
        draft.lastUpdateId = updateId
        return drafts.saveAndFlush(draft).toView()
    }

    @Transactional(readOnly = true)
    fun current(userId: UUID): BookingRequest? = drafts.findById(userId).orElse(null)?.toView()

    @Transactional
    fun accept(userId: UUID, id: UUID, revision: Int, updateId: Int?, text: String?): BookingRequest =
        mutate(userId, id, revision, updateId) { draft ->
            when (draft.step) {
                BookingRequestStep.COMMENT -> {
                    draft.comment = BookingPolicy.text(text, BookingPolicy.MAX_COMMENT)
                    draft.step = BookingRequestStep.PLACE
                }
                BookingRequestStep.PLACE -> {
                    draft.proposedPlace = BookingPolicy.text(text, BookingPolicy.MAX_PLACE)
                    draft.step = BookingRequestStep.REVIEW
                }
                else -> throw BusinessException(BusinessError.STALE_DRAFT)
            }
        }

    @Transactional
    fun cancel(userId: UUID, id: UUID, revision: Int, updateId: Int?): BookingRequest =
        mutate(userId, id, revision, updateId) { it.step = BookingRequestStep.CANCELED }

    @Transactional
    fun submit(userId: UUID, id: UUID, revision: Int, updateId: Int?): BookingRequest {
        lockUser(userId)
        val draft = locked(userId)
        if (draft.sessionId == id && draft.step == BookingRequestStep.SUBMITTED) return draft.toView()
        return change(draft, id, revision, updateId) {
            if (it.step != BookingRequestStep.REVIEW) throw BusinessException(BusinessError.STALE_DRAFT)
            it.bookingId = bookings.book(BookSlotCommand(userId, it.slot.id!!, it.proposedPlace, it.comment))
            it.step = BookingRequestStep.SUBMITTED
        }
    }

    private fun mutate(
        userId: UUID, id: UUID, revision: Int, updateId: Int?, operation: (BookingRequestDraft) -> Unit
    ): BookingRequest {
        lockUser(userId)
        return change(locked(userId), id, revision, updateId, operation)
    }

    private fun change(
        draft: BookingRequestDraft, id: UUID, revision: Int, updateId: Int?, operation: (BookingRequestDraft) -> Unit
    ): BookingRequest {
        if (draft.sessionId != id) throw BusinessException(BusinessError.STALE_DRAFT)
        if (draft.repeated(updateId)) return draft.toView()
        if (draft.revision != revision || draft.step in setOf(BookingRequestStep.SUBMITTED, BookingRequestStep.CANCELED)) {
            throw BusinessException(BusinessError.STALE_DRAFT)
        }
        if (!draft.expiresAt.isAfter(clock.instant())) throw BusinessException(BusinessError.DRAFT_EXPIRED)
        operation(draft)
        draft.revision++
        draft.lastUpdateId = updateId ?: draft.lastUpdateId
        draft.expiresAt = expiry()
        return draft.toView()
    }

    private fun lockUser(userId: UUID) {
        if (users.findForMutationById(userId) == null) throw BusinessException(BusinessError.USER_NOT_FOUND)
    }

    private fun locked(userId: UUID) =
        drafts.findLockedByUserId(userId) ?: throw BusinessException(BusinessError.BOOKING_DRAFT_NOT_FOUND)

    private fun expiry() = clock.instant().plus(Duration.ofHours(24))

    private fun BookingRequestDraft.repeated(updateId: Int?) =
        updateId != null && lastUpdateId?.let { updateId <= it } == true

    private fun BookingRequestDraft.toView() = BookingRequest(
        sessionId, revision, step, expiresAt, slot.id!!, slot.startAt, slot.endAt,
        slot.slotSet.owner.toSummary().name, slot.slotSet.place, comment, proposedPlace, bookingId, lastUpdateId
    )
}
