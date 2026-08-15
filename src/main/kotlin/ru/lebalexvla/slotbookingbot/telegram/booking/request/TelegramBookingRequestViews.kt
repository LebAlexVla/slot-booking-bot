package ru.lebalexvla.slotbookingbot.telegram.booking.request

import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.booking.request.BookingRequest
import ru.lebalexvla.slotbookingbot.booking.request.BookingRequestStep
import ru.lebalexvla.slotbookingbot.slot.SlotInterval
import ru.lebalexvla.slotbookingbot.slot.SlotPublicationProperties
import ru.lebalexvla.slotbookingbot.telegram.booking.BookingAction
import ru.lebalexvla.slotbookingbot.telegram.booking.reference
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews.button
import ru.lebalexvla.slotbookingbot.telegram.slot.TelegramSlotTime
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage
import java.time.Clock

@Component
class TelegramBookingRequestViews(private val properties: SlotPublicationProperties, private val clock: Clock) {
    fun prompt(actor: TelegramActor, request: BookingRequest, notice: String? = null): OutgoingTelegramMessage {
        if (request.step == BookingRequestStep.CANCELED) {
            return TelegramViews.menu(actor.chatId, "Создание заявки отменено. Выберите слот через /slots.")
        }
        if (!request.expiresAt.isAfter(clock.instant())) {
            return TelegramViews.menu(actor.chatId, "Срок черновика заявки истёк. Выберите слот через /slots.")
        }
        val ref = request.reference()
        val primary = when (request.step) {
            BookingRequestStep.COMMENT, BookingRequestStep.PLACE -> button("Пропустить", BookingAction.Skip(ref))
            BookingRequestStep.REVIEW -> button("Отправить заявку", BookingAction.Submit(ref))
            else -> error("Submitted request must open its booking card")
        }
        return OutgoingTelegramMessage(
            actor.chatId,
            listOfNotNull(notice, text(request)).joinToString("\n\n"),
            listOf(listOf(primary), listOf(button("Отменить создание заявки", BookingAction.Discard(ref))), TelegramViews.toMenu)
        )
    }

    private fun text(request: BookingRequest): String = buildString {
        appendLine("Заявка на встречу с ${request.ownerName.take(120)}")
        appendLine(TelegramSlotTime.format(SlotInterval(request.startAt, request.endAt), properties.timeZone))
        appendLine("Часовой пояс: ${properties.timeZone}")
        appendLine("Место владельца: ${request.place ?: "не указано"}")
        appendLine()
        appendLine(when (request.step) {
            BookingRequestStep.COMMENT -> "Добавьте комментарий (до 1000 символов) или нажмите «Пропустить»."
            BookingRequestStep.PLACE -> "Предложите своё место (до 200 символов) или нажмите «Пропустить»."
            BookingRequestStep.REVIEW -> preview(request)
            else -> error("Request has no input step")
        })
        appendLine("\nДо отправки заявки слот остаётся свободным.")
        append("Черновик сохранён до ${TelegramSlotTime.format(request.expiresAt, properties.timeZone)}. Продолжить: /draft.")
    }

    private fun preview(request: BookingRequest): String = buildString {
        appendLine("Проверьте заявку перед отправкой.")
        appendLine("Комментарий: ${request.comment ?: "не указан"}")
        append("Предложенное место: ${request.proposedPlace ?: "не указано"}")
        if (request.proposedPlace != null) append("\nПредложение места требует отдельного согласования.")
    }
}
