package ru.lebalexvla.slotbookingbot.telegram.transport

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.GetMe
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.generics.TelegramClient

@Component
class TelegramApiGateway(private val telegramClient: TelegramClient) : TelegramGateway {
    private val cachedBotUsername by lazy {
        execute { telegramClient.execute(GetMe()) }.userName
            ?.takeIf { it.isNotBlank() }
            ?: error("Telegram bot has no username")
    }

    override fun getBotUsername(): String = cachedBotUsername

    override fun sendMessage(message: OutgoingTelegramMessage) {
        val request = SendMessage.builder()
            .chatId(message.chatId.toString())
            .text(message.text)
            .build()

        if (message.keyboard.isNotEmpty()) {
            request.replyMarkup = InlineKeyboardMarkup.builder()
                .keyboard(message.keyboard.map { row ->
                    InlineKeyboardRow(row.map { button ->
                        InlineKeyboardButton.builder()
                            .text(button.text)
                            .callbackData(button.callbackData)
                            .build()
                    })
                })
                .build()
        }
        execute { telegramClient.execute(request) }
    }

    override fun acknowledgeCallback(callbackQueryId: String) {
        require(callbackQueryId.isNotBlank()) { "Callback query id must not be blank" }
        execute {
            telegramClient.execute(
                AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).build()
            )
        }
    }

    private fun <T> execute(request: () -> T): T =
        try {
            request()
        } catch (exception: TelegramApiException) {
            throw TelegramApiRequestException(exception)
        }
}

class TelegramApiRequestException(
    cause: TelegramApiException
) : RuntimeException("Telegram API request failed", cause)
