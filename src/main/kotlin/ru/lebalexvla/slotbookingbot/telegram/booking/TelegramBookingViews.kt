package ru.lebalexvla.slotbookingbot.telegram.booking

import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.booking.BookingCard
import ru.lebalexvla.slotbookingbot.booking.BookingDecision
import ru.lebalexvla.slotbookingbot.booking.BookingList
import ru.lebalexvla.slotbookingbot.booking.BookingStatus
import ru.lebalexvla.slotbookingbot.common.ListPage
import ru.lebalexvla.slotbookingbot.slot.SlotInterval
import ru.lebalexvla.slotbookingbot.slot.SlotPublicationProperties
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews.button
import ru.lebalexvla.slotbookingbot.telegram.slot.TelegramSlotTime
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramButton
import java.time.Clock

@Component
class TelegramBookingViews(private val properties: SlotPublicationProperties, private val clock: Clock) {
    fun list(actor: TelegramActor, mode: BookingList, page: ListPage<BookingCard>): OutgoingTelegramMessage {
        val cards = page.items.mapIndexed { index, booking ->
            listOf(button("Подробнее · ${index + 1}", BookingAction.Card(booking.id, mode, page.number)))
        }
        return OutgoingTelegramMessage(
            actor.chatId, listText(mode, page),
            cards + TelegramViews.pagination(page) { BookingAction.Listing(mode, it) } + listOf(TelegramViews.toMenu)
        )
    }

    fun card(actor: TelegramActor, booking: BookingCard, mode: BookingList, page: Int, notice: String?) =
        OutgoingTelegramMessage(
            actor.chatId,
            listOfNotNull(notice, cardText(booking)).joinToString("\n\n"),
            decisions(actor, booking, mode, page) +
                listOf(listOf(button("К списку", BookingAction.Listing(mode, page))), TelegramViews.toMenu)
        )

    private fun listText(mode: BookingList, page: ListPage<BookingCard>): String = buildString {
        appendLine(if (mode == BookingList.MINE) "Мои брони" else "Входящие заявки и встречи")
        appendLine("Часовой пояс: ${properties.timeZone} · страница ${page.number + 1}")
        if (page.items.isEmpty()) appendLine("На этой странице бронирований нет.")
        page.items.forEachIndexed { index, booking ->
            val partner = if (mode == BookingList.MINE) booking.ownerName else booking.bookerName
            appendLine("\n${index + 1}. ${interval(booking)}")
            appendLine("${partner.take(80).trim()} · ${status(booking)}")
        }
    }.trim()

    private fun cardText(booking: BookingCard): String = buildString {
        appendLine(interval(booking))
        appendLine("Часовой пояс: ${properties.timeZone}")
        appendLine("Владелец: ${booking.ownerName.take(120).trim()}")
        appendLine("Заявитель: ${booking.bookerName.take(120).trim()}")
        appendLine("Статус: ${status(booking)}")
        if (!future(booking)) appendLine("Время начала уже наступило.")
        appendLine("Место владельца: ${booking.place ?: "не указано"}")
        if (booking.proposedPlace != null) {
            appendLine("Предложенное место: ${booking.proposedPlace}")
            appendLine("Альтернативное место ещё не согласовано.")
        }
        appendLine("Комментарий: ${booking.comment ?: "не указан"}")
        append("Описание слота: ${booking.description ?: "не указано"}")
    }

    private fun decisions(
        actor: TelegramActor, booking: BookingCard, mode: BookingList, page: Int
    ): List<List<TelegramButton>> {
        fun action(label: String, decision: BookingDecision) =
            listOf(button(label, BookingAction.Decide(booking.id, decision, booking.status, mode, page)))
        val owner = booking.ownerId == actor.userId
        return when (booking.status) {
            BookingStatus.PENDING -> buildList {
                if (owner && future(booking)) add(action("Подтвердить", BookingDecision.CONFIRM))
                add(if (owner) action("Отклонить", BookingDecision.REJECT) else action("Отменить заявку", BookingDecision.CANCEL))
            }
            BookingStatus.CONFIRMED -> if (future(booking)) listOf(action("Отменить бронь", BookingDecision.CANCEL)) else emptyList()
            else -> emptyList()
        }
    }

    private fun status(booking: BookingCard): String = when (booking.status) {
        BookingStatus.PENDING -> if (future(booking)) "Ожидает подтверждения" else "Срок заявки истёк"
        BookingStatus.CONFIRMED -> "Подтверждена"
        BookingStatus.REJECTED -> "Отклонена"
        BookingStatus.CANCELED -> "Отменена"
    }

    private fun future(booking: BookingCard) = booking.startAt.isAfter(clock.instant())

    private fun interval(booking: BookingCard) =
        TelegramSlotTime.format(SlotInterval(booking.startAt, booking.endAt), properties.timeZone)
}
