package ru.lebalexvla.slotbookingbot.booking

import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.slot.SlotStatus
import java.time.Instant

enum class BookingDecision(val result: BookingStatus) {
    CONFIRM(BookingStatus.CONFIRMED),
    REJECT(BookingStatus.REJECTED),
    CANCEL(BookingStatus.CANCELED)
}

object BookingLifecycle {
    fun apply(booking: Booking, decision: BookingDecision, now: Instant, expected: BookingStatus?) {
        // A retry must not touch a slot already reused by a newer booking.
        if (booking.status == decision.result) return
        if (expected != null && booking.status != expected) stale()
        requireActive(booking)

        when (decision) {
            BookingDecision.CONFIRM -> {
                if (booking.status != BookingStatus.PENDING) stale()
                requireFuture(booking, now)
                booking.confirmedAt = now
                booking.slot.status = SlotStatus.BOOKED
            }
            BookingDecision.REJECT -> {
                if (booking.status != BookingStatus.PENDING) stale()
                release(booking, now)
            }
            BookingDecision.CANCEL -> {
                if (booking.status == BookingStatus.CONFIRMED) requireFuture(booking, now)
                booking.canceledAt = now
                release(booking, now)
            }
        }
        booking.status = decision.result
    }

    private fun requireActive(booking: Booking) {
        val expected = when (booking.status) {
            BookingStatus.PENDING -> SlotStatus.PENDING_CONFIRMATION
            BookingStatus.CONFIRMED -> SlotStatus.BOOKED
            else -> stale()
        }
        check(booking.slot.status == expected) { "Booking and slot states are inconsistent" }
    }

    private fun requireFuture(booking: Booking, now: Instant) {
        if (!booking.slot.startAt.isAfter(now)) throw BusinessException(BusinessError.BOOKING_STARTED)
    }

    private fun release(booking: Booking, now: Instant) {
        booking.slot.status = if (booking.slot.startAt.isAfter(now)) SlotStatus.AVAILABLE else SlotStatus.EXPIRED
    }

    private fun stale(): Nothing = throw BusinessException(BusinessError.STALE_BOOKING)
}
