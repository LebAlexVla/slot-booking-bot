package ru.lebalexvla.slotbookingbot.telegram

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.lebalexvla.slotbookingbot.user.RegisterOrUpdateUserCommand
import ru.lebalexvla.slotbookingbot.user.UserService

@Component
class TelegramUpdateHandler(
    private val userService: UserService,
    private val messageSender: TelegramMessageSender
) {
    fun handle(update: Update) {
        val message = update.message ?: return
        val text = message.text ?: return

        val command = text
            .substringBefore(' ')
            .substringBefore('@')

        when (command) {
            "/start" -> handleStart(message)
        }
    }

    private fun handleStart(
        message: Message
    ) {
        val telegramUser = message.from ?: return

        userService.registerOrUpdate(
            RegisterOrUpdateUserCommand(
                telegramUserId = telegramUser.id,
                telegramChatId = message.chatId,
                username = telegramUser.userName,
                firstName = telegramUser.firstName,
                lastName = telegramUser.lastName
            )
        )

        messageSender.send(
            message.chatId,
            "Вы зарегистрированы)"
        )
    }
}