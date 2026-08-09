package ru.lebalexvla.slotbookingbot.telegram

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.generics.TelegramClient

class TelegramMessageSenderTest {

    private val telegramClient: TelegramClient = mock()
    private val messageSender = TelegramMessageSender(telegramClient)

    @Test
    fun `wraps Telegram API exception`() {
        val telegramException =
            TelegramApiException("Telegram is unavailable")

        whenever(
            telegramClient.execute(any<SendMessage>())
        ).thenThrow(telegramException)

        assertThatThrownBy {
            messageSender.send(
                chatId = 123L,
                text = "Hello"
            )
        }
            .isInstanceOf(TelegramDeliveryException::class.java)
            .hasCause(telegramException)
    }
}
