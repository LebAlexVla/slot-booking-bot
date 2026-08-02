package ru.lebalexvla.slotbookingbot.user

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    var telegramUserId: Long,

    var telegramChatId: Long,

    var username: String? = null,

    var firstName: String? = null,

    var lastName: String? = null,

    var createdAt: Instant = Instant.now()
) {
}