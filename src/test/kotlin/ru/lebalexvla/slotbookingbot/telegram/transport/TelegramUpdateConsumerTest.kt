package ru.lebalexvla.slotbookingbot.telegram.transport

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.telegram.telegrambots.meta.api.objects.Update
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActorProfile
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramEvent
import ru.lebalexvla.slotbookingbot.telegram.TelegramEventHandler

class TelegramUpdateConsumerTest {

    private val updateMapper: TelegramUpdateMapper = mock()
    private val eventHandler: TelegramEventHandler = mock()
    private val updateConsumer = TelegramUpdateConsumer(
        updateMapper,
        eventHandler
    )

    @Test
    fun `does not propagate handler failure`() {
        val update = Update().apply {
            updateId = 42
        }
        val event = TelegramEvent.Text(
            actor = TelegramActorProfile(
                telegramUserId = 123L,
                chatId = 456L,
                username = "alex",
                firstName = "Alex",
                lastName = "Test"
            ),
            text = "Hello"
        )

        whenever(updateMapper.map(update)).thenReturn(event)

        doThrow(IllegalStateException("Handler failed"))
            .whenever(eventHandler)
            .handle(event)

        assertThatCode {
            updateConsumer.consume(update)
        }.doesNotThrowAnyException()

        verify(updateMapper).map(update)
        verify(eventHandler).handle(event)
    }

    @Test
    fun `does not handle unsupported update`() {
        val update = Update().apply {
            updateId = 43
        }

        whenever(updateMapper.map(update)).thenReturn(null)

        updateConsumer.consume(update)

        verify(updateMapper).map(update)
        verifyNoInteractions(eventHandler)
    }

    @Test
    fun `does not propagate mapper failure`() {
        val update = Update().apply {
            updateId = 44
        }

        whenever(updateMapper.map(update))
            .thenThrow(IllegalStateException("Mapper failed"))

        assertThatCode {
            updateConsumer.consume(update)
        }.doesNotThrowAnyException()

        verify(updateMapper).map(update)
        verifyNoInteractions(eventHandler)
    }
}
