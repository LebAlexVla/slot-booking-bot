package ru.lebalexvla.slotbookingbot.category

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
import ru.lebalexvla.slotbookingbot.user.User
import ru.lebalexvla.slotbookingbot.user.UserRepository

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(ServiceConnectionAutoConfiguration::class)
class CategoryRepositoryIntegrationTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryMemberRepository: CategoryMemberRepository
) {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-bookworm")
    }

    @Test
    fun `saves category member`() {
        val owner = userRepository.saveAndFlush(
            User(
                telegramUserId = 100L,
                telegramChatId = 100L,
                firstName = "Owner"
            )
        )

        val member = userRepository.saveAndFlush(
            User(
                telegramUserId = 200L,
                telegramChatId = 200L,
                firstName = "Member"
            )
        )

        val category = categoryRepository.saveAndFlush(
            Category(
                owner = owner,
                name = "University"
            )
        )

        val categoryMember = categoryMemberRepository.saveAndFlush(
            CategoryMember(
                category = category,
                user = member
            )
        )

        assertThat(categoryMember.id).isNotNull()

        assertThat(
            categoryMemberRepository.existsByCategoryIdAndUserId(
                category.id!!,
                member.id!!
            )
        ).isTrue()
    }
}