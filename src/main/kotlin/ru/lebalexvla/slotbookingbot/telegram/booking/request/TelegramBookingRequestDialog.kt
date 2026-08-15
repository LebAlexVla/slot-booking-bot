package ru.lebalexvla.slotbookingbot.telegram.booking.request

import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.booking.request.BookingRequest
import ru.lebalexvla.slotbookingbot.booking.request.BookingRequestService
import ru.lebalexvla.slotbookingbot.booking.request.BookingRequestStep
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.telegram.booking.BookingAction
import ru.lebalexvla.slotbookingbot.telegram.booking.BookingRequestAction
import ru.lebalexvla.slotbookingbot.telegram.booking.TelegramBookingMenu
import ru.lebalexvla.slotbookingbot.telegram.booking.reference
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramDraftCoordinator
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramEvent
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage
import java.time.Clock

@Component
class TelegramBookingRequestDialog(
    private val requests: BookingRequestService,
    private val coordinator: TelegramDraftCoordinator,
    private val views: TelegramBookingRequestViews,
    private val bookings: TelegramBookingMenu,
    private val clock: Clock
) {
    fun command(actor: TelegramActor, event: TelegramEvent.Command): OutgoingTelegramMessage? {
        if (event.name == "booking_draft") return render(actor, current(actor))
        if (event.name !in setOf("draft", "skip", "cancel")) return null
        val request = active(actor) ?: return null
        val action = when (event.name) {
            "skip" -> BookingAction.Skip(request.reference())
            "cancel" -> BookingAction.Discard(request.reference())
            else -> BookingAction.Resume
        }
        return callback(actor, action, event.updateId)
    }

    fun callback(actor: TelegramActor, action: BookingRequestAction, updateId: Int?): OutgoingTelegramMessage {
        val request = when (action) {
            is BookingAction.Begin -> coordinator.startBooking(actor.userId, action.slotId, updateId)
            BookingAction.Resume -> current(actor)
            is BookingAction.Skip -> requests.accept(actor.userId, action.draft.id, action.draft.revision, updateId, null)
            is BookingAction.Submit -> requests.submit(actor.userId, action.draft.id, action.draft.revision, updateId)
            is BookingAction.Discard -> requests.cancel(actor.userId, action.draft.id, action.draft.revision, updateId)
        }
        return render(actor, request)
    }

    fun text(actor: TelegramActor, text: String, updateId: Int?): OutgoingTelegramMessage? {
        val request = active(actor) ?: return null
        if (request.step == BookingRequestStep.REVIEW) return views.prompt(actor, request, "Проверьте заявку и используйте кнопки.")
        return render(actor, requests.accept(actor.userId, request.id, request.revision, updateId, text))
    }

    fun error(actor: TelegramActor, error: BusinessError): OutgoingTelegramMessage {
        val request = active(actor)
        return if (request != null) views.prompt(actor, request, TelegramViews.errorText(error))
        else TelegramViews.error(actor.chatId, error)
    }

    private fun render(actor: TelegramActor, request: BookingRequest): OutgoingTelegramMessage =
        if (request.step == BookingRequestStep.SUBMITTED) bookings.card(actor, checkNotNull(request.bookingId))
        else views.prompt(actor, request)

    private fun current(actor: TelegramActor) =
        requests.current(actor.userId) ?: throw BusinessException(BusinessError.BOOKING_DRAFT_NOT_FOUND)

    private fun active(actor: TelegramActor) = requests.current(actor.userId)?.takeIf { it.activeAt(clock.instant()) }
}
