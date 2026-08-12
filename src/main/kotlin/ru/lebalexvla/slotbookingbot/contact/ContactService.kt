package ru.lebalexvla.slotbookingbot.contact

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.common.ListPage
import ru.lebalexvla.slotbookingbot.user.UserRepository
import ru.lebalexvla.slotbookingbot.user.UserSummary
import ru.lebalexvla.slotbookingbot.user.toSummary
import java.util.UUID

@Service
class ContactService(
    private val contactRepository: ContactRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun addContact(ownerId: UUID, contactUserId: UUID): UserSummary {
        if (ownerId == contactUserId) throw BusinessException(BusinessError.SELF_CONTACT)
        if (!userRepository.existsById(ownerId)) throw BusinessException(BusinessError.USER_NOT_FOUND)
        val contact = userRepository.findById(contactUserId)
            .orElseThrow { BusinessException(BusinessError.USER_NOT_FOUND) }

        // Atomic insertion also handles simultaneous reciprocal additions without locking both users.
        contactRepository.insertIfAbsent(UUID.randomUUID(), ownerId, contactUserId)
        return contact.toSummary()
    }

    @Transactional(readOnly = true)
    fun getCandidate(ownerId: UUID, userId: UUID): UserSummary {
        if (ownerId == userId) throw BusinessException(BusinessError.SELF_CONTACT)
        return userRepository.findById(userId)
            .orElseThrow { BusinessException(BusinessError.USER_NOT_FOUND) }
            .toSummary()
    }

    @Transactional(readOnly = true)
    fun getContacts(ownerId: UUID, page: Int = 0): ListPage<UserSummary> =
        ListPage.from(
            contactRepository.findAllByOwnerIdOrderByCreatedAtAscIdAsc(ownerId, ListPage.request(page))
        ) { it.contactUser.toSummary() }
}
