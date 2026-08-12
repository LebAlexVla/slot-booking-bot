package ru.lebalexvla.slotbookingbot.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository
) {

    @Transactional
    fun registerOrUpdate(command: RegisterOrUpdateUserCommand): UUID {
        val user = userRepository.findByTelegramUserId(command.telegramUserId)

        if (user == null) {
            val savedUser = userRepository.save(
                User(
                    telegramUserId = command.telegramUserId,
                    telegramChatId = command.telegramChatId,
                    username = command.username,
                    firstName = command.firstName,
                    lastName = command.lastName
                )
            )

            return checkNotNull(savedUser.id) {
                "Saved user must have an id"
            }
        }

        val userId = checkNotNull(user.id) {
            "Existing user must have an id"
        }

        user.telegramChatId = command.telegramChatId
        user.username = command.username
        user.firstName = command.firstName
        user.lastName = command.lastName

        return userId
    }
}
