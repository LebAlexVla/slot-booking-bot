package ru.lebalexvla.slotbookingbot.slot

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.EntityType
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.MessageEntity
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User as TelegramUser
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.lebalexvla.slotbookingbot.booking.BookingService
import ru.lebalexvla.slotbookingbot.booking.BookSlotCommand
import ru.lebalexvla.slotbookingbot.category.CategoryService
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.common.ListPage
import ru.lebalexvla.slotbookingbot.contact.ContactService
import ru.lebalexvla.slotbookingbot.slot.draft.DraftInput
import ru.lebalexvla.slotbookingbot.slot.draft.PublicationDraft
import ru.lebalexvla.slotbookingbot.slot.draft.PublicationStep
import ru.lebalexvla.slotbookingbot.slot.draft.SlotDraftService
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActorResolver
import ru.lebalexvla.slotbookingbot.telegram.directory.TelegramCategoryMenu
import ru.lebalexvla.slotbookingbot.telegram.directory.TelegramContactMenu
import ru.lebalexvla.slotbookingbot.telegram.directory.TelegramDirectoryHandler
import ru.lebalexvla.slotbookingbot.telegram.slot.publication.TelegramPublicationDialog
import ru.lebalexvla.slotbookingbot.telegram.slot.publication.TelegramPublicationViews
import ru.lebalexvla.slotbookingbot.telegram.slot.publication.TelegramRecipientMenu
import ru.lebalexvla.slotbookingbot.telegram.slot.reference
import ru.lebalexvla.slotbookingbot.telegram.slot.SlotAction
import ru.lebalexvla.slotbookingbot.telegram.slot.SlotListMode
import ru.lebalexvla.slotbookingbot.telegram.slot.TelegramSlotMenu
import ru.lebalexvla.slotbookingbot.telegram.TelegramEventHandler
import ru.lebalexvla.slotbookingbot.telegram.TelegramRouter
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage
import ru.lebalexvla.slotbookingbot.telegram.transport.RecordingTelegramGateway
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramApiRequestException
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramUpdateConsumer
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramUpdateMapper
import ru.lebalexvla.slotbookingbot.user.User
import ru.lebalexvla.slotbookingbot.user.UserRepository
import ru.lebalexvla.slotbookingbot.user.UserService

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(ServiceConnectionAutoConfiguration::class)
@Import(
    ContactService::class, CategoryService::class, UserService::class, SlotService::class,
    BookingService::class, SlotDraftService::class, SlotPublicationPolicy::class,
    SlotPublicationConfiguration::class, SlotPublicationIntegrationTest.TimeConfiguration::class
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SlotPublicationIntegrationTest @Autowired constructor(
    private val contacts: ContactService,
    private val categories: CategoryService,
    private val users: UserRepository,
    private val userService: UserService,
    private val slots: SlotService,
    private val slotRepository: SlotRepository,
    private val slotSets: SlotSetRepository,
    private val drafts: SlotDraftService,
    private val bookings: BookingService,
    private val properties: SlotPublicationProperties,
    private val clock: MutableClock
) {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-bookworm")
        private val telegramIds = AtomicLong(200_000)
        private val updateIds = AtomicInteger(100_000)
        private val NOW = Instant.parse("2030-01-01T00:00:00Z")
    }

    class MutableClock : Clock() {
        var current: Instant = NOW
        override fun instant() = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    }

    @TestConfiguration
    class TimeConfiguration {
        @Bean
        @Primary
        fun publicationTestClock() = MutableClock()
    }

    @BeforeEach
    fun resetClock() { clock.current = NOW }

    @Test
    fun `publishes direct slots through Telegram and recovers a persisted draft with a new router`() {
        val owner = user("Автор")
        val recipient = user("Получатель")
        contacts.addContact(owner.id!!, recipient.id!!)
        val bot = BotDriver()
        val first = bot.command(owner, "/new_slots")
        assertThat(first.text).contains("Europe/Moscow")
        val recipients = bot.click(owner, first.action<SlotAction.Targets> { it.type == SlotSetTargetType.USER })
        val intervals = bot.click(owner, recipients.action<SlotAction.SelectTarget>())
        assertThat(intervals.text).contains("интервалов")
        val update = bot.textUpdate(owner, "2030-01-02 10:00-11:00\n2030-01-02 11:00-12:00")
        assertThat(bot.consume(update).text).contains("Введите место")
        assertThat(bot.consume(update).text).contains("Введите место")
        assertThat(drafts.current(owner.id!!)!!.place).isNull()
        bot.command(owner, "/menu")

        val restarted = BotDriver()
        assertThat(restarted.command(owner, "/draft").text).contains("Введите место")
        restarted.text(owner, "  Кафе  ")
        restarted.text(owner, "Обсудим проект")
        val review = restarted.text(owner, "2")
        assertThat(review.text).contains("Проверьте", "Кафе", "Обсудим проект", "02.01.2030 10:00")
        assertThat(slots.getOwnedSlots(owner.id!!).items).isEmpty()
        val confirm = review.action<SlotAction.Confirm>()
        assertThat(restarted.click(owner, confirm).text).contains("Набор опубликован")
        assertThat(restarted.click(owner, confirm).text).contains("Набор опубликован")
        assertThat(slots.getOwnedSlots(owner.id!!).items).hasSize(2)
        val visible = restarted.command(recipient, "/slots")
        assertThat(visible.text).contains("Автор", "Кафе", "Europe/Moscow")
        val card = restarted.click(recipient, visible.action<SlotAction.Card>())
        assertThat(card.text).contains("Обсудим проект", "Лимит бронирований на человека в наборе: 2")
        assertThat(restarted.gateway.answeredCallbacks).hasSize(3)
    }

    @Test
    fun `publishes to category with optional fields skipped and preserves draft after bad input`() {
        val owner = user()
        val member = user()
        contacts.addContact(owner.id!!, member.id!!)
        val category = categories.createCategory(owner.id!!, "Команда")
        categories.addMember(owner.id!!, category.id, member.id!!)
        val bot = BotDriver()
        val menu = bot.command(owner, "/new_slots")
        val recipients = bot.click(owner, menu.action<SlotAction.Targets> { it.type == SlotSetTargetType.CATEGORY })
        bot.click(owner, recipients.action<SlotAction.SelectTarget>())
        assertThat(bot.text(owner, "2030-02-30 10:00-11:00").text).contains("ГГГГ-ММ-ДД")
        assertThat(drafts.current(owner.id!!)!!.step).isEqualTo(PublicationStep.INTERVALS)
        bot.text(owner, "2030-01-02 10:00-11:00")
        val description = bot.command(owner, "/skip")
        bot.click(owner, description.action<SlotAction.Skip>())
        val revision = drafts.current(owner.id!!)!!.revision
        bot.text(owner, "0")
        bot.text(owner, "2")
        assertThat(drafts.current(owner.id!!)!!.revision).isEqualTo(revision)
        val review = bot.text(owner, "1")
        bot.click(owner, review.action<SlotAction.Confirm>())
        val card = slots.getVisibleSlots(member.id!!).items.single()
        assertThat(card.place).isNull()
        assertThat(card.description).isNull()
        assertThat(slots.getOwnedSlots(owner.id!!).items.single().targetName).isEqualTo("Команда")
    }

    @Test
    fun `failed Telegram delivery preserves accepted text and published set for retry`() {
        val owner = user()
        val recipient = user()
        contacts.addContact(owner.id!!, recipient.id!!)
        val start = drafts.start(owner.id!!, null)
        drafts.selectTarget(owner.id!!, start.id, start.revision, null, SlotSetTarget.UserTarget(recipient.id!!))
        val bot = BotDriver()
        val input = bot.textUpdate(owner, "2030-01-02 10:00-11:00")
        bot.gateway.deliveryFailure = TelegramApiRequestException(TelegramApiException("Unavailable"))
        bot.deliver(input)
        assertThat(drafts.current(owner.id!!)!!.step).isEqualTo(PublicationStep.PLACE)
        bot.gateway.deliveryFailure = null
        assertThat(bot.consume(input).text).contains("Введите место")
        bot.command(owner, "/skip")
        bot.command(owner, "/skip")
        val preview = bot.text(owner, "1")
        val confirm = bot.callbackUpdate(owner, preview.action<SlotAction.Confirm>())
        bot.gateway.deliveryFailure = TelegramApiRequestException(TelegramApiException("Unavailable"))
        bot.deliver(confirm)
        val publishedId = drafts.current(owner.id!!)!!.publishedSetId
        assertThat(publishedId).isNotNull()
        bot.gateway.deliveryFailure = null
        assertThat(bot.consume(confirm).text).contains("Набор опубликован")
        assertThat(drafts.current(owner.id!!)!!.publishedSetId).isEqualTo(publishedId)
        assertThat(slots.getOwnedSlots(owner.id!!).items).hasSize(1)
    }

    @Test
    fun `cancel starts a fresh session and old buttons cannot mutate it`() {
        val (owner, target) = pair()
        val first = drafts.start(owner, 10)
        val selected = drafts.selectTarget(owner, first.id, first.revision, 11, target)
        drafts.cancel(owner, selected.id, selected.revision, 12)
        assertThat(drafts.start(owner, 10).step).isEqualTo(PublicationStep.CANCELED)
        val next = drafts.start(owner, 13)
        assertThat(next.id).isNotEqualTo(first.id)
        assertThat(next.target).isNull()
        expect(BusinessError.STALE_DRAFT) { drafts.selectTarget(owner, first.id, 0, 14, target) }
        assertThat(drafts.current(owner)).isEqualTo(next)
    }

    @Test
    fun `active start is idempotent and stale revisions and duplicate inputs do not advance dialogue`() {
        val (owner, target) = pair()
        val first = drafts.start(owner, 1)
        val selected = drafts.selectTarget(owner, first.id, 0, 2, target)
        assertThat(drafts.start(owner, 3)).isEqualTo(selected)
        val place = drafts.accept(owner, selected.id, selected.revision, 4, DraftInput.Intervals(intervals()))
        assertThat(drafts.accept(owner, selected.id, selected.revision, 4, DraftInput.Intervals(intervals()))).isEqualTo(place)
        expect(BusinessError.STALE_DRAFT) {
            drafts.accept(owner, selected.id, selected.revision, 5, DraftInput.Place("stale"))
        }
        assertThat(drafts.current(owner)).isEqualTo(place)
    }

    @Test
    fun `expiry boundary rejects changes and restart clears old fields`() {
        val (owner, target) = pair()
        val review = review(owner, target)
        clock.current = review.expiresAt
        expect(BusinessError.DRAFT_EXPIRED) { drafts.confirm(owner, review.id, review.revision, null) }
        assertThat(slots.getOwnedSlots(owner).items).isEmpty()
        val fresh = drafts.start(owner, null)
        assertThat(fresh.id).isNotEqualTo(review.id)
        assertThat(fresh.step).isEqualTo(PublicationStep.RECIPIENT)
        assertThat(fresh.intervals).isEmpty()
        assertThat(fresh.place).isNull()
    }

    @Test
    fun `confirmation rechecks time and edited intervals allow recovery`() {
        val (owner, target) = pair()
        val review = review(owner, target, intervals(1, 1))
        clock.current = review.intervals.first().startAt
        expect(BusinessError.SLOT_IN_PAST) { drafts.confirm(owner, review.id, review.revision, null) }
        assertThat(drafts.current(owner)!!.step).isEqualTo(PublicationStep.REVIEW)
        val editing = drafts.editIntervals(owner, review.id, review.revision, null)
        val place = accept(owner, editing, DraftInput.Intervals(intervals(2, 2)))
        val description = accept(owner, place, DraftInput.Place(null))
        val limit = accept(owner, description, DraftInput.Description(null))
        val revised = accept(owner, limit, DraftInput.Limit(2))
        val done = drafts.confirm(owner, revised.id, revised.revision, null)
        assertThat(slotRepository.findAllBySlotSetId(done.publishedSetId!!)).hasSize(2)
    }

    @Test
    fun `recipient must be an owned active category or an existing contact`() {
        val owner = user().id!!
        val outsider = user().id!!
        val category = categories.createCategory(outsider, "Чужая")
        val draft = drafts.start(owner, null)
        expect(BusinessError.CONTACT_REQUIRED) {
            drafts.selectTarget(owner, draft.id, 0, null, SlotSetTarget.UserTarget(outsider))
        }
        expect(BusinessError.CATEGORY_NOT_FOUND) {
            drafts.selectTarget(owner, draft.id, 0, null, SlotSetTarget.CategoryTarget(category.id))
        }
        expect(BusinessError.CONTACT_REQUIRED) {
            slots.publish(command(owner, SlotSetTarget.UserTarget(owner)))
        }
        expect(BusinessError.CATEGORY_NOT_FOUND) {
            slots.publish(command(owner, SlotSetTarget.CategoryTarget(category.id)))
        }
        assertThat(drafts.current(owner)).isEqualTo(draft)
    }

    @Test
    fun `another actor cannot read or confirm a guessed draft reference`() {
        val (owner, target) = pair()
        val review = review(owner, target)
        val outsider = user().id!!
        expect(BusinessError.DRAFT_NOT_FOUND) { drafts.confirm(outsider, review.id, review.revision, null) }
        val own = drafts.start(outsider, null)
        expect(BusinessError.STALE_DRAFT) { drafts.inspect(outsider, review.id, review.revision) }
        expect(BusinessError.STALE_DRAFT) { drafts.confirm(outsider, review.id, review.revision, null) }
        assertThat(drafts.current(outsider)).isEqualTo(own)
        assertThat(drafts.current(owner)).isEqualTo(review)
    }

    @Test
    fun `archiving selected category rolls back confirmation and retains editable draft`() {
        val owner = user().id!!
        val category = categories.createCategory(owner, "Группа")
        val review = review(owner, SlotSetTarget.CategoryTarget(category.id))
        val setCount = slotSets.count()
        categories.archiveCategory(owner, category.id)
        expect(BusinessError.CATEGORY_NOT_FOUND) { drafts.confirm(owner, review.id, review.revision, null) }
        assertThat(slotSets.count()).isEqualTo(setCount)
        assertThat(drafts.current(owner)).isEqualTo(review)
    }

    @Test
    fun `overlapping publication rolls back whole set and duplicate draft confirmation creates once`() {
        val (owner, target) = pair()
        val review = review(owner, target)
        val results = concurrently(
            { drafts.confirm(owner, review.id, review.revision, 10) },
            { drafts.confirm(owner, review.id, review.revision, 11) }
        )
        assertThat(results).allMatch { it.isSuccess }
        assertThat(results.map { (it.getOrThrow() as PublicationDraft).publishedSetId }.distinct()).hasSize(1)
        assertThat(slots.getOwnedSlots(owner).items).hasSize(2)
        val count = slotSets.count()
        expect(BusinessError.SLOT_OVERLAP) {
            slots.publish(command(owner, target).copy(slots = intervals(1, 48) + intervals()))
        }
        assertThat(slotSets.count()).isEqualTo(count)
    }

    @Test
    fun `concurrent overlapping publications from same owner allow only one set`() {
        val (owner, target) = pair()
        val request = command(owner, target)
        val results = concurrently({ slots.publish(request) }, { slots.publish(request) })
        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat((results.single { it.isFailure }.exceptionOrNull() as BusinessException).error)
            .isEqualTo(BusinessError.SLOT_OVERLAP)
        assertThat(slots.getOwnedSlots(owner).items).hasSize(2)
    }

    @Test
    fun `reciprocal publishers may use the same time without deadlocking foreign keys`() {
        val alice = user().id!!
        val bob = user().id!!
        contacts.addContact(alice, bob)
        contacts.addContact(bob, alice)
        val results = concurrently(
            { slots.publish(command(alice, SlotSetTarget.UserTarget(bob))) },
            { slots.publish(command(bob, SlotSetTarget.UserTarget(alice))) }
        )
        assertThat(results).allMatch { it.isSuccess }
        assertThat(slots.getOwnedSlots(alice).items).hasSize(2)
        assertThat(slots.getOwnedSlots(bob).items).hasSize(2)
    }

    @Test
    fun `cross-set adjacency is allowed while canceled slots do not reserve time`() {
        val (owner, target) = pair()
        val first = slots.publish(command(owner, target).copy(slots = intervals(1, 24)))
        slots.publish(command(owner, target).copy(slots = intervals(1, 25)))
        val slot = slotRepository.findAllBySlotSetId(first).single()
        slot.status = SlotStatus.CANCELED
        slotRepository.saveAndFlush(slot)
        slots.publish(command(owner, target).copy(slots = intervals(1, 24)))
        assertThat(slots.getOwnedSlots(owner).items).hasSize(3)
    }

    @Test
    fun `archived free offers stop reserving time but pending and confirmed meetings still block`() {
        val (owner, direct) = pair()
        val member = (direct as SlotSetTarget.UserTarget).userId
        val category = categories.createCategory(owner, "Группа")
        categories.addMember(owner, category.id, member)
        val set = slots.publish(command(owner, SlotSetTarget.CategoryTarget(category.id)).copy(slots = intervals(3), maxBookingsPerUser = 3))
        val published = slotRepository.findAllBySlotSetId(set).sortedBy { it.startAt }
        bookings.book(BookSlotCommand(member, published[0].id!!))
        val confirmed = bookings.book(BookSlotCommand(member, published[1].id!!))
        bookings.confirm(confirmed, owner)
        categories.archiveCategory(owner, category.id)
        for (slot in published.take(2)) {
            expect(BusinessError.SLOT_OVERLAP) {
                slots.publish(command(owner, direct).copy(slots = listOf(SlotInterval(slot.startAt, slot.endAt))))
            }
        }
        slots.publish(command(owner, direct).copy(slots = listOf(SlotInterval(published[2].startAt, published[2].endAt))))
        assertThat(slots.getOwnedSlot(owner, published[2].id!!).targetArchived).isTrue()
    }

    @Test
    fun `slot pages are ordered bounded and cards enforce access on every request`() {
        val (owner, target) = pair()
        val recipient = (target as SlotSetTarget.UserTarget).userId
        val outsider = user().id!!
        slots.publish(command(owner, target).copy(slots = intervals(10)))
        val page = slots.getVisibleSlots(recipient)
        val next = slots.getVisibleSlots(recipient, 1)
        assertThat(page.items).hasSize(ListPage.SIZE)
        assertThat(page.hasNext).isTrue()
        assertThat(next.items).hasSize(2)
        assertThat(next.hasNext).isFalse()
        assertThat((page.items + next.items).map { it.startAt }).isSorted()
        val first = page.items.first()
        val bot = BotDriver()
        val ownerUser = users.findById(owner).orElseThrow()
        val owned = bot.command(ownerUser, "/my_slots")
        val secondPage = bot.click(ownerUser, owned.action<SlotAction.Listing> { it.page == 1 })
        assertThat(secondPage.text).contains("страница 2")
        assertThat(bot.click(ownerUser, secondPage.action<SlotAction.Card>()).text).contains("Статус: Свободен", "Получатель:")
        val unauthorized = bot.click(users.findById(outsider).orElseThrow(), SlotAction.Card(SlotListMode.AVAILABLE, first.id))
        assertThat(unauthorized.text).contains("Слот недоступен").doesNotContain("Встреча", "Кафе")
        assertThat(slots.getVisibleSlot(recipient, first.id).ownerName).isNotBlank()
        expect(BusinessError.SLOT_NOT_AVAILABLE) { slots.getVisibleSlot(outsider, first.id) }
        expect(BusinessError.SLOT_NOT_AVAILABLE) { slots.getOwnedSlot(recipient, first.id) }
        clock.current = first.startAt
        expect(BusinessError.SLOT_NOT_AVAILABLE) { slots.getVisibleSlot(recipient, first.id) }
        assertThat(slots.getVisibleSlots(recipient).items.map { it.id }).doesNotContain(first.id)
        assertThat(slots.getOwnedSlot(owner, first.id).id).isEqualTo(first.id)
        assertThatThrownBy { slots.getOwnedSlots(owner, -1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `category card access is revoked immediately after member removal and archive`() {
        val (owner, direct) = pair()
        val member = (direct as SlotSetTarget.UserTarget).userId
        val category = categories.createCategory(owner, "Команда")
        categories.addMember(owner, category.id, member)
        val set = slots.publish(command(owner, SlotSetTarget.CategoryTarget(category.id)))
        val id = slotRepository.findAllBySlotSetId(set).first().id!!
        assertThat(slots.getVisibleSlot(member, id).id).isEqualTo(id)
        categories.removeMember(owner, category.id, member)
        expect(BusinessError.SLOT_NOT_AVAILABLE) { slots.getVisibleSlot(member, id) }
        categories.addMember(owner, category.id, member)
        categories.archiveCategory(owner, category.id)
        expect(BusinessError.SLOT_NOT_AVAILABLE) { slots.getVisibleSlot(member, id) }
        assertThat(slots.getOwnedSlot(owner, id).targetArchived).isTrue()
    }

    @Test
    fun `largest preview stays within Telegram message budget`() {
        val owner = user()
        val target = user("я".repeat(120))
        contacts.addContact(owner.id!!, target.id!!)
        val draft = review(owner.id!!, SlotSetTarget.UserTarget(target.id!!), intervals(20),
            place = "я".repeat(200), description = "я".repeat(1000))
        val message = TelegramPublicationViews(clock).prompt(TelegramActor(owner.id!!, owner.telegramChatId), draft)
        assertThat(message.text.length).isLessThanOrEqualTo(4096)
        assertThat(message.text).contains("я".repeat(1000))
    }

    private fun pair(): Pair<UUID, SlotSetTarget> {
        val owner = user().id!!
        val recipient = user().id!!
        contacts.addContact(owner, recipient)
        return owner to SlotSetTarget.UserTarget(recipient)
    }

    private fun user(name: String = "Пользователь"): User {
        val id = telegramIds.incrementAndGet()
        return users.saveAndFlush(User(telegramUserId = id, telegramChatId = id, firstName = name))
    }

    private fun intervals(count: Int = 2, startHour: Int = 24) = (0 until count).map {
        val start = NOW.plus(Duration.ofHours(startHour + it.toLong()))
        SlotInterval(start, start.plusSeconds(3600))
    }

    private fun command(owner: UUID, target: SlotSetTarget) = PublishSlotSetCommand(
        owner, target, "Кафе", "Встреча", 1, intervals()
    )

    private fun review(
        owner: UUID, target: SlotSetTarget, times: List<SlotInterval> = intervals(),
        place: String? = "Кафе", description: String? = "Встреча"
    ): PublicationDraft {
        val start = drafts.start(owner, null)
        val selected = drafts.selectTarget(owner, start.id, start.revision, null, target)
        val location = accept(owner, selected, DraftInput.Intervals(times))
        val text = accept(owner, location, DraftInput.Place(place))
        val limit = accept(owner, text, DraftInput.Description(description))
        return accept(owner, limit, DraftInput.Limit(times.size))
    }

    private fun accept(owner: UUID, draft: PublicationDraft, input: DraftInput) =
        drafts.accept(owner, draft.id, draft.revision, null, input)

    private fun expect(error: BusinessError, action: () -> Unit) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }

    private fun concurrently(vararg actions: () -> Any): List<Result<Any>> {
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
            futures.map { it.get(20, TimeUnit.SECONDS) }
        } finally { executor.shutdownNow() }
    }

    private inline fun <reified T : SlotAction> OutgoingTelegramMessage.action(
        predicate: (T) -> Boolean = { true }
    ): SlotAction = keyboard.flatten().mapNotNull { SlotAction.parse(it.callbackData) }.filterIsInstance<T>().first(predicate)

    private inner class BotDriver {
        val gateway = RecordingTelegramGateway()
        private val consumer = TelegramUpdateConsumer(
            TelegramUpdateMapper(),
            TelegramEventHandler(
                TelegramActorResolver(userService), gateway,
                TelegramRouter(
                    TelegramDirectoryHandler(TelegramContactMenu(contacts, gateway), TelegramCategoryMenu(categories)),
                    TelegramSlotMenu(slots, properties, clock),
                    TelegramPublicationDialog(
                        drafts, TelegramRecipientMenu(drafts, contacts, categories), TelegramPublicationViews(clock)
                    )
                )
            )
        )

        fun command(user: User, text: String) = consume(textUpdate(user, text).apply {
            message.entities = listOf(MessageEntity(EntityType.BOTCOMMAND, 0, text.substringBefore(' ').length))
        })

        fun text(user: User, text: String) = consume(textUpdate(user, text))

        fun textUpdate(user: User, text: String) = Update().apply {
            updateId = updateIds.incrementAndGet()
            message = message(user).apply { this.text = text }
        }

        fun click(user: User, action: SlotAction) = consume(callbackUpdate(user, action))

        fun callbackUpdate(user: User, action: SlotAction) = Update().apply {
            updateId = updateIds.incrementAndGet()
            callbackQuery = CallbackQuery().apply {
                id = UUID.randomUUID().toString()
                from = telegramUser(user)
                message = message(user)
                data = action.encode()
            }
        }

        fun deliver(update: Update) = consumer.consume(update)

        fun consume(update: Update): OutgoingTelegramMessage {
            val before = gateway.sentMessages.size
            deliver(update)
            assertThat(gateway.sentMessages).hasSize(before + 1)
            return gateway.sentMessages.last()
        }

        private fun message(user: User) = Message().apply {
            messageId = 1
            date = 1
            from = telegramUser(user)
            chat = Chat(user.telegramChatId, "private")
        }

        private fun telegramUser(user: User) = TelegramUser.builder()
            .id(user.telegramUserId).isBot(false).firstName(checkNotNull(user.firstName)).build()
    }
}
