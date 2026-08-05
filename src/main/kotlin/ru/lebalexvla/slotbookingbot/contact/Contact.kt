package ru.lebalexvla.slotbookingbot.contact

import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Entity
import jakarta.persistence.Table
import ru.lebalexvla.slotbookingbot.user.User
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "contacts")
class Contact(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    var owner: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_user_id")
    var contactUser: User,

    var createdAt: Instant = Instant.now()
)