package ru.lebalexvla.slotbookingbot.booking

import ru.lebalexvla.slotbookingbot.slot.Slot
import ru.lebalexvla.slotbookingbot.slot.SlotStatus
import java.time.Instant

object BookingLifecycle {

    fun confirm(
        booking: Booking,
        slot: Slot,
        now: Instant
    ) {
        requireState(
            booking = booking,
            expectedBookingStatus = BookingStatus.PENDING,
            slot = slot,
            expectedSlotStatus = SlotStatus.PENDING_CONFIRMATION
        )

        booking.status = BookingStatus.CONFIRMED
        booking.confirmedAt = now

        slot.status = SlotStatus.BOOKED
    }

    fun reject(
        booking: Booking,
        slot: Slot
    ) {
        requireState(
            booking = booking,
            expectedBookingStatus = BookingStatus.PENDING,
            slot = slot,
            expectedSlotStatus = SlotStatus.PENDING_CONFIRMATION
        )

        booking.status = BookingStatus.REJECTED
        slot.status = SlotStatus.AVAILABLE
    }

    fun cancel(
        booking: Booking,
        slot: Slot,
        now: Instant
    ) {
        val expectedSlotStatus = when (booking.status) {
            BookingStatus.PENDING ->
                SlotStatus.PENDING_CONFIRMATION

            BookingStatus.CONFIRMED ->
                SlotStatus.BOOKED

            BookingStatus.REJECTED,
            BookingStatus.CANCELED ->
                throw IllegalStateException(
                    "Booking cannot be canceled"
                )
        }

        check(slot.status == expectedSlotStatus) {
            "Booking and slot states are inconsistent"
        }

        check(slot.startAt.isAfter(now)) {
            "Slot has already started"
        }

        booking.status = BookingStatus.CANCELED
        booking.canceledAt = now

        slot.status = SlotStatus.AVAILABLE
    }

    private fun requireState(
        booking: Booking,
        expectedBookingStatus: BookingStatus,
        slot: Slot,
        expectedSlotStatus: SlotStatus
    ) {
        check(booking.status == expectedBookingStatus) {
            "Unexpected booking status: ${booking.status}"
        }

        check(slot.status == expectedSlotStatus) {
            "Booking and slot states are inconsistent"
        }
    }
}