package ru.lebalexvla.slotbookingbot.telegram

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.telegram.telegrambots.meta.api.objects.Update

class TelegramUpdateConsumerTest {

    private val updateHandler: TelegramUpdateHandler = mock()
    private val updateConsumer = TelegramUpdateConsumer(updateHandler)

    @Test
    fun `does not propagate handler failure`() {
        val update = Update().apply {
            updateId = 42
        }

        doThrow(IllegalStateException("Handler failed"))
            .whenever(updateHandler)
            .handle(update)

        assertThatCode {
            updateConsumer.consume(update)
        }.doesNotThrowAnyException()

        verify(updateHandler).handle(update)
    }
}
