package ru.lebalexvla.slotbookingbot.telegram

import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramEvent
import ru.lebalexvla.slotbookingbot.telegram.directory.TelegramDirectoryHandler
import ru.lebalexvla.slotbookingbot.telegram.slot.publication.TelegramPublicationDialog
import ru.lebalexvla.slotbookingbot.telegram.slot.PublicationAction
import ru.lebalexvla.slotbookingbot.telegram.slot.SlotAction
import ru.lebalexvla.slotbookingbot.telegram.slot.SlotListMode
import ru.lebalexvla.slotbookingbot.telegram.slot.TelegramSlotMenu
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage

@Component
class TelegramRouter(
    private val directory: TelegramDirectoryHandler,
    private val slots: TelegramSlotMenu,
    private val publication: TelegramPublicationDialog
) {
    fun handle(actor: TelegramActor, event: TelegramEvent): OutgoingTelegramMessage =
        slotResponse(actor, event) ?: directory.handle(actor, event)

    private fun slotResponse(actor: TelegramActor, event: TelegramEvent): OutgoingTelegramMessage? = try {
        when (event) {
            is TelegramEvent.Command -> command(actor, event)
            is TelegramEvent.Callback -> if (event.data?.startsWith("s:") == true) {
                callback(actor, SlotAction.parse(event.data), event.updateId)
            } else null
            is TelegramEvent.Text -> publication.text(actor, event.text, event.updateId)
        }
    } catch (exception: BusinessException) {
        publication.error(actor, exception.error)
    }

    private fun command(actor: TelegramActor, event: TelegramEvent.Command): OutgoingTelegramMessage? =
        when (event.name) {
            "slots" -> slots.list(actor, SlotListMode.AVAILABLE)
            "my_slots" -> slots.list(actor, SlotListMode.OWN)
            else -> publication.command(actor, event)
        }

    private fun callback(actor: TelegramActor, action: SlotAction?, updateId: Int?): OutgoingTelegramMessage =
        when (action) {
            is SlotAction.Listing -> slots.list(actor, action.mode, action.page)
            is SlotAction.Card -> slots.card(actor, action.mode, action.id, action.page)
            is PublicationAction -> publication.callback(actor, action, updateId)
            null -> throw BusinessException(BusinessError.STALE_DRAFT)
        }
}
