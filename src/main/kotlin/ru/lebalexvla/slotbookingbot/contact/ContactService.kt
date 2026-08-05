package ru.lebalexvla.slotbookingbot.contact

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.lebalexvla.slotbookingbot.user.UserRepository
import java.util.UUID

@Service
class ContactService(
    private val contactRepository: ContactRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun addContact(
        ownerId: UUID,
        contactUserId: UUID
    ) {
        require(ownerId != contactUserId) {
            "User cannot add themselves as a contact"
        }

        if (
            contactRepository.existsByOwnerIdAndContactUserId(
                ownerId,
                contactUserId
            )
        ) {
            return
        }

        val owner = userRepository.findById(ownerId)
            .orElseThrow { IllegalArgumentException("Owner not found") }

        val contactUser = userRepository.findById(contactUserId)
            .orElseThrow { IllegalArgumentException("Contact user not found") }

        contactRepository.save(
            Contact(
                owner = owner,
                contactUser = contactUser
            )
        )
    }

    @Transactional(readOnly = true)
    fun getContacts(ownerId: UUID): List<Contact> =
        contactRepository.findAllByOwnerId(ownerId)
}