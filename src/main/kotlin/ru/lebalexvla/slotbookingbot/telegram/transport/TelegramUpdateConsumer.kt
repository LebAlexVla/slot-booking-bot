package ru.lebalexvla.slotbookingbot.telegram.transport

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer
import org.telegram.telegrambots.meta.api.objects.Update
import ru.lebalexvla.slotbookingbot.telegram.TelegramEventHandler

@Component
class TelegramUpdateConsumer(
    private val updateMapper: TelegramUpdateMapper,
    private val eventHandler: TelegramEventHandler
) : DefaultLongPollingUpdateConsumer() {

    override fun consume(update: Update) {
        try {
            updateMapper.map(update)?.let(eventHandler::handle)
        } catch (exception: Exception) {
            logger.error(
                "Failed to process Telegram update {}",
                update.updateId,
                exception
            )
        }
    }

    companion object {
        private val logger =
            LoggerFactory.getLogger(TelegramUpdateConsumer::class.java)
    }
}
