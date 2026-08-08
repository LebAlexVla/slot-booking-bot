package ru.lebalexvla.slotbookingbot.slot

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "slots")
class Slot(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_set_id")
    var slotSet: SlotSet,

    var startAt: Instant,

    var endAt: Instant,

    @Enumerated(EnumType.STRING)
    var status: SlotStatus = SlotStatus.AVAILABLE,

    var createdAt: Instant = Instant.now()
)