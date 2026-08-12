package ru.lebalexvla.slotbookingbot.telegram

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActorResolver
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramEvent
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramApiRequestException
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramGateway

@Component
class TelegramEventHandler(
    private val actorResolver: TelegramActorResolver,
    private val telegramGateway: TelegramGateway,
    private val router: TelegramRouter
) {
    fun handle(event: TelegramEvent) {
        if (event is TelegramEvent.Callback) acknowledgeCallback(event.callbackQueryId)

        val response = try {
            val actor = actorResolver.resolveAndSync(event.actor)
            router.handle(actor, event)
        } catch (exception: BusinessException) {
            TelegramViews.error(event.actor.chatId, exception.error)
        } catch (exception: Exception) {
            logger.error("Failed to handle Telegram event for user {}", event.actor.telegramUserId, exception)
            TelegramViews.menu(event.actor.chatId, "Не удалось выполнить действие. Попробуйте ещё раз или откройте /menu.")
        }
        telegramGateway.sendMessage(response)
    }

    private fun acknowledgeCallback(callbackQueryId: String) {
        try {
            telegramGateway.acknowledgeCallback(callbackQueryId)
        } catch (exception: TelegramApiRequestException) {
            logger.warn("Failed to acknowledge Telegram callback {}", callbackQueryId, exception)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TelegramEventHandler::class.java)
    }
}
