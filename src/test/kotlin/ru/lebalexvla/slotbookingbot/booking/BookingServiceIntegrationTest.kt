package ru.lebalexvla.slotbookingbot.booking

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
import ru.lebalexvla.slotbookingbot.category.Category
import ru.lebalexvla.slotbookingbot.category.CategoryMember
import ru.lebalexvla.slotbookingbot.category.CategoryMemberRepository
import ru.lebalexvla.slotbookingbot.category.CategoryRepository
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

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(
    ServiceConnectionAutoConfiguration::class
)
@Import(BookingService::class)
class BookingServiceIntegrationTest @Autowired constructor(
    private val bookingService: BookingService,
    private val bookingRepository: BookingRepository,
    private val slotRepository: SlotRepository,
    private val slotSetRepository: SlotSetRepository,
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryMemberRepository: CategoryMemberRepository
) {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:18-bookworm")

        private val FUTURE_START =
            Instant.parse("2100-01-01T10:00:00Z")

        private val FUTURE_END =
            Instant.parse("2100-01-01T11:00:00Z")
    }

    @Test
    fun `books available visible slot`() {
        val owner = saveUser(100L)
        val user = saveUser(200L)

        val slotSet = saveUserTargetedSlotSet(
            owner = owner,
            target = user
        )

        val slot = saveSlot(slotSet)

        val bookingId = book(
            user = user,
            slot = slot,
            proposedPlace = " Cafe ",
            comment = " Hello "
        )

        val booking =
            bookingRepository.findById(bookingId).orElseThrow()

        assertThat(booking.status)
            .isEqualTo(BookingStatus.PENDING)

        assertThat(booking.proposedPlace)
            .isEqualTo("Cafe")

        assertThat(booking.comment)
            .isEqualTo("Hello")

        assertThat(slot.status)
            .isEqualTo(SlotStatus.PENDING_CONFIRMATION)
    }

    @Test
    fun `rejects booking when user cannot see slot`() {
        val owner = saveUser(300L)
        val target = saveUser(400L)
        val outsider = saveUser(500L)

        val slotSet = saveUserTargetedSlotSet(
            owner = owner,
            target = target
        )

        val slot = saveSlot(slotSet)

        val result = runCatching {
            book(
                user = outsider,
                slot = slot
            )
        }

        assertThat(result.isFailure)
            .isTrue()

        assertThat(
            bookingRepository.findAllBySlotId(slot.id!!)
        ).isEmpty()
    }

    @Test
    fun `rejects booking when booking limit is reached`() {
        val owner = saveUser(600L)
        val user = saveUser(700L)

        val slotSet = saveUserTargetedSlotSet(
            owner = owner,
            target = user,
            maxBookingsPerUser = 1
        )

        val firstSlot = saveSlot(slotSet)
        val secondSlot = saveSlot(slotSet)

        book(
            user = user,
            slot = firstSlot
        )

        val result = runCatching {
            book(
                user = user,
                slot = secondSlot
            )
        }

        assertThat(result.isFailure)
            .isTrue()

        assertThat(
            bookingRepository.findAllByBookedByAndSlotSlotSet(
                bookedBy = user,
                slotSet = slotSet
            )
        ).hasSize(1)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `only one concurrent booking succeeds for same slot`() {
        val owner = saveUser(800L)
        val firstUser = saveUser(900L)
        val secondUser = saveUser(1000L)

        val slotSet = saveCategoryTargetedSlotSet(
            owner = owner,
            members = listOf(firstUser, secondUser)
        )

        val slot = saveSlot(slotSet)

        val results = runConcurrently(
            { book(firstUser, slot) },
            { book(secondUser, slot) }
        )

        assertOneSuccessAndOneFailure(results)

        assertThat(
            bookingRepository.findAllBySlotId(slot.id!!)
        ).hasSize(1)

        val persistedSlot =
            slotRepository.findById(slot.id!!).orElseThrow()

        assertThat(persistedSlot.status)
            .isEqualTo(SlotStatus.PENDING_CONFIRMATION)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `booking limit survives concurrent requests`() {
        val owner = saveUser(1100L)
        val user = saveUser(1200L)

        val slotSet = saveUserTargetedSlotSet(
            owner = owner,
            target = user,
            maxBookingsPerUser = 1
        )

        val firstSlot = saveSlot(slotSet)
        val secondSlot = saveSlot(slotSet)

        val results = runConcurrently(
            { book(user, firstSlot) },
            { book(user, secondSlot) }
        )

        assertOneSuccessAndOneFailure(results)

        assertThat(
            bookingRepository.findAllByBookedByAndSlotSlotSet(
                bookedBy = user,
                slotSet = slotSet
            )
        ).hasSize(1)
    }

    private fun book(
        user: User,
        slot: Slot,
        proposedPlace: String? = null,
        comment: String? = null
    ): UUID =
        bookingService.book(
            BookSlotCommand(
                userId = user.id!!,
                slotId = slot.id!!,
                proposedPlace = proposedPlace,
                comment = comment
            )
        )

    private fun saveUser(
        telegramId: Long
    ): User =
        userRepository.saveAndFlush(
            User(
                telegramUserId = telegramId,
                telegramChatId = telegramId,
                firstName = "User $telegramId"
            )
        )

    private fun saveUserTargetedSlotSet(
        owner: User,
        target: User,
        maxBookingsPerUser: Int = 1
    ): SlotSet =
        slotSetRepository.saveAndFlush(
            SlotSet(
                owner = owner,
                targetType = SlotSetTargetType.USER,
                targetUser = target,
                maxBookingsPerUser = maxBookingsPerUser
            )
        )

    private fun saveCategoryTargetedSlotSet(
        owner: User,
        members: List<User>,
        maxBookingsPerUser: Int = 1
    ): SlotSet {
        val category = categoryRepository.saveAndFlush(
            Category(
                owner = owner,
                name = "Category ${UUID.randomUUID()}"
            )
        )

        members.forEach { member ->
            categoryMemberRepository.saveAndFlush(
                CategoryMember(
                    category = category,
                    user = member
                )
            )
        }

        return slotSetRepository.saveAndFlush(
            SlotSet(
                owner = owner,
                targetType = SlotSetTargetType.CATEGORY,
                targetCategory = category,
                maxBookingsPerUser = maxBookingsPerUser
            )
        )
    }

    private fun saveSlot(
        slotSet: SlotSet
    ): Slot =
        slotRepository.saveAndFlush(
            Slot(
                slotSet = slotSet,
                startAt = FUTURE_START,
                endAt = FUTURE_END
            )
        )

    private fun runConcurrently(
        vararg tasks: () -> UUID
    ): List<Result<UUID>> {
        val executor =
            Executors.newFixedThreadPool(tasks.size)

        val start = CountDownLatch(1)

        return try {
            val futures = tasks.map { task ->
                executor.submit<Result<UUID>> {
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

    private fun assertOneSuccessAndOneFailure(
        results: List<Result<UUID>>
    ) {
        assertThat(results.count { it.isSuccess })
            .isEqualTo(1)

        assertThat(results.count { it.isFailure })
            .isEqualTo(1)
    }
}