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
import ru.lebalexvla.slotbookingbot.category.Category
import ru.lebalexvla.slotbookingbot.user.User
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "slot_sets")
class SlotSet(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    var owner: User,

    @Enumerated(EnumType.STRING)
    var targetType: SlotSetTargetType,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    var targetUser: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_category_id")
    var targetCategory: Category? = null,

    var place: String? = null,

    var description: String? = null,

    var maxBookingsPerUser: Int,

    var createdAt: Instant = Instant.now()
)