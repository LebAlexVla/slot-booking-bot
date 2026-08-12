package ru.lebalexvla.slotbookingbot.user

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

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(ServiceConnectionAutoConfiguration::class)
@Import(UserService::class)
class UserPersistenceIntegrationTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val userService: UserService
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

        assertThat(user.id).isNotNull()
        assertThat(result).isNotNull
        assertThat(result?.telegramUserId).isEqualTo(123456789L)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `registers and updates user preserving persisted id`() {
        val createCommand = RegisterOrUpdateUserCommand(
            telegramUserId = 987654321L,
            telegramChatId = 100L,
            username = "old_username",
            firstName = "Old",
            lastName = "Name"
        )

        val createdId = userService.registerOrUpdate(createCommand)
        val createdUser = checkNotNull(
            userRepository.findByTelegramUserId(
                createCommand.telegramUserId
            )
        )

        assertThat(createdUser.id).isEqualTo(createdId)
        assertMatches(createdUser, createCommand)

        val updateCommand = RegisterOrUpdateUserCommand(
            telegramUserId = createCommand.telegramUserId,
            telegramChatId = 200L,
            username = "new_username",
            firstName = "New",
            lastName = null
        )

        val updatedId = userService.registerOrUpdate(updateCommand)
        val updatedUser = checkNotNull(
            userRepository.findByTelegramUserId(
                updateCommand.telegramUserId
            )
        )

        assertThat(updatedId).isEqualTo(createdId)
        assertThat(updatedUser.id).isEqualTo(createdId)
        assertMatches(updatedUser, updateCommand)
    }

    private fun assertMatches(
        user: User,
        command: RegisterOrUpdateUserCommand
    ) {
        assertThat(user.telegramUserId)
            .isEqualTo(command.telegramUserId)
        assertThat(user.telegramChatId)
            .isEqualTo(command.telegramChatId)
        assertThat(user.username).isEqualTo(command.username)
        assertThat(user.firstName).isEqualTo(command.firstName)
        assertThat(user.lastName).isEqualTo(command.lastName)
    }
}
