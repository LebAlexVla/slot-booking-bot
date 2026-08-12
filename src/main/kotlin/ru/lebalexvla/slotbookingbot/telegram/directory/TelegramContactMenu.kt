package ru.lebalexvla.slotbookingbot.telegram.directory

import java.util.UUID
import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.contact.ContactService
import ru.lebalexvla.slotbookingbot.telegram.common.CompactId
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews.button
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramGateway

@Component
class TelegramContactMenu(
    private val contactService: ContactService,
    private val gateway: TelegramGateway
) {
    fun invite(actor: TelegramActor): OutgoingTelegramMessage {
        val link = "https://t.me/${gateway.getBotUsername()}?start=contact_${CompactId.encode(actor.userId)}"
        return TelegramViews.menu(
            actor.chatId,
            "Отправьте эту ссылку человеку, который хочет добавить вас в контакты:\n$link\n\n" +
                "Чтобы добавить его, попросите его собственную ссылку из /invite."
        )
    }

    fun preview(actor: TelegramActor, userId: UUID): OutgoingTelegramMessage {
        val user = contactService.getCandidate(actor.userId, userId)
        return OutgoingTelegramMessage(
            actor.chatId,
            "Добавить в ваши контакты: ${TelegramViews.label(user)}?",
            listOf(listOf(button("Добавить в контакты", DirectoryAction.AddContact(user.id))), TelegramViews.toMenu)
        )
    }

    fun add(actor: TelegramActor, userId: UUID): OutgoingTelegramMessage {
        val user = contactService.addContact(actor.userId, userId)
        return list(actor, notice = "${TelegramViews.label(user)} теперь в ваших контактах.")
    }

    fun list(actor: TelegramActor, pageNumber: Int = 0, notice: String? = null): OutgoingTelegramMessage {
        val page = contactService.getContacts(actor.userId, pageNumber)
        val text = buildString {
            if (notice != null) appendLine("$notice\n")
            appendLine("Контакты · страница ${page.number + 1}")
            if (page.items.isEmpty()) append("На этой странице контактов нет. Попросите у человека ссылку из /invite.")
            else page.items.forEach { appendLine("• ${TelegramViews.label(it)}") }
        }.trim()
        return OutgoingTelegramMessage(
            actor.chatId, text,
            TelegramViews.pagination(page) { DirectoryAction.Contacts(it) } +
                listOf(
                    listOf(button("Моя ссылка для контактов", DirectoryAction.Invite)),
                    TelegramViews.toMenu
                )
        )
    }
}
