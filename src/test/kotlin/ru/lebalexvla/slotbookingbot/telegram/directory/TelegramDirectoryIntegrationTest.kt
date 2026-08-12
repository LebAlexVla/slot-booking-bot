package ru.lebalexvla.slotbookingbot.telegram.directory

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.EntityType
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.MessageEntity
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User as TelegramUser
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.lebalexvla.slotbookingbot.booking.BookingRepository
import ru.lebalexvla.slotbookingbot.booking.BookingService
import ru.lebalexvla.slotbookingbot.booking.BookingStatus
import ru.lebalexvla.slotbookingbot.booking.BookSlotCommand
import ru.lebalexvla.slotbookingbot.category.CategoryRepository
import ru.lebalexvla.slotbookingbot.category.CategoryService
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.common.ListPage
import ru.lebalexvla.slotbookingbot.contact.ContactService
import ru.lebalexvla.slotbookingbot.slot.PublishSlotSetCommand
import ru.lebalexvla.slotbookingbot.slot.SlotInterval
import ru.lebalexvla.slotbookingbot.slot.SlotPublicationConfiguration
import ru.lebalexvla.slotbookingbot.slot.SlotPublicationPolicy
import ru.lebalexvla.slotbookingbot.slot.SlotRepository
import ru.lebalexvla.slotbookingbot.slot.SlotService
import ru.lebalexvla.slotbookingbot.slot.SlotSetTarget
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActorResolver
import ru.lebalexvla.slotbookingbot.telegram.slot.publication.TelegramPublicationDialog
import ru.lebalexvla.slotbookingbot.telegram.slot.TelegramSlotMenu
import ru.lebalexvla.slotbookingbot.telegram.TelegramEventHandler
import ru.lebalexvla.slotbookingbot.telegram.TelegramRouter
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage
import ru.lebalexvla.slotbookingbot.telegram.transport.RecordingTelegramGateway
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramUpdateConsumer
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramUpdateMapper
import ru.lebalexvla.slotbookingbot.user.User
import ru.lebalexvla.slotbookingbot.user.UserRepository
import ru.lebalexvla.slotbookingbot.user.UserService

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(ServiceConnectionAutoConfiguration::class)
@Import(ContactService::class, CategoryService::class, UserService::class, SlotService::class,
    BookingService::class, SlotPublicationConfiguration::class, SlotPublicationPolicy::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TelegramDirectoryIntegrationTest @Autowired constructor(
    private val contacts: ContactService,
    private val categories: CategoryService,
    private val users: UserRepository,
    private val userService: UserService,
    private val categoryRepository: CategoryRepository,
    private val slots: SlotService,
    private val slotRepository: SlotRepository,
    private val bookings: BookingService,
    private val bookingRepository: BookingRepository
) {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-bookworm")

        private val telegramIds = AtomicLong(100_000)
    }

    @Test
    fun `two users prepare contacts and a category entirely through Telegram updates`() {
        val bot = BotDriver()
        val alice = telegramIds.incrementAndGet()
        val bob = telegramIds.incrementAndGet()
        assertThat(bot.command(alice, "/start").text).contains("Добро пожаловать")
        bot.command(bob, "/start")
        val bobId = users.findByTelegramUserId(bob)!!.id!!
        val aliceId = users.findByTelegramUserId(alice)!!.id!!

        val invitation = bot.command(bob, "/invite").text
        val payload = Regex("start=(contact_[A-Za-z0-9_-]+)").find(invitation)!!.groupValues[1]
        val preview = bot.command(alice, "/start $payload")
        assertThat(contacts.getContacts(aliceId).items).isEmpty()
        val addContact = preview.keyboard.flatten().first().callbackData
        assertThat(bot.click(alice, addContact).text).contains("теперь в ваших контактах")
        bot.click(alice, addContact)
        assertThat(contacts.getContacts(aliceId).items.map { it.id }).containsExactly(bobId)
        assertThat(contacts.getContacts(bobId).items).isEmpty()

        val created = bot.command(alice, "/new_category Друзья")
        assertThat(created.text).contains("Категория создана", "Друзья")
        val categoryId = categories.getCategories(aliceId).items.single().id
        val candidates = bot.click(alice, DirectoryAction.Candidates(categoryId).encode())
        val addMember = candidates.keyboard.flatten().first().callbackData
        bot.click(alice, addMember)
        bot.click(alice, addMember)
        assertThat(categories.getMembers(aliceId, categoryId).items.map { it.id }).containsExactly(bobId)

        assertThat(bot.click(bob, DirectoryAction.Category(categoryId).encode()).text).contains("недоступна")
        assertThat(bot.click(bob, DirectoryAction.DeleteCategory(categoryId).encode()).text).contains("недоступна")
        bot.click(alice, DirectoryAction.RemoveMember(categoryId, bobId).encode())
        assertThat(categories.getMembers(aliceId, categoryId).items).isEmpty()

        val confirmation = bot.click(alice, DirectoryAction.ConfirmDeletion(categoryId).encode())
        assertThat(confirmation.text).contains("Удалить категорию", "Существующие заявки")
        assertThat(categories.getCategories(aliceId).items).hasSize(1)
        bot.click(alice, DirectoryAction.DeleteCategory(categoryId).encode())
        bot.click(alice, DirectoryAction.DeleteCategory(categoryId).encode())
        assertThat(categories.getCategories(aliceId).items).isEmpty()
        assertThat(bot.click(alice, addMember).text).contains("недоступна")
        assertThat(bot.gateway.answeredCallbacks).hasSize(12)
    }

    @Test
    fun `contact constraints reject self and unknown users`() {
        val owner = saveUser()
        expectError(BusinessError.SELF_CONTACT) { contacts.addContact(owner, owner) }
        expectError(BusinessError.USER_NOT_FOUND) { contacts.addContact(owner, UUID.randomUUID()) }
        expectError(BusinessError.USER_NOT_FOUND) { contacts.addContact(UUID.randomUUID(), owner) }
        assertThat(contacts.getContacts(owner).items).isEmpty()
    }

    @Test
    fun `contact lists are paginated isolated and usable outside transaction`() {
        val owner = saveUser()
        val expected = (0..ListPage.SIZE).map { saveUser("Контакт $it") }
        expected.forEach { contacts.addContact(owner, it) }
        val first = contacts.getContacts(owner)
        val last = contacts.getContacts(owner, 1)
        assertThat(first.items).hasSize(ListPage.SIZE)
        assertThat(first.hasNext).isTrue()
        assertThat(last.items).hasSize(1)
        assertThat(last.hasNext).isFalse()
        assertThat((first.items + last.items).map { it.id }).containsExactlyElementsOf(expected)
        assertThat(first.items.first().name).isEqualTo("Контакт 0")
        assertThat(contacts.getContacts(saveUser()).items).isEmpty()
    }

    @Test
    fun `simultaneous duplicate and reciprocal contacts are safe`() {
        val first = saveUser()
        val second = saveUser()
        val results = concurrent(
            { contacts.addContact(first, second) },
            { contacts.addContact(first, second) },
            { contacts.addContact(second, first) }
        )
        assertThat(results).allSatisfy { assertThat(it.isSuccess).isTrue() }
        assertThat(contacts.getContacts(first).items.map { it.id }).containsExactly(second)
        assertThat(contacts.getContacts(second).items.map { it.id }).containsExactly(first)
    }

    @Test
    fun `category names are normalized validated and scoped to owner`() {
        val owner = saveUser()
        val category = categories.createCategory(owner, "  Мои   друзья  ")
        assertThat(category.name).isEqualTo("Мои друзья")
        expectError(BusinessError.CATEGORY_NAME_TAKEN) { categories.createCategory(owner, "Мои друзья") }
        expectError(BusinessError.INVALID_CATEGORY_NAME) { categories.createCategory(owner, " \n ") }
        expectError(BusinessError.INVALID_CATEGORY_NAME) { categories.createCategory(owner, "я".repeat(65)) }
        val another = categories.createCategory(saveUser(), "Мои друзья")
        assertThat(another.id).isNotEqualTo(category.id)
        assertThat(categories.getCategories(owner).items).containsExactly(category)
    }

    @Test
    fun `concurrent category creation produces one category and a business rejection`() {
        val owner = saveUser()
        val results = concurrent(
            { categories.createCategory(owner, "Друзья") },
            { categories.createCategory(owner, "Друзья") }
        )
        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        val failure = results.single { it.isFailure }.exceptionOrNull()
        assertThat(failure).isInstanceOf(BusinessException::class.java)
        assertThat((failure as BusinessException).error).isEqualTo(BusinessError.CATEGORY_NAME_TAKEN)
        assertThat(categories.getCategories(owner).items).hasSize(1)
    }

    @Test
    fun `category list uses stable pagination and omits archived entries`() {
        val owner = saveUser()
        val expected = (0..ListPage.SIZE).map { categories.createCategory(owner, "Группа $it") }
        val first = categories.getCategories(owner)
        val next = categories.getCategories(owner, 1)
        assertThat(first.hasNext).isTrue()
        assertThat(next.hasNext).isFalse()
        assertThat(first.items + next.items).containsExactlyElementsOf(expected)
        categories.archiveCategory(owner, expected.first().id)
        assertThat(categories.getCategories(owner).items).doesNotContain(expected.first())
    }

    @Test
    fun `every category operation checks owner and hides foreign data`() {
        val owner = saveUser()
        val outsider = saveUser()
        val member = saveUser()
        contacts.addContact(owner, member)
        val category = categories.createCategory(owner, "Личная")
        categories.addMember(owner, category.id, member)

        listOf<() -> Any>(
            { categories.getCategory(outsider, category.id) },
            { categories.getMembers(outsider, category.id) },
            { categories.getCandidates(outsider, category.id) },
            { categories.addMember(outsider, category.id, outsider) },
            { categories.removeMember(outsider, category.id, member) },
            { categories.archiveCategory(outsider, category.id) }
        ).forEach { operation -> expectError(BusinessError.CATEGORY_NOT_FOUND) { operation() } }

        assertThat(categories.getMembers(owner, category.id).items.map { it.id }).containsExactly(member)
    }

    @Test
    fun `members must be contacts and candidates exclude existing members and other owners contacts`() {
        val owner = saveUser()
        val first = saveUser()
        val second = saveUser()
        val outsider = saveUser()
        contacts.addContact(owner, first)
        contacts.addContact(owner, second)
        val category = categories.createCategory(owner, "Друзья")
        expectError(BusinessError.CONTACT_REQUIRED) { categories.addMember(owner, category.id, outsider) }
        categories.addMember(owner, category.id, first)
        categories.addMember(owner, category.id, first)
        assertThat(categories.getCandidates(owner, category.id).items.map { it.id }).containsExactly(second)
        assertThat(categories.getMembers(owner, category.id).items.map { it.id }).containsExactly(first)

        categories.removeMember(owner, category.id, first)
        categories.removeMember(owner, category.id, first)
        assertThat(categories.getMembers(owner, category.id).items).isEmpty()
        assertThat(categories.getCandidates(owner, category.id).items.map { it.id }).containsExactly(first, second)
    }

    @Test
    fun `member and candidate pagination covers all contacts`() {
        val owner = saveUser()
        val category = categories.createCategory(owner, "Группа")
        val members = (0..ListPage.SIZE).map { saveUser() }
        members.forEach { contacts.addContact(owner, it) }
        assertThat(categories.getCandidates(owner, category.id).hasNext).isTrue()
        assertThat(categories.getCandidates(owner, category.id, 1).items).hasSize(1)
        members.forEach { categories.addMember(owner, category.id, it) }
        val first = categories.getMembers(owner, category.id)
        val next = categories.getMembers(owner, category.id, 1)
        assertThat(first.items + next.items).hasSize(members.size)
        assertThat((first.items + next.items).map { it.id }).containsExactlyElementsOf(members)
        assertThat(categories.getCandidates(owner, category.id).items).isEmpty()
    }

    @Test
    fun `concurrent membership additions stay unique`() {
        val owner = saveUser()
        val user = saveUser()
        contacts.addContact(owner, user)
        val category = categories.createCategory(owner, "Группа")
        val results = concurrent(
            { categories.addMember(owner, category.id, user) },
            { categories.addMember(owner, category.id, user) }
        )
        assertThat(results).allSatisfy { assertThat(it.isSuccess).isTrue() }
        assertThat(categories.getMembers(owner, category.id).items).hasSize(1)
    }

    @Test
    fun `archiving closes access but preserves pending and confirmed bookings and allows name reuse`() {
        val owner = saveUser()
        val user = saveUser()
        contacts.addContact(owner, user)
        val category = categories.createCategory(owner, "Группа")
        categories.addMember(owner, category.id, user)
        val setId = slots.publish(publication(owner, SlotSetTarget.CategoryTarget(category.id)))
        val published = slotRepository.findAllBySlotSetId(setId).sortedBy { it.startAt }
        val pending = bookings.book(BookSlotCommand(user, published[0].id!!))
        val confirmed = bookings.book(BookSlotCommand(user, published[1].id!!))
        bookings.confirm(confirmed, owner)
        assertThat(slots.getVisibleSlots(user).items.map { it.id }).contains(published[2].id)

        categories.archiveCategory(owner, category.id)
        categories.archiveCategory(owner, category.id)
        assertThat(slots.getVisibleSlots(user).items.map { it.id }).doesNotContain(published[2].id)
        assertThatThrownBy { bookings.book(BookSlotCommand(user, published[2].id!!)) }
            .isInstanceOf(IllegalStateException::class.java).hasMessage("Slot is not visible to user")
        assertThat(bookingRepository.findById(pending).orElseThrow().status).isEqualTo(BookingStatus.PENDING)
        assertThat(bookingRepository.findById(confirmed).orElseThrow().status).isEqualTo(BookingStatus.CONFIRMED)
        assertThat(categoryRepository.findById(category.id).orElseThrow().archivedAt).isNotNull()
        assertThat(categories.createCategory(owner, "Группа").id).isNotEqualTo(category.id)

        expectError(BusinessError.CATEGORY_NOT_FOUND) { categories.addMember(owner, category.id, user) }
        expectError(BusinessError.CATEGORY_NOT_FOUND) {
            slots.publish(publication(owner, SlotSetTarget.CategoryTarget(category.id)))
        }
        bookings.confirm(pending, owner)
        bookings.cancel(confirmed, user)
        assertThat(slots.getVisibleSlots(user).items).isEmpty()
    }

    @Test
    fun `removing member revokes category visibility without canceling bookings or direct access`() {
        val owner = saveUser()
        val user = saveUser()
        contacts.addContact(owner, user)
        val category = categories.createCategory(owner, "Группа")
        categories.addMember(owner, category.id, user)
        val categorySet = slots.publish(publication(owner, SlotSetTarget.CategoryTarget(category.id)))
        val direct = publication(owner, SlotSetTarget.UserTarget(user))
        val directSet = slots.publish(direct.copy(slots = direct.slots.map {
            SlotInterval(it.startAt.plusSeconds(86400), it.endAt.plusSeconds(86400))
        }))
        val categorySlot = slotRepository.findAllBySlotSetId(categorySet).first()
        val booking = bookings.book(BookSlotCommand(user, categorySlot.id!!))
        categories.removeMember(owner, category.id, user)
        assertThat(slots.getVisibleSlots(user).items.map { it.slotSetId }).containsOnly(directSet)
        assertThat(bookingRepository.findById(booking).orElseThrow().status).isEqualTo(BookingStatus.PENDING)
    }

    private fun publication(owner: UUID, target: SlotSetTarget) = PublishSlotSetCommand(
        ownerId = owner, target = target, place = "Кафе", description = "Встреча", maxBookingsPerUser = 3,
        slots = (0..2).map {
            val start = Instant.parse("2100-01-01T10:00:00Z").plusSeconds(it * 7200L)
            SlotInterval(start, start.plusSeconds(3600))
        }
    )

    private fun saveUser(name: String = "Пользователь"): UUID {
        val id = telegramIds.incrementAndGet()
        return users.saveAndFlush(User(telegramUserId = id, telegramChatId = id, firstName = name)).id!!
    }

    private fun expectError(error: BusinessError, action: () -> Unit) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }

    private fun concurrent(vararg actions: () -> Any): List<Result<Any>> {
        val executor = Executors.newFixedThreadPool(actions.size)
        val ready = CountDownLatch(actions.size)
        val start = CountDownLatch(1)
        return try {
            val futures = actions.map { action ->
                executor.submit<Result<Any>> {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    runCatching(action)
                }
            }
            check(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            futures.map { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private inner class BotDriver {
        val gateway = RecordingTelegramGateway()
        private val consumer = TelegramUpdateConsumer(
            TelegramUpdateMapper(),
            TelegramEventHandler(
                TelegramActorResolver(userService), gateway,
                TelegramRouter(
                    TelegramDirectoryHandler(TelegramContactMenu(contacts, gateway), TelegramCategoryMenu(categories)),
                    mock<TelegramSlotMenu>(), mock<TelegramPublicationDialog>()
                )
            )
        )

        fun command(telegramId: Long, text: String): OutgoingTelegramMessage = consume(Update().apply {
            updateId = 1
            message = message(telegramId).apply {
                this.text = text
                entities = listOf(MessageEntity(EntityType.BOTCOMMAND, 0, text.substringBefore(' ').length))
            }
        })

        fun click(telegramId: Long, data: String): OutgoingTelegramMessage = consume(Update().apply {
            updateId = 2
            callbackQuery = CallbackQuery().apply {
                id = UUID.randomUUID().toString()
                from = telegramUser(telegramId)
                message = message(telegramId).apply { from = telegramUser(999L) }
                this.data = data
            }
        })

        private fun consume(update: Update): OutgoingTelegramMessage {
            val before = gateway.sentMessages.size
            consumer.consume(update)
            assertThat(gateway.sentMessages).hasSize(before + 1)
            return gateway.sentMessages.last()
        }

        private fun message(telegramId: Long) = Message().apply {
            messageId = 1
            date = 1
            from = telegramUser(telegramId)
            chat = Chat(telegramId, "private")
        }

        private fun telegramUser(id: Long) = TelegramUser.builder()
            .id(id).isBot(false).firstName("Пользователь $id").build()
    }
}
