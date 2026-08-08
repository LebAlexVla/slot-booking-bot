package ru.lebalexvla.slotbookingbot.slot

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SlotRepository : JpaRepository<Slot, UUID> {

    fun findAllBySlotSetId(
        slotSetId: UUID
    ): List<Slot>

    @Query(
        value = """
            SELECT
                s.id AS id,
                ss.id AS slotSetId,
                s.start_at AS startAt,
                s.end_at AS endAt,
                ss.place AS place,
                ss.description AS description
            FROM slots s
            JOIN slot_sets ss
                ON ss.id = s.slot_set_id
            JOIN slot_visibility sv
                ON sv.slot_id = s.id
            WHERE sv.user_id = :userId
              AND s.status = 'AVAILABLE'
              AND s.start_at >= :now
            ORDER BY s.start_at
        """,
        nativeQuery = true
    )
    fun findVisibleSlots(
        userId: UUID,
        now: Instant
    ): List<VisibleSlotProjection>
}