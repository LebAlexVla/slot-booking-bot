package ru.lebalexvla.slotbookingbot.booking

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.slot.Slot
import ru.lebalexvla.slotbookingbot.slot.SlotRepository
import ru.lebalexvla.slotbookingbot.slot.SlotStatus
import ru.lebalexvla.slotbookingbot.user.User
import ru.lebalexvla.slotbookingbot.user.UserRepository
import java.time.Clock
import java.util.UUID

@Service
class BookingService(
    private val bookings: BookingRepository,
    private val slots: SlotRepository,
    private val users: UserRepository,
    private val clock: Clock
) {
    @Transactional
    fun book(command: BookSlotCommand): UUID {
        val user = users.findForMutationById(command.userId)
            ?: throw BusinessException(BusinessError.USER_NOT_FOUND)
        val slot = slots.findLockedById(command.slotId)
            ?: throw BusinessException(BusinessError.SLOT_NOT_AVAILABLE)
        validateBookability(slot, user)
        val active = bookings.countByBookedByAndSlotSlotSetAndStatusIn(user, slot.slotSet, ACTIVE_STATUSES)
        if (active >= slot.slotSet.maxBookingsPerUser) throw BusinessException(BusinessError.BOOKING_LIMIT_REACHED)
        val booking = bookings.save(Booking(
            slot = slot,
            bookedBy = user,
            proposedPlace = BookingPolicy.text(command.proposedPlace, BookingPolicy.MAX_PLACE),
            comment = BookingPolicy.text(command.comment, BookingPolicy.MAX_COMMENT),
            createdAt = clock.instant()
        ))
        slot.status = SlotStatus.PENDING_CONFIRMATION
        return checkNotNull(booking.id)
    }

    @Transactional
    fun confirm(bookingId: UUID, ownerId: UUID, expected: BookingStatus? = null) =
        change(bookingId, ownerId, BookingDecision.CONFIRM, expected)

    @Transactional
    fun reject(bookingId: UUID, ownerId: UUID, expected: BookingStatus? = null) =
        change(bookingId, ownerId, BookingDecision.REJECT, expected)

    @Transactional
    fun cancel(bookingId: UUID, requesterId: UUID, expected: BookingStatus? = null) =
        change(bookingId, requesterId, BookingDecision.CANCEL, expected)

    private fun change(id: UUID, actorId: UUID, decision: BookingDecision, expected: BookingStatus?) {
        val booking = locked(id)
        val isOwner = booking.slot.slotSet.owner.id == actorId
        val canCancel = decision == BookingDecision.CANCEL && booking.bookedBy.id == actorId
        if (!isOwner && !canCancel) throw BusinessException(BusinessError.BOOKING_NOT_FOUND)
        BookingLifecycle.apply(booking, decision, clock.instant(), expected)
    }

    private fun locked(id: UUID): Booking {
        val slotId = bookings.findSlotId(id) ?: throw BusinessException(BusinessError.BOOKING_NOT_FOUND)
        // Every operation locks the slot before its booking; no booking -> slot lock cycle.
        slots.findLockedById(slotId) ?: throw BusinessException(BusinessError.BOOKING_NOT_FOUND)
        return bookings.findLockedById(id) ?: throw BusinessException(BusinessError.BOOKING_NOT_FOUND)
    }

    private fun validateBookability(slot: Slot, user: User) {
        if (slot.slotSet.owner.id == user.id || !slots.isVisibleToUser(slot.id!!, user.id!!)) {
            throw BusinessException(BusinessError.SLOT_NOT_AVAILABLE)
        }
        if (!slot.startAt.isAfter(clock.instant())) throw BusinessException(BusinessError.BOOKING_STARTED)
        if (slot.status != SlotStatus.AVAILABLE) throw BusinessException(BusinessError.SLOT_TAKEN)
    }

    companion object {
        private val ACTIVE_STATUSES = setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED)
    }
}
