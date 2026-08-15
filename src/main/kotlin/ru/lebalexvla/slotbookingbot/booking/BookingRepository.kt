package ru.lebalexvla.slotbookingbot.booking

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import java.time.Instant
import ru.lebalexvla.slotbookingbot.slot.SlotSet
import ru.lebalexvla.slotbookingbot.user.User
import java.util.UUID

private const val CARD_SELECT = """
    SELECT new ru.lebalexvla.slotbookingbot.booking.BookingCard(
        b.id, s.id, owner.id, booker.id,
        concat(owner.firstName, ' ', coalesce(owner.lastName, '')),
        concat(booker.firstName, ' ', coalesce(booker.lastName, '')),
        s.startAt, s.endAt, b.status, ss.place, b.proposedPlace, b.comment, ss.description)
    FROM Booking b JOIN b.slot s JOIN s.slotSet ss JOIN ss.owner owner JOIN b.bookedBy booker
"""

interface BookingRepository : JpaRepository<Booking, UUID> {
    @Query("SELECT b.slot.id FROM Booking b WHERE b.id = :id")
    fun findSlotId(id: UUID): UUID?

    @Query(CARD_SELECT + " WHERE b.id = :id AND (owner.id = :actorId OR booker.id = :actorId)")
    fun findCard(actorId: UUID, id: UUID): BookingCard?

    @Query(CARD_SELECT + """
        WHERE (:incoming = true AND owner.id = :actorId) OR (:incoming = false AND booker.id = :actorId)
        ORDER BY CASE
            WHEN b.status = 'PENDING' AND s.startAt > :now THEN 0
            WHEN b.status = 'CONFIRMED' AND s.startAt > :now THEN 1
            ELSE 2 END, s.startAt, b.id
    """)
    fun findCards(actorId: UUID, incoming: Boolean, now: Instant, pageable: Pageable): Slice<BookingCard>

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
