package ru.lebalexvla.slotbookingbot.telegram.common

import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.user.RegisterOrUpdateUserCommand
import ru.lebalexvla.slotbookingbot.user.UserService

@Component
class TelegramActorResolver(
    private val userService: UserService
) {

    fun resolveAndSync(profile: TelegramActorProfile): TelegramActor {
        val userId = userService.registerOrUpdate(
            RegisterOrUpdateUserCommand(
                telegramUserId = profile.telegramUserId,
                telegramChatId = profile.chatId,
                username = profile.username,
                firstName = profile.firstName,
                lastName = profile.lastName
            )
        )

        return TelegramActor(
            userId = userId,
            chatId = profile.chatId
        )
    }
}
