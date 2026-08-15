package ru.lebalexvla.slotbookingbot.telegram.booking

import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.booking.BookingDecision
import ru.lebalexvla.slotbookingbot.booking.BookingList
import ru.lebalexvla.slotbookingbot.booking.BookingQueries
import ru.lebalexvla.slotbookingbot.booking.BookingService
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews
import java.util.UUID

@Component
class TelegramBookingMenu(
    private val bookings: BookingService,
    private val queries: BookingQueries,
    private val views: TelegramBookingViews
) {
    fun list(actor: TelegramActor, mode: BookingList, page: Int = 0) =
        views.list(actor, mode, queries.list(actor.userId, mode, page))

    fun card(actor: TelegramActor, id: UUID, mode: BookingList = BookingList.MINE, page: Int = 0, notice: String? = null) =
        views.card(actor, queries.card(actor.userId, id), mode, page, notice)

    fun decide(actor: TelegramActor, action: BookingAction.Decide) = try {
        when (action.decision) {
            BookingDecision.CONFIRM -> bookings.confirm(action.id, actor.userId, action.expected)
            BookingDecision.REJECT -> bookings.reject(action.id, actor.userId, action.expected)
            BookingDecision.CANCEL -> bookings.cancel(action.id, actor.userId, action.expected)
        }
        card(actor, action.id, action.mode, action.page)
    } catch (exception: BusinessException) {
        card(actor, action.id, action.mode, action.page, TelegramViews.errorText(exception.error))
    }
}
