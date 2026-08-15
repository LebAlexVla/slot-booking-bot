package ru.lebalexvla.slotbookingbot.telegram.booking

import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.booking.BookingList
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.telegram.booking.request.TelegramBookingRequestDialog
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramEvent
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage

@Component
class TelegramBookingHandler(
    private val menu: TelegramBookingMenu,
    private val request: TelegramBookingRequestDialog
) {
    fun handle(actor: TelegramActor, event: TelegramEvent): OutgoingTelegramMessage? = try {
        when (event) {
            is TelegramEvent.Command -> when (event.name) {
                "bookings" -> menu.list(actor, BookingList.MINE)
                "requests" -> menu.list(actor, BookingList.INCOMING)
                else -> request.command(actor, event)
            }
            is TelegramEvent.Text -> request.text(actor, event.text, event.updateId)
            is TelegramEvent.Callback -> if (event.data?.startsWith("b:") == true) {
                callback(actor, BookingAction.parse(event.data), event.updateId)
            } else null
        }
    } catch (exception: BusinessException) {
        request.error(actor, exception.error)
    }

    private fun callback(actor: TelegramActor, action: BookingAction?, updateId: Int?): OutgoingTelegramMessage =
        when (action) {
            is BookingAction.Listing -> menu.list(actor, action.mode, action.page)
            is BookingAction.Card -> menu.card(actor, action.id, action.mode, action.page)
            is BookingAction.Decide -> menu.decide(actor, action)
            is BookingRequestAction -> request.callback(actor, action, updateId)
            null -> throw BusinessException(BusinessError.STALE_BOOKING)
        }
}
