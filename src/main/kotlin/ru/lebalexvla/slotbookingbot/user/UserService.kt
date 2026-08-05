package ru.lebalexvla.slotbookingbot.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    val userRepository: UserRepository
) {

    @Transactional
    fun registerOrUpdate(command: RegisterOrUpdateUserCommand) {
        val user = userRepository.findByTelegramUserId(command.telegramUserId);

        if (user == null) {
            userRepository.save(
                User(
                    telegramUserId = command.telegramUserId,
                    telegramChatId =  command.telegramChatId,
                    username = command.username,
                    firstName = command.firstName,
                    lastName = command.lastName
                )
            )

            return
        }

        user.telegramChatId = command.telegramChatId
        user.username = command.username
        user.firstName = command.firstName
        user.lastName = command.lastName
    }
}