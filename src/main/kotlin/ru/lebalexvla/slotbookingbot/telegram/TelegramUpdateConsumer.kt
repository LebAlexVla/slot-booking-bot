package ru.lebalexvla.slotbookingbot.telegram

import org.springframework.stereotype.Component
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class TelegramUpdateConsumer(
    private val updateHandler: TelegramUpdateHandler
) : DefaultLongPollingUpdateConsumer() {

    override fun consume(update: Update) {
        updateHandler.handle(update)
    }
}