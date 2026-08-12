package ru.lebalexvla.slotbookingbot.telegram.slot.publication

import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.category.CategoryService
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.common.ListPage
import ru.lebalexvla.slotbookingbot.contact.ContactService
import ru.lebalexvla.slotbookingbot.slot.draft.PublicationStep
import ru.lebalexvla.slotbookingbot.slot.draft.SlotDraftService
import ru.lebalexvla.slotbookingbot.slot.SlotSetTargetType
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews.button
import ru.lebalexvla.slotbookingbot.telegram.slot.SlotAction
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage

@Component
class TelegramRecipientMenu(
    private val drafts: SlotDraftService,
    private val contacts: ContactService,
    private val categories: CategoryService
) {
    fun list(actor: TelegramActor, action: SlotAction.Targets): OutgoingTelegramMessage {
        val draft = drafts.inspect(actor.userId, action.draft.id, action.draft.revision)
        if (draft.step != PublicationStep.RECIPIENT) throw BusinessException(BusinessError.STALE_DRAFT)
        val page = recipients(actor, action)
        val text = if (page.items.isEmpty()) {
            "На этой странице получателей нет. Добавьте контакт или категорию через меню, затем вернитесь в /draft."
        } else {
            "Выберите получателя · страница ${page.number + 1}:"
        }
        val choices = page.items.map { (id, name) ->
            listOf(button(name.take(60), SlotAction.SelectTarget(action.draft, action.type, id)))
        }
        return OutgoingTelegramMessage(
            actor.chatId, text,
            choices + TelegramViews.pagination(page) { SlotAction.Targets(action.draft, action.type, it) } +
                listOf(listOf(button("К черновику", SlotAction.Resume)), TelegramViews.toMenu)
        )
    }

    private fun recipients(actor: TelegramActor, action: SlotAction.Targets) = when (action.type) {
        SlotSetTargetType.USER -> contacts.getContacts(actor.userId, action.page).let { page ->
            ListPage(page.items.map { it.id to TelegramViews.label(it) }, page.number, page.hasNext)
        }
        SlotSetTargetType.CATEGORY -> categories.getCategories(actor.userId, action.page).let { page ->
            ListPage(page.items.map { it.id to it.name }, page.number, page.hasNext)
        }
    }
}
