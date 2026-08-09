package ru.lebalexvla.slotbookingbot.booking

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import ru.lebalexvla.slotbookingbot.slot.SlotSet
import ru.lebalexvla.slotbookingbot.user.User
import java.util.UUID

interface BookingRepository : JpaRepository<Booking, UUID> {

    fun countByBookedByAndSlotSlotSetAndStatusIn(
        bookedBy: User,
        slotSet: SlotSet,
        statuses: Collection<BookingStatus>
    ): Long

    fun findAllByBookedByAndSlotSlotSet(
        bookedBy: User,
        slotSet: SlotSet
    ): List<Booking>

    fun findAllBySlotId(
        slotId: UUID
    ): List<Booking>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findLockedById(
        id: UUID
    ): Booking?
}