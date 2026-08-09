package ru.lebalexvla.slotbookingbot.telegram

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class TelegramUpdateConsumer(
    private val updateHandler: TelegramUpdateHandler
) : DefaultLongPollingUpdateConsumer() {

    override fun consume(update: Update) {
        try {
            updateHandler.handle(update)
        } catch (exception: Exception) {
            logger.error(
                "Failed to process Telegram update ${update.updateId}",
                exception
            )
        }
    }

    companion object {
        private val logger =
            LoggerFactory.getLogger(TelegramUpdateConsumer::class.java)
    }
}
