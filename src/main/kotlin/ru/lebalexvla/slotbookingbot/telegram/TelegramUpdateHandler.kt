package ru.lebalexvla.slotbookingbot.telegram

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class TelegramUpdateHandler(
    private val messageSender: TelegramMessageSender
) {
    fun handle(update: Update) {
        val message = update.message ?: return
        val text = message.text ?: return

        val command = text
            .substringBefore(' ')
            .substringBefore('@')

        if (command == "/start") {
            messageSender.send(
                message.chatId,
                "Привет!!!"
            )
        }
    }
}