package ru.lebalexvla.slotbookingbot.telegram.common

import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.ListPage
import ru.lebalexvla.slotbookingbot.telegram.directory.DirectoryAction
import ru.lebalexvla.slotbookingbot.telegram.slot.SlotAction
import ru.lebalexvla.slotbookingbot.telegram.slot.SlotListMode
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramButton
import ru.lebalexvla.slotbookingbot.user.UserSummary

object TelegramViews {
    fun button(text: String, action: TelegramAction) = TelegramButton(text, action.encode())

    fun menu(chatId: Long, text: String = "Выберите действие:") = OutgoingTelegramMessage(
        chatId, text, listOf(
            listOf(button("Создать слоты", SlotAction.Start), button("Черновик", SlotAction.Resume)),
            listOf(button("Доступные слоты", SlotAction.Listing(SlotListMode.AVAILABLE)), button("Мои слоты", SlotAction.Listing(SlotListMode.OWN))),
            listOf(button("Контакты", DirectoryAction.Contacts()), button("Категории", DirectoryAction.Categories())),
            listOf(button("Моя ссылка для контактов", DirectoryAction.Invite)),
            listOf(button("Помощь", DirectoryAction.Help))
        )
    )

    fun help(chatId: Long) = menu(
        chatId,
        """
            Здесь можно подготовить контакты и категории, опубликовать время для встреч и посмотреть доступные слоты.

            Чтобы добавить человека, попросите его открыть /invite и прислать свою ссылку. Перейдите по ней и нажмите «Добавить в контакты». Контакты у каждого свои; обратное добавление выполняется отдельно.

            /contacts — мои контакты
            /categories — мои категории
            /new_category Друзья — создать категорию
            /invite — моя ссылка
            /new_slots — создать набор слотов или продолжить текущий
            /draft — вернуться к сохранённому черновику
            /slots — доступные мне слоты
            /my_slots — мои опубликованные слоты
            /skip — пропустить место или описание при создании
            /menu — главное меню
            /cancel — отменить создание набора

            Участниками категории могут быть только ваши контакты. Удаление категории закрывает доступ к её слотам, сохраняя существующие брони.
        """.trimIndent()
    )

    fun error(chatId: Long, error: BusinessError) = menu(chatId, errorText(error))

    fun errorText(error: BusinessError): String = when (error) {
        BusinessError.USER_NOT_FOUND -> "Пользователь не найден. Попросите его открыть бота и прислать свою ссылку."
        BusinessError.SELF_CONTACT -> "Себя нельзя добавить в контакты. Передайте свою ссылку другому человеку."
        BusinessError.CATEGORY_NOT_FOUND -> "Категория недоступна: она удалена или принадлежит другому пользователю."
        BusinessError.INVALID_CATEGORY_NAME -> "Название должно содержать от 1 до 64 символов. Пример: /new_category Друзья"
        BusinessError.CATEGORY_NAME_TAKEN -> "Категория с таким названием уже есть. Откройте список или выберите другое название."
        BusinessError.CONTACT_REQUIRED -> "Сначала добавьте этого человека в свои контакты."
        BusinessError.INVALID_SLOT_INTERVALS -> "Нужны от 1 до 20 интервалов. Конец каждого должен быть позже начала."
        BusinessError.INVALID_SLOT_FORMAT -> "Формат интервала: ГГГГ-ММ-ДД ЧЧ:ММ-ЧЧ:ММ, например 2030-01-15 10:00-11:00."
        BusinessError.INVALID_LOCAL_TIME -> "Это местное время не существует или неоднозначно из-за перевода часов. Выберите другое время."
        BusinessError.SLOT_IN_PAST -> "Время начала должно быть в будущем. Исправьте интервалы."
        BusinessError.SLOT_OVERLAP -> "Интервалы пересекаются между собой или с вашими существующими слотами. Выберите другое время."
        BusinessError.INVALID_SLOT_LIMIT -> "Лимит должен быть целым числом от 1 до количества слотов в наборе."
        BusinessError.INVALID_SLOT_TEXT -> "Место — до 200 символов, описание — до 1000. Уберите недопустимые символы."
        BusinessError.SLOT_NOT_AVAILABLE -> "Слот недоступен: он занят, устарел или у вас нет доступа. Обновите список."
        BusinessError.DRAFT_NOT_FOUND -> "Черновика нет. Начните создание: /new_slots."
        BusinessError.DRAFT_EXPIRED -> "Срок черновика истёк. Начните заново: /new_slots."
        BusinessError.STALE_DRAFT -> "Действие устарело. Продолжите с текущего шага через /draft."
    }

    fun label(user: UserSummary): String =
        user.name.replace(Regex("\\s+"), " ").take(80) +
            (user.username?.let { " (@${it.take(32)})" } ?: "")

    fun pagination(page: ListPage<*>, action: (Int) -> TelegramAction): List<List<TelegramButton>> {
        val buttons = buildList {
            if (page.number > 0) add(button("Назад", action(page.number - 1)))
            if (page.hasNext && page.number < ListPage.MAX_NUMBER) add(button("Далее", action(page.number + 1)))
        }
        return if (buttons.isEmpty()) emptyList() else listOf(buttons)
    }

    val toMenu get() = listOf(button("В меню", DirectoryAction.Menu))
}
