package ru.lebalexvla.slotbookingbot.telegram

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.generics.TelegramClient

@Component
class TelegramMessageSender(
    private val telegramClient: TelegramClient
) {
    fun send(chatId: Long, text: String) {
        try {
            telegramClient.execute(
                SendMessage(chatId.toString(), text)
            )
        } catch (exception: TelegramApiException) {
            TODO()
        }
    }
}