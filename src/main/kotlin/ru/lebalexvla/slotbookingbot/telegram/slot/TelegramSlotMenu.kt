package ru.lebalexvla.slotbookingbot.telegram.slot

import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.common.ListPage
import ru.lebalexvla.slotbookingbot.slot.SlotCard
import ru.lebalexvla.slotbookingbot.slot.SlotInterval
import ru.lebalexvla.slotbookingbot.slot.SlotPublicationProperties
import ru.lebalexvla.slotbookingbot.slot.SlotService
import ru.lebalexvla.slotbookingbot.slot.SlotStatus
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.booking.BookingAction
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews.button
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage

@Component
class TelegramSlotMenu(
    private val slots: SlotService,
    private val properties: SlotPublicationProperties,
    private val clock: Clock
) {
    fun list(actor: TelegramActor, mode: SlotListMode, pageNumber: Int = 0): OutgoingTelegramMessage {
        val page = when (mode) {
            SlotListMode.AVAILABLE -> slots.getVisibleSlots(actor.userId, pageNumber)
            SlotListMode.OWN -> slots.getOwnedSlots(actor.userId, pageNumber)
        }
        val cards = page.items.mapIndexed { index, slot ->
            listOf(button("Подробнее · ${index + 1}", SlotAction.Card(mode, slot.id, page.number)))
        }
        return OutgoingTelegramMessage(
            actor.chatId, listText(mode, page),
            cards + TelegramViews.pagination(page) { SlotAction.Listing(mode, it) } +
                listOf(listOf(button("Создать набор", SlotAction.Start)), TelegramViews.toMenu)
        )
    }

    fun card(actor: TelegramActor, mode: SlotListMode, id: UUID, page: Int): OutgoingTelegramMessage {
        val slot = when (mode) {
            SlotListMode.AVAILABLE -> slots.getVisibleSlot(actor.userId, id)
            SlotListMode.OWN -> slots.getOwnedSlot(actor.userId, id)
        }
        val booking = if (mode == SlotListMode.AVAILABLE) {
            listOf(listOf(button("Подать заявку", BookingAction.Begin(slot.id))))
        } else emptyList()
        return OutgoingTelegramMessage(
            actor.chatId, cardText(mode, slot),
            booking + listOf(listOf(button("К списку слотов", SlotAction.Listing(mode, page))), TelegramViews.toMenu)
        )
    }

    private fun listText(mode: SlotListMode, page: ListPage<SlotCard>): String = buildString {
        appendLine(if (mode == SlotListMode.OWN) "Мои слоты" else "Доступные слоты")
        appendLine("Часовой пояс: ${properties.timeZone} · страница ${page.number + 1}")
        if (page.items.isEmpty()) appendLine("На этой странице слотов нет.")
        page.items.forEachIndexed { index, slot ->
            appendLine("\n${index + 1}. ${interval(slot)}")
            appendLine("Владелец: ${slot.ownerName.take(80)}")
            if (mode == SlotListMode.OWN) appendLine("Получатель: ${slot.targetName.take(80)}")
            appendLine("Место: ${slot.place?.take(80) ?: "не указано"}")
        }
    }.trim()

    private fun cardText(mode: SlotListMode, slot: SlotCard): String = buildString {
        appendLine(interval(slot))
        appendLine("Часовой пояс: ${properties.timeZone}")
        appendLine("Владелец: ${slot.ownerName.take(120)}")
        if (mode == SlotListMode.OWN) {
            appendLine("Получатель: ${slot.targetName.take(120)}")
            if (slot.targetArchived) appendLine("Категория удалена: новые бронирования закрыты.")
        }
        appendLine("Статус: ${status(slot.status)}")
        if (!slot.startAt.isAfter(clock.instant())) appendLine("Время начала уже наступило.")
        appendLine("Место: ${slot.place ?: "не указано"}")
        appendLine("Описание: ${slot.description ?: "не указано"}")
        append("Лимит бронирований на человека в наборе: ${slot.maxBookingsPerUser}")
    }

    private fun status(status: SlotStatus): String = when (status) {
        SlotStatus.AVAILABLE -> "Свободен"
        SlotStatus.PENDING_CONFIRMATION -> "Ожидает подтверждения"
        SlotStatus.BOOKED -> "Забронирован"
        SlotStatus.CANCELED -> "Отменён"
        SlotStatus.EXPIRED -> "Время прошло"
    }

    private fun interval(slot: SlotCard): String =
        TelegramSlotTime.format(SlotInterval(slot.startAt, slot.endAt), properties.timeZone)
}
