package ru.lebalexvla.slotbookingbot.telegram.directory

import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.telegram.common.CompactId
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramEvent
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage

@Component
class TelegramDirectoryHandler(
    private val contacts: TelegramContactMenu,
    private val categories: TelegramCategoryMenu
) {
    fun handle(actor: TelegramActor, event: TelegramEvent): OutgoingTelegramMessage = when (event) {
        is TelegramEvent.Command -> command(actor, event)
        is TelegramEvent.Callback -> callback(actor, DirectoryAction.parse(event.data))
        is TelegramEvent.Text -> TelegramViews.menu(
            actor.chatId, "Выберите действие в меню. Для создания категории отправьте /new_category Название."
        )
    }

    private fun command(actor: TelegramActor, command: TelegramEvent.Command): OutgoingTelegramMessage =
        when (command.name) {
            "start" -> {
                val argument = command.arguments
                if (argument == null) TelegramViews.menu(actor.chatId, "Добро пожаловать! Выберите действие:")
                else {
                    val id = argument.takeIf { it.startsWith("contact_") }
                        ?.removePrefix("contact_")?.let(CompactId::decode)
                    if (id == null) TelegramViews.menu(actor.chatId, "Ссылка недействительна. Попросите новую ссылку из /invite.")
                    else contacts.preview(actor, id)
                }
            }
            "menu", "cancel" -> TelegramViews.menu(actor.chatId)
            "help" -> TelegramViews.help(actor.chatId)
            "invite" -> contacts.invite(actor)
            "contacts" -> contacts.list(actor)
            "categories" -> categories.list(actor)
            "new_category" -> command.arguments?.let { categories.create(actor, it) } ?: categories.newCategory(actor)
            else -> TelegramViews.menu(actor.chatId, "Неизвестная команда. Доступные действия — в меню и /help.")
        }

    private fun callback(actor: TelegramActor, action: DirectoryAction?): OutgoingTelegramMessage = when (action) {
        DirectoryAction.Menu -> TelegramViews.menu(actor.chatId)
        DirectoryAction.Help -> TelegramViews.help(actor.chatId)
        DirectoryAction.Invite -> contacts.invite(actor)
        DirectoryAction.NewCategory -> categories.newCategory(actor)
        is DirectoryAction.Contacts -> contacts.list(actor, action.page)
        is DirectoryAction.Categories -> categories.list(actor, action.page)
        is DirectoryAction.AddContact -> contacts.add(actor, action.userId)
        is DirectoryAction.Category -> categories.show(actor, action.id, action.page)
        is DirectoryAction.Candidates -> categories.candidates(actor, action.id, action.page)
        is DirectoryAction.AddMember -> categories.addMember(actor, action.categoryId, action.userId)
        is DirectoryAction.RemoveMember -> categories.removeMember(actor, action.categoryId, action.userId)
        is DirectoryAction.ConfirmDeletion -> categories.confirmDeletion(actor, action.id)
        is DirectoryAction.DeleteCategory -> categories.delete(actor, action.id)
        null -> TelegramViews.menu(actor.chatId, "Кнопка устарела или недействительна. Откройте нужное действие заново.")
    }
}
