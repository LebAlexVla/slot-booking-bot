package ru.lebalexvla.slotbookingbot.slot

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

private const val CARD_QUERY = """
    SELECT s.id, ss.id AS slotSetId, s.start_at AS startAt, s.end_at AS endAt,
           ss.place, ss.description, s.status, ss.max_bookings_per_user AS maxBookingsPerUser,
           concat_ws(' ', owner.first_name, owner.last_name) AS ownerName,
           COALESCE(c.name, concat_ws(' ', target.first_name, target.last_name)) AS targetName,
           (c.archived_at IS NOT NULL) AS targetArchived
    FROM slots s
    JOIN slot_sets ss ON ss.id = s.slot_set_id
    JOIN users owner ON owner.id = ss.owner_id
    LEFT JOIN users target ON target.id = ss.target_user_id
    LEFT JOIN categories c ON c.id = ss.target_category_id
"""
private const val VISIBLE_FILTER = """
    WHERE s.status = 'AVAILABLE' AND s.start_at > :now
      AND EXISTS (SELECT 1 FROM slot_visibility sv WHERE sv.slot_id = s.id AND sv.user_id = :userId)
"""

interface SlotRepository : JpaRepository<Slot, UUID> {
    fun findAllBySlotSetId(slotSetId: UUID): List<Slot>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findLockedById(id: UUID): Slot?

    @Query(value = """
        SELECT EXISTS (SELECT 1 FROM slot_visibility WHERE slot_id = :slotId AND user_id = :userId)
    """, nativeQuery = true)
    fun isVisibleToUser(slotId: UUID, userId: UUID): Boolean

    @Query(value = CARD_QUERY + VISIBLE_FILTER + " ORDER BY s.start_at, s.id", nativeQuery = true)
    fun findVisibleSlots(
        userId: UUID, now: Instant, pageable: Pageable = Pageable.unpaged()
    ): Slice<SlotCardProjection>

    @Query(value = CARD_QUERY + VISIBLE_FILTER + " AND s.id = :slotId", nativeQuery = true)
    fun findVisibleSlot(userId: UUID, slotId: UUID, now: Instant): SlotCardProjection?

    @Query(value = CARD_QUERY + """
        WHERE ss.owner_id = :ownerId
        ORDER BY CASE WHEN s.start_at > :now THEN 0 ELSE 1 END, s.start_at, s.id
    """, nativeQuery = true)
    fun findOwnedSlots(ownerId: UUID, now: Instant, pageable: Pageable): Slice<SlotCardProjection>

    @Query(value = CARD_QUERY + " WHERE ss.owner_id = :ownerId AND s.id = :slotId", nativeQuery = true)
    fun findOwnedSlot(ownerId: UUID, slotId: UUID): SlotCardProjection?

    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM slots s
            JOIN slot_sets ss ON ss.id = s.slot_set_id
            LEFT JOIN categories c ON c.id = ss.target_category_id
            WHERE ss.owner_id = :ownerId AND s.start_at < :endAt AND s.end_at > :startAt
              AND (s.status IN ('PENDING_CONFIRMATION', 'BOOKED')
                   OR (s.status = 'AVAILABLE' AND c.archived_at IS NULL))
        )
    """, nativeQuery = true)
    fun overlaps(ownerId: UUID, startAt: Instant, endAt: Instant): Boolean
}
