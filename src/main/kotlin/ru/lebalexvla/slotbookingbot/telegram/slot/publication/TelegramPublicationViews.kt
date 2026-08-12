package ru.lebalexvla.slotbookingbot.telegram.slot.publication

import java.time.Clock
import java.time.ZoneId
import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.slot.draft.PublicationDraft
import ru.lebalexvla.slotbookingbot.slot.draft.PublicationStep
import ru.lebalexvla.slotbookingbot.slot.SlotPublicationPolicy
import ru.lebalexvla.slotbookingbot.slot.SlotSetTargetType
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews.button
import ru.lebalexvla.slotbookingbot.telegram.slot.reference
import ru.lebalexvla.slotbookingbot.telegram.slot.SlotAction
import ru.lebalexvla.slotbookingbot.telegram.slot.TelegramSlotTime
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramButton

@Component
class TelegramPublicationViews(private val clock: Clock) {
    fun prompt(actor: TelegramActor, draft: PublicationDraft, notice: String? = null): OutgoingTelegramMessage {
        terminalMessage(draft)?.let { return TelegramViews.menu(actor.chatId, it) }
        val text = listOfNotNull(
            notice,
            "Часовой пояс: ${draft.timeZone}",
            instruction(draft),
            "Черновик сохранён до ${TelegramSlotTime.format(draft.expiresAt, draft.timeZone)}. Продолжить: /draft."
        ).joinToString("\n\n")
        return OutgoingTelegramMessage(actor.chatId, text, keyboard(draft))
    }

    private fun terminalMessage(draft: PublicationDraft): String? = when {
        draft.step == PublicationStep.PUBLISHED -> "Набор опубликован. Посмотреть слоты можно в /my_slots."
        draft.step == PublicationStep.CANCELED -> "Создание набора отменено. Новый набор: /new_slots."
        !draft.expiresAt.isAfter(clock.instant()) -> "Срок черновика истёк. Начните заново: /new_slots."
        else -> null
    }

    private fun instruction(draft: PublicationDraft): String = when (draft.step) {
        PublicationStep.RECIPIENT -> "Кому доступны слоты? Выберите контакт или категорию."
        PublicationStep.INTERVALS -> intervalInstruction(draft.timeZone)
        PublicationStep.PLACE -> "Введите место встречи (до 200 символов) или нажмите «Пропустить»."
        PublicationStep.DESCRIPTION -> "Введите описание (до 1000 символов) или нажмите «Пропустить»."
        PublicationStep.LIMIT ->
            "Сколько слотов из набора может занять один человек? Введите число от 1 до ${draft.intervals.size}."
        PublicationStep.REVIEW -> preview(draft)
        PublicationStep.PUBLISHED, PublicationStep.CANCELED -> error("Terminal draft has no input step")
    }

    private fun intervalInstruction(timeZone: String): String {
        val exampleDate = clock.instant().atZone(ZoneId.of(timeZone)).toLocalDate().plusDays(1)
        return """
            Введите от 1 до ${SlotPublicationPolicy.MAX_INTERVALS} интервалов, каждый с новой строки:
            $exampleDate 10:00-11:00
            $exampleDate 11:00-12:00

            Интервал должен закончиться в тот же день. Начало — в будущем; пересечения с вашими другими слотами не допускаются.
        """.trimIndent()
    }

    private fun preview(draft: PublicationDraft): String = buildString {
        appendLine("Проверьте набор перед публикацией.")
        appendLine("Получатель: ${draft.targetLabel?.take(120)}")
        draft.intervals.forEach { appendLine("• ${TelegramSlotTime.format(it, draft.timeZone)}") }
        appendLine("Место: ${draft.place ?: "не указано"}")
        appendLine("Описание: ${draft.description ?: "не указано"}")
        append("Лимит на человека: ${draft.maxBookingsPerUser}")
    }

    private fun keyboard(draft: PublicationDraft): List<List<TelegramButton>> {
        val ref = draft.reference()
        val actions = when (draft.step) {
            PublicationStep.RECIPIENT -> listOf(
                listOf(button("Контакт", SlotAction.Targets(ref, SlotSetTargetType.USER))),
                listOf(button("Категория", SlotAction.Targets(ref, SlotSetTargetType.CATEGORY)))
            )
            PublicationStep.PLACE, PublicationStep.DESCRIPTION -> listOf(listOf(button("Пропустить", SlotAction.Skip(ref))))
            PublicationStep.REVIEW -> listOf(
                listOf(button("Опубликовать", SlotAction.Confirm(ref))),
                listOf(button("Изменить интервалы", SlotAction.EditIntervals(ref)))
            )
            else -> emptyList()
        }
        return actions + listOf(
            listOf(button("Отменить создание", SlotAction.Cancel(ref))),
            TelegramViews.toMenu
        )
    }
}
