package ru.lebalexvla.slotbookingbot.booking

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
import ru.lebalexvla.slotbookingbot.slot.Slot
import ru.lebalexvla.slotbookingbot.user.User
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bookings")
class Booking(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    var slot: Slot,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booked_by_user_id")
    var bookedBy: User,

    @Enumerated(EnumType.STRING)
    var status: BookingStatus = BookingStatus.PENDING,

    var proposedPlace: String? = null,

    var comment: String? = null,

    var createdAt: Instant = Instant.now(),

    var confirmedAt: Instant? = null,

    var canceledAt: Instant? = null
)