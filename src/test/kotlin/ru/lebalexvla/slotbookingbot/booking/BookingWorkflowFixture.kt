package ru.lebalexvla.slotbookingbot.booking

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.lebalexvla.slotbookingbot.booking.request.BookingRequest
import ru.lebalexvla.slotbookingbot.booking.request.BookingRequestService
import ru.lebalexvla.slotbookingbot.category.CategoryService
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.contact.ContactService
import ru.lebalexvla.slotbookingbot.slot.*
import ru.lebalexvla.slotbookingbot.slot.draft.SlotDraftService
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramDraftCoordinator
import ru.lebalexvla.slotbookingbot.telegram.transport.RecordingTelegramGateway
import ru.lebalexvla.slotbookingbot.user.User
import ru.lebalexvla.slotbookingbot.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Testcontainers
@SpringBootTest(properties = ["telegram.bot.enabled=false", "telegram.bot.token=test-token"])
@Import(BookingWorkflowFixture.Configuration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class BookingWorkflowFixture {
    companion object {
        @Container @ServiceConnection @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-bookworm")
        val NOW: Instant = Instant.parse("2030-01-01T00:00:00Z")
        private val telegramIds = AtomicLong(900_000)
    }

    @Autowired protected lateinit var bookings: BookingService
    @Autowired protected lateinit var queries: BookingQueries
    @Autowired protected lateinit var requests: BookingRequestService
    @Autowired protected lateinit var publications: SlotDraftService
    @Autowired protected lateinit var coordinator: TelegramDraftCoordinator
    @Autowired protected lateinit var slots: SlotService
    @Autowired protected lateinit var slotRepository: SlotRepository
    @Autowired protected lateinit var bookingRepository: BookingRepository
    @Autowired protected lateinit var users: UserRepository
    @Autowired protected lateinit var contacts: ContactService
    @Autowired protected lateinit var categories: CategoryService
    @Autowired protected lateinit var clock: TestClock
    @Autowired protected lateinit var gateway: RecordingTelegramGateway

    class TestClock : Clock() {
        var now = NOW
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = fixed(now, zone)
    }

    @TestConfiguration
    class Configuration {
        @Bean @Primary fun bookingTestClock() = TestClock()
        @Bean @Primary fun recordingTelegramGateway() = RecordingTelegramGateway()
    }

    @BeforeEach
    fun reset() {
        clock.now = NOW
        gateway.sentMessages.clear()
        gateway.answeredCallbacks.clear()
        gateway.deliveryFailure = null
        gateway.callbackFailure = null
    }

    protected data class Offer(val owner: User, val booker: User, val ids: List<UUID>, val categoryId: UUID?)

    protected fun offer(count: Int = 1, limit: Int = 1, group: Boolean = false): Offer {
        val owner = user("Владелец")
        val booker = user("Участник")
        contacts.addContact(owner.id!!, booker.id!!)
        val category = if (group) categories.createCategory(owner.id!!, "Команда").also {
            categories.addMember(owner.id!!, it.id, booker.id!!)
        } else null
        val target = category?.let { SlotSetTarget.CategoryTarget(it.id) } ?: SlotSetTarget.UserTarget(booker.id!!)
        val set = slots.publish(PublishSlotSetCommand(
            owner.id!!, target, "Кафе", "Обсудим проект", limit,
            (0 until count).map {
                val start = NOW.plusSeconds(172800 + it * 3600L)
                SlotInterval(start, start.plusSeconds(3600))
            }
        ))
        return Offer(owner, booker, slotRepository.findAllBySlotSetId(set).sortedBy { it.startAt }.map { it.id!! }, category?.id)
    }

    protected fun user(name: String = "Пользователь"): User {
        val id = telegramIds.incrementAndGet()
        return users.saveAndFlush(User(telegramUserId = id, telegramChatId = id, firstName = name))
    }

    protected fun ready(offer: Offer, index: Int = 0): BookingRequest {
        var draft = requests.start(offer.booker.id!!, offer.ids[index], null)
        draft = requests.accept(offer.booker.id!!, draft.id, draft.revision, null, "Комментарий")
        return requests.accept(offer.booker.id!!, draft.id, draft.revision, null, "Библиотека")
    }

    protected fun submit(offer: Offer, draft: BookingRequest): BookingRequest =
        requests.submit(offer.booker.id!!, draft.id, draft.revision, null)

    protected fun expect(error: BusinessError, action: () -> Unit) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }

    protected fun concurrent(vararg actions: () -> Any): List<Result<Any>> {
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
}
