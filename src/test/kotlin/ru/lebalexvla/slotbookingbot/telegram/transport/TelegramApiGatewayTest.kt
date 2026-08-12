package ru.lebalexvla.slotbookingbot.telegram.transport

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.GetMe
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.generics.TelegramClient

class TelegramApiGatewayTest {

    private val telegramClient: TelegramClient = mock()
    private val gateway = TelegramApiGateway(telegramClient)

    @Test
    fun `maps inline keyboard without Telegram types escaping gateway`() {
        gateway.sendMessage(OutgoingTelegramMessage(123L, "Menu", listOf(
            listOf(TelegramButton("Contacts", "c:0"), TelegramButton("Categories", "g:0"))
        )))

        argumentCaptor<SendMessage>().apply {
            verify(telegramClient).execute(capture())
            val keyboard = (firstValue.replyMarkup as InlineKeyboardMarkup).keyboard
            assertThat(keyboard).hasSize(1)
            assertThat(keyboard.first().map { it.text }).containsExactly("Contacts", "Categories")
            assertThat(keyboard.first().map { it.callbackData }).containsExactly("c:0", "g:0")
            firstValue.validate()
        }
    }

    @Test
    fun `loads bot identity lazily and caches successful response`() {
        whenever(telegramClient.execute(any<GetMe>())).thenReturn(
            User.builder().id(999L).isBot(true).firstName("Bot").userName("slot_bot").build()
        )
        assertThat(gateway.getBotUsername()).isEqualTo("slot_bot")
        assertThat(gateway.getBotUsername()).isEqualTo("slot_bot")
        verify(telegramClient, times(1)).execute(any<GetMe>())
    }

    @Test
    fun `failed identity lookup can be retried`() {
        whenever(telegramClient.execute(any<GetMe>()))
            .thenThrow(TelegramApiException("Unavailable"))
            .thenReturn(User.builder().id(999L).isBot(true).firstName("Bot").userName("slot_bot").build())
        assertThatThrownBy { gateway.getBotUsername() }.isInstanceOf(TelegramApiRequestException::class.java)
        assertThat(gateway.getBotUsername()).isEqualTo("slot_bot")
    }

    @Test
    fun `maps outgoing message to Telegram request`() {
        gateway.sendMessage(
            OutgoingTelegramMessage(
                chatId = 123L,
                text = "Hello"
            )
        )

        argumentCaptor<SendMessage>().apply {
            verify(telegramClient).execute(capture())

            assertThat(firstValue.chatId).isEqualTo("123")
            assertThat(firstValue.text).isEqualTo("Hello")
            assertThat(firstValue.replyMarkup).isNull()
        }
    }

    @Test
    fun `rejects blank outgoing values`() {
        assertThatThrownBy {
            OutgoingTelegramMessage(
                chatId = 123L,
                text = "   "
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            gateway.acknowledgeCallback("   ")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `maps callback acknowledgement to Telegram request`() {
        gateway.acknowledgeCallback("callback-42")

        argumentCaptor<AnswerCallbackQuery>().apply {
            verify(telegramClient).execute(capture())
            assertThat(firstValue.callbackQueryId)
                .isEqualTo("callback-42")
        }
    }

    @Test
    fun `wraps Telegram API exception`() {
        val telegramException =
            TelegramApiException("Telegram is unavailable")

        whenever(
            telegramClient.execute(any<AnswerCallbackQuery>())
        ).thenThrow(telegramException)

        assertThatThrownBy {
            gateway.acknowledgeCallback("callback-42")
        }
            .isInstanceOf(TelegramApiRequestException::class.java)
            .hasCause(telegramException)
    }
}
