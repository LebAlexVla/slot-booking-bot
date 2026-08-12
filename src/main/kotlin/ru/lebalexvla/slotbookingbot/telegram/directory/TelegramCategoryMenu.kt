package ru.lebalexvla.slotbookingbot.telegram.directory

import java.util.UUID
import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.category.CategoryService
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews.button
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage

@Component
class TelegramCategoryMenu(private val categoryService: CategoryService) {
    fun newCategory(actor: TelegramActor) = TelegramViews.menu(
        actor.chatId,
        "Отправьте название командой, например:\n/new_category Друзья\n\nНазвание — от 1 до 64 символов."
    )

    fun create(actor: TelegramActor, name: String): OutgoingTelegramMessage {
        val category = categoryService.createCategory(actor.userId, name)
        return show(actor, category.id, notice = "Категория создана.")
    }

    fun list(actor: TelegramActor, pageNumber: Int = 0, notice: String? = null): OutgoingTelegramMessage {
        val page = categoryService.getCategories(actor.userId, pageNumber)
        val title = if (page.items.isEmpty()) "На этой странице категорий нет." else "Выберите категорию:"
        return OutgoingTelegramMessage(
            actor.chatId,
            listOfNotNull(notice, "$title\nСтраница ${page.number + 1}").joinToString("\n\n"),
            page.items.map { listOf(button(it.name, DirectoryAction.Category(it.id))) } +
                TelegramViews.pagination(page) { DirectoryAction.Categories(it) } +
                listOf(listOf(button("Создать категорию", DirectoryAction.NewCategory)), TelegramViews.toMenu)
        )
    }

    fun show(
        actor: TelegramActor, categoryId: UUID, pageNumber: Int = 0, notice: String? = null
    ): OutgoingTelegramMessage {
        val category = categoryService.getCategory(actor.userId, categoryId)
        val page = categoryService.getMembers(actor.userId, categoryId, pageNumber)
        val text = buildString {
            if (notice != null) appendLine("$notice\n")
            appendLine("Категория «${category.name}» · страница ${page.number + 1}")
            if (page.items.isEmpty()) appendLine("На этой странице участников нет.")
            else {
                page.items.forEach { appendLine("• ${TelegramViews.label(it)}") }
                appendLine("\nКнопки «Убрать» исключают участника из категории.")
            }
        }.trim()
        return OutgoingTelegramMessage(
            actor.chatId, text,
            page.items.map {
                listOf(button("Убрать: ${TelegramViews.label(it).take(40)}", DirectoryAction.RemoveMember(categoryId, it.id)))
            } +
                TelegramViews.pagination(page) { DirectoryAction.Category(categoryId, it) } +
                listOf(
                    listOf(button("Добавить участника", DirectoryAction.Candidates(categoryId))),
                    listOf(button("Удалить категорию", DirectoryAction.ConfirmDeletion(categoryId))),
                    listOf(button("К категориям", DirectoryAction.Categories()))
                )
        )
    }

    fun candidates(actor: TelegramActor, categoryId: UUID, pageNumber: Int = 0): OutgoingTelegramMessage {
        val category = categoryService.getCategory(actor.userId, categoryId)
        val page = categoryService.getCandidates(actor.userId, categoryId, pageNumber)
        val text = if (page.items.isEmpty()) {
            "Нет контактов для добавления на этой странице. Добавьте новый контакт или вернитесь к категории."
        } else {
            "Выберите контакт для категории «${category.name}»:"
        }
        return OutgoingTelegramMessage(
            actor.chatId, "$text\nСтраница ${page.number + 1}",
            page.items.map {
                listOf(button(TelegramViews.label(it).take(60), DirectoryAction.AddMember(categoryId, it.id)))
            } +
                TelegramViews.pagination(page) { DirectoryAction.Candidates(categoryId, it) } +
                listOf(listOf(button("К категории", DirectoryAction.Category(categoryId))))
        )
    }

    fun addMember(actor: TelegramActor, categoryId: UUID, userId: UUID): OutgoingTelegramMessage {
        categoryService.addMember(actor.userId, categoryId, userId)
        return show(actor, categoryId, notice = "Участник добавлен.")
    }

    fun removeMember(actor: TelegramActor, categoryId: UUID, userId: UUID): OutgoingTelegramMessage {
        categoryService.removeMember(actor.userId, categoryId, userId)
        return show(actor, categoryId, notice = "Участник исключён. Его существующие брони сохранены.")
    }

    fun confirmDeletion(actor: TelegramActor, categoryId: UUID): OutgoingTelegramMessage {
        val category = categoryService.getCategory(actor.userId, categoryId)
        return OutgoingTelegramMessage(
            actor.chatId,
            "Удалить категорию «${category.name}»?\n\nЕё слоты перестанут быть доступны для новых бронирований. " +
                "Существующие заявки и подтверждённые встречи сохранятся.",
            listOf(
                listOf(button("Да, удалить", DirectoryAction.DeleteCategory(categoryId))),
                listOf(button("Отмена", DirectoryAction.Category(categoryId)))
            )
        )
    }

    fun delete(actor: TelegramActor, categoryId: UUID): OutgoingTelegramMessage {
        categoryService.archiveCategory(actor.userId, categoryId)
        return list(actor, notice = "Категория удалена. Существующие брони сохранены.")
    }
}
