package ru.lebalexvla.slotbookingbot.booking

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.lebalexvla.slotbookingbot.slot.Slot
import ru.lebalexvla.slotbookingbot.slot.SlotRepository
import ru.lebalexvla.slotbookingbot.slot.SlotStatus
import ru.lebalexvla.slotbookingbot.user.User
import ru.lebalexvla.slotbookingbot.user.UserRepository
import java.time.Instant
import java.util.UUID

@Service
class BookingService(
    private val bookingRepository: BookingRepository,
    private val slotRepository: SlotRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun book(command: BookSlotCommand): UUID {
        val user = getLockedUser(command.userId)
        val slot = getLockedSlot(command.slotId)

        validateBookability(slot, user)
        validateBookingLimit(slot, user)

        val booking = bookingRepository.save(
            Booking(
                slot = slot,
                bookedBy = user,
                proposedPlace = normalize(command.proposedPlace),
                comment = normalize(command.comment)
            )
        )

        slot.status = SlotStatus.PENDING_CONFIRMATION

        return booking.id!!
    }

    private fun getLockedUser(userId: UUID): User =
        userRepository.findLockedById(userId)
            ?: throw IllegalArgumentException("User not found")

    private fun getLockedSlot(slotId: UUID): Slot =
        slotRepository.findLockedById(slotId)
            ?: throw IllegalArgumentException("Slot not found")

    private fun validateBookability(
        slot: Slot,
        user: User
    ) {
        check(slot.status == SlotStatus.AVAILABLE) {
            "Slot is not available"
        }

        check(slot.startAt.isAfter(Instant.now())) {
            "Slot has already started"
        }

        check(
            slotRepository.isVisibleToUser(
                slotId = slot.id!!,
                userId = user.id!!
            )
        ) {
            "Slot is not visible to user"
        }
    }

    private fun validateBookingLimit(
        slot: Slot,
        user: User
    ) {
        val activeBookings =
            bookingRepository.countByBookedByAndSlotSlotSetAndStatusIn(
                bookedBy = user,
                slotSet = slot.slotSet,
                statuses = ACTIVE_STATUSES
            )

        check(
            activeBookings < slot.slotSet.maxBookingsPerUser
        ) {
            "Booking limit reached"
        }
    }

    private fun normalize(value: String?): String? =
        value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    companion object {
        private val ACTIVE_STATUSES = setOf(
            BookingStatus.PENDING,
            BookingStatus.CONFIRMED
        )
    }
}