package ru.lebalexvla.slotbookingbot.slot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.lebalexvla.slotbookingbot.category.Category
import ru.lebalexvla.slotbookingbot.category.CategoryMember
import ru.lebalexvla.slotbookingbot.category.CategoryMemberRepository
import ru.lebalexvla.slotbookingbot.category.CategoryRepository
import ru.lebalexvla.slotbookingbot.user.User
import ru.lebalexvla.slotbookingbot.user.UserRepository
import java.time.Instant

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(
    ServiceConnectionAutoConfiguration::class
)
class SlotRepositoryIntegrationTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryMemberRepository: CategoryMemberRepository,
    private val slotSetRepository: SlotSetRepository,
    private val slotRepository: SlotRepository
) {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:18-bookworm")

        val NOW: Instant =
            Instant.parse("2029-01-01T00:00:00Z")

        val FUTURE_START: Instant =
            Instant.parse("2030-01-01T10:00:00Z")

        val FUTURE_END: Instant =
            Instant.parse("2030-01-01T11:00:00Z")

        val PAST_START: Instant =
            Instant.parse("2028-01-01T10:00:00Z")

        val PAST_END: Instant =
            Instant.parse("2028-01-01T11:00:00Z")
    }

    @Test
    fun `finds slot targeted directly to user`() {
        val owner = saveUser(100L, "Owner")
        val target = saveUser(200L, "Target")
        val outsider = saveUser(300L, "Outsider")

        val slotSet = saveUserTargetedSlotSet(
            owner = owner,
            target = target
        )

        val slot = saveSlot(
            slotSet = slotSet,
            startAt = FUTURE_START,
            endAt = FUTURE_END
        )

        val targetSlots = slotRepository.findVisibleSlots(
            userId = target.id!!,
            now = NOW
        )

        val outsiderSlots = slotRepository.findVisibleSlots(
            userId = outsider.id!!,
            now = NOW
        )

        assertThat(targetSlots.map { it.id })
            .containsExactly(slot.id)

        assertThat(outsiderSlots)
            .isEmpty()
    }

    @Test
    fun `finds slot targeted to category member`() {
        val owner = saveUser(100L, "Owner")
        val member = saveUser(200L, "Member")
        val outsider = saveUser(300L, "Outsider")

        val category = categoryRepository.saveAndFlush(
            Category(
                owner = owner,
                name = "University"
            )
        )

        categoryMemberRepository.saveAndFlush(
            CategoryMember(
                category = category,
                user = member
            )
        )

        val slotSet = slotSetRepository.saveAndFlush(
            SlotSet(
                owner = owner,
                targetType = SlotSetTargetType.CATEGORY,
                targetCategory = category,
                maxBookingsPerUser = 1
            )
        )

        val slot = saveSlot(
            slotSet = slotSet,
            startAt = FUTURE_START,
            endAt = FUTURE_END
        )

        val memberSlots = slotRepository.findVisibleSlots(
            userId = member.id!!,
            now = NOW
        )

        val outsiderSlots = slotRepository.findVisibleSlots(
            userId = outsider.id!!,
            now = NOW
        )

        assertThat(memberSlots.map { it.id })
            .containsExactly(slot.id)

        assertThat(outsiderSlots)
            .isEmpty()
    }

    @Test
    fun `does not return past or unavailable slots`() {
        val owner = saveUser(100L, "Owner")
        val target = saveUser(200L, "Target")

        val slotSet = saveUserTargetedSlotSet(
            owner = owner,
            target = target
        )

        saveSlot(
            slotSet = slotSet,
            startAt = PAST_START,
            endAt = PAST_END
        )

        saveSlot(
            slotSet = slotSet,
            startAt = FUTURE_START,
            endAt = FUTURE_END,
            status = SlotStatus.BOOKED
        )

        val visibleSlots = slotRepository.findVisibleSlots(
            userId = target.id!!,
            now = NOW
        )

        assertThat(visibleSlots)
            .isEmpty()
    }

    private fun saveUser(
        telegramId: Long,
        firstName: String
    ): User =
        userRepository.saveAndFlush(
            User(
                telegramUserId = telegramId,
                telegramChatId = telegramId,
                firstName = firstName
            )
        )

    private fun saveUserTargetedSlotSet(
        owner: User,
        target: User
    ): SlotSet =
        slotSetRepository.saveAndFlush(
            SlotSet(
                owner = owner,
                targetType = SlotSetTargetType.USER,
                targetUser = target,
                maxBookingsPerUser = 1
            )
        )

    private fun saveSlot(
        slotSet: SlotSet,
        startAt: Instant,
        endAt: Instant,
        status: SlotStatus = SlotStatus.AVAILABLE
    ): Slot =
        slotRepository.saveAndFlush(
            Slot(
                slotSet = slotSet,
                startAt = startAt,
                endAt = endAt,
                status = status
            )
        )
}