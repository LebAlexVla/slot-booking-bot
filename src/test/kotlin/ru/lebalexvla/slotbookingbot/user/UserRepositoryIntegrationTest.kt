package ru.lebalexvla.slotbookingbot.user

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


@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(ServiceConnectionAutoConfiguration::class)
class UserRepositoryIntegrationTest @Autowired constructor(
    private val userRepository: UserRepository
) {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-bookworm")
    }

    @Test
    fun `finds user by telegram user id`() {
        val user = User(
            telegramUserId = 123456789L,
            telegramChatId = 123456789L,
            firstName = "Test"
        )

        userRepository.saveAndFlush(user)

        val result =
            userRepository.findByTelegramUserId(123456789L)

        assertThat(result).isNotNull
        assertThat(result?.telegramUserId).isEqualTo(123456789L)
    }
}