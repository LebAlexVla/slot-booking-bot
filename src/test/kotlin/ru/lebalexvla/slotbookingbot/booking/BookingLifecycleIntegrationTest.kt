package ru.lebalexvla.slotbookingbot.booking

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.lebalexvla.slotbookingbot.slot.Slot
import ru.lebalexvla.slotbookingbot.slot.SlotRepository
import ru.lebalexvla.slotbookingbot.slot.SlotSet
import ru.lebalexvla.slotbookingbot.slot.SlotSetRepository
import ru.lebalexvla.slotbookingbot.slot.SlotSetTargetType
import ru.lebalexvla.slotbookingbot.slot.SlotStatus
import ru.lebalexvla.slotbookingbot.user.User
import ru.lebalexvla.slotbookingbot.user.UserRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(
    ServiceConnectionAutoConfiguration::class
)
@Import(BookingService::class)
class BookingLifecycleIntegrationTest @Autowired constructor(
    private val bookingService: BookingService,
    private val bookingRepository: BookingRepository,
    private val slotRepository: SlotRepository,
    private val slotSetRepository: SlotSetRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager
) {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:18-bookworm")

        private val telegramIds =
            AtomicLong(10_000)

        private val FUTURE_START =
            Instant.parse("2100-01-01T10:00:00Z")

        private val FUTURE_END =
            Instant.parse("2100-01-01T11:00:00Z")
    }

    @Test
    fun `owner confirms pending booking`() {
        val context = createBooking()

        bookingService.confirm(
            bookingId = context.bookingId,
            ownerId = context.ownerId
        )

        flushAndClear()

        val booking = getBooking(context.bookingId)
        val slot = getSlot(context.slotId)

        assertThat(booking.status)
            .isEqualTo(BookingStatus.CONFIRMED)

        assertThat(booking.confirmedAt)
            .isNotNull()

        assertThat(slot.status)
            .isEqualTo(SlotStatus.BOOKED)
    }

    @Test
    fun `owner rejects pending booking`() {
        val context = createBooking()

        bookingService.reject(
            bookingId = context.bookingId,
            ownerId = context.ownerId
        )

        flushAndClear()

        val booking = getBooking(context.bookingId)
        val slot = getSlot(context.slotId)

        assertThat(booking.status)
            .isEqualTo(BookingStatus.REJECTED)

        assertThat(slot.status)
            .isEqualTo(SlotStatus.AVAILABLE)
    }

    @Test
    fun `booker cancels pending booking`() {
        val context = createBooking()

        bookingService.cancel(
            bookingId = context.bookingId,
            requesterId = context.userId
        )

        flushAndClear()

        val booking = getBooking(context.bookingId)
        val slot = getSlot(context.slotId)

        assertThat(booking.status)
            .isEqualTo(BookingStatus.CANCELED)

        assertThat(booking.canceledAt)
            .isNotNull()

        assertThat(slot.status)
            .isEqualTo(SlotStatus.AVAILABLE)
    }

    @Test
    fun `owner cancels confirmed booking`() {
        val context = createBooking()

        bookingService.confirm(
            bookingId = context.bookingId,
            ownerId = context.ownerId
        )

        bookingService.cancel(
            bookingId = context.bookingId,
            requesterId = context.ownerId
        )

        flushAndClear()

        val booking = getBooking(context.bookingId)
        val slot = getSlot(context.slotId)

        assertThat(booking.status)
            .isEqualTo(BookingStatus.CANCELED)

        assertThat(booking.confirmedAt)
            .isNotNull()

        assertThat(booking.canceledAt)
            .isNotNull()

        assertThat(slot.status)
            .isEqualTo(SlotStatus.AVAILABLE)
    }

    @Test
    fun `outsider cannot confirm booking`() {
        val context = createBooking()
        val outsider = saveUser()

        val result = runCatching {
            bookingService.confirm(
                bookingId = context.bookingId,
                ownerId = outsider.id!!
            )
        }

        assertThat(result.isFailure)
            .isTrue()

        flushAndClear()

        assertThat(
            getBooking(context.bookingId).status
        ).isEqualTo(BookingStatus.PENDING)

        assertThat(
            getSlot(context.slotId).status
        ).isEqualTo(SlotStatus.PENDING_CONFIRMATION)
    }

    @Test
    fun `outsider cannot cancel booking`() {
        val context = createBooking()
        val outsider = saveUser()

        val result = runCatching {
            bookingService.cancel(
                bookingId = context.bookingId,
                requesterId = outsider.id!!
            )
        }

        assertThat(result.isFailure)
            .isTrue()

        flushAndClear()

        assertThat(
            getBooking(context.bookingId).status
        ).isEqualTo(BookingStatus.PENDING)

        assertThat(
            getSlot(context.slotId).status
        ).isEqualTo(SlotStatus.PENDING_CONFIRMATION)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `confirm and reject cannot both succeed concurrently`() {
        val context = createBooking()

        val results = runConcurrently(
            {
                bookingService.confirm(
                    bookingId = context.bookingId,
                    ownerId = context.ownerId
                )
            },
            {
                bookingService.reject(
                    bookingId = context.bookingId,
                    ownerId = context.ownerId
                )
            }
        )

        assertThat(results.count { it.isSuccess })
            .isEqualTo(1)

        assertThat(results.count { it.isFailure })
            .isEqualTo(1)

        val booking = getBooking(context.bookingId)
        val slot = getSlot(context.slotId)

        when (booking.status) {
            BookingStatus.CONFIRMED ->
                assertThat(slot.status)
                    .isEqualTo(SlotStatus.BOOKED)

            BookingStatus.REJECTED ->
                assertThat(slot.status)
                    .isEqualTo(SlotStatus.AVAILABLE)

            else ->
                throw AssertionError(
                    "Unexpected booking status: ${booking.status}"
                )
        }
    }

    private fun createBooking(): BookingContext {
        val owner = saveUser()
        val user = saveUser()

        val slotSet = slotSetRepository.saveAndFlush(
            SlotSet(
                owner = owner,
                targetType = SlotSetTargetType.USER,
                targetUser = user,
                maxBookingsPerUser = 1
            )
        )

        val slot = slotRepository.saveAndFlush(
            Slot(
                slotSet = slotSet,
                startAt = FUTURE_START,
                endAt = FUTURE_END
            )
        )

        val bookingId = bookingService.book(
            BookSlotCommand(
                userId = user.id!!,
                slotId = slot.id!!
            )
        )

        return BookingContext(
            ownerId = owner.id!!,
            userId = user.id!!,
            slotId = slot.id!!,
            bookingId = bookingId
        )
    }

    private fun saveUser(): User {
        val telegramId = telegramIds.incrementAndGet()

        return userRepository.saveAndFlush(
            User(
                telegramUserId = telegramId,
                telegramChatId = telegramId,
                firstName = "User $telegramId"
            )
        )
    }

    private fun getBooking(
        bookingId: UUID
    ): Booking =
        bookingRepository.findById(
            bookingId
        ).orElseThrow()

    private fun getSlot(
        slotId: UUID
    ): Slot =
        slotRepository.findById(
            slotId
        ).orElseThrow()

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun runConcurrently(
        vararg tasks: () -> Unit
    ): List<Result<Unit>> {
        val executor =
            Executors.newFixedThreadPool(tasks.size)

        val start = CountDownLatch(1)

        return try {
            val futures = tasks.map { task ->
                executor.submit<Result<Unit>> {
                    start.await()
                    runCatching(task)
                }
            }

            start.countDown()

            futures.map {
                it.get(10, TimeUnit.SECONDS)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private data class BookingContext(
        val ownerId: UUID,
        val userId: UUID,
        val slotId: UUID,
        val bookingId: UUID
    )
}