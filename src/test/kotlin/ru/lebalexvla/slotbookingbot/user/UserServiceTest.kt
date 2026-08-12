package ru.lebalexvla.slotbookingbot.user

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class UserServiceTest {

    private val userRepository: UserRepository = mock()
    private val userService = UserService(userRepository)

    @Test
    fun `creates user when user does not exist`() {
        val command = command()
        val userId = UUID.randomUUID()

        whenever(
            userRepository.findByTelegramUserId(command.telegramUserId)
        ).thenReturn(null)
        whenever(userRepository.save(any<User>())).thenAnswer { invocation ->
            invocation.getArgument<User>(0).apply {
                id = userId
            }
        }

        val result = userService.registerOrUpdate(command)

        argumentCaptor<User>().apply {
            verify(userRepository).save(capture())
            assertMatches(firstValue, command)
        }
        assertThat(result).isEqualTo(userId)
    }

    @Test
    fun `updates existing user`() {
        val userId = UUID.randomUUID()
        val user = User(
            id = userId,
            telegramUserId = 123L,
            telegramChatId = 100L,
            username = "old_username",
            firstName = "Old",
            lastName = "Name"
        )

        val command = command()

        whenever(
            userRepository.findByTelegramUserId(command.telegramUserId)
        ).thenReturn(user)

        val result = userService.registerOrUpdate(command)

        assertMatches(user, command)
        assertThat(result).isEqualTo(userId)

        verify(userRepository, never()).save(any<User>())
    }

    @Test
    fun `fails when saved user has no generated id`() {
        val command = command()

        whenever(
            userRepository.findByTelegramUserId(command.telegramUserId)
        ).thenReturn(null)
        whenever(userRepository.save(any<User>())).thenAnswer { invocation ->
            invocation.getArgument<User>(0)
        }

        assertThatIllegalStateException()
            .isThrownBy { userService.registerOrUpdate(command) }
            .withMessage("Saved user must have an id")
    }

    @Test
    fun `fails when existing user has no id`() {
        val command = command()
        val user = User(
            telegramUserId = command.telegramUserId,
            telegramChatId = 100L,
            firstName = "Old"
        )

        whenever(
            userRepository.findByTelegramUserId(command.telegramUserId)
        ).thenReturn(user)

        assertThatIllegalStateException()
            .isThrownBy { userService.registerOrUpdate(command) }
            .withMessage("Existing user must have an id")

        assertThat(user.telegramChatId).isEqualTo(100L)
        assertThat(user.firstName).isEqualTo("Old")
        verify(userRepository, never()).save(any<User>())
    }

    private fun command() = RegisterOrUpdateUserCommand(
        telegramUserId = 123L,
        telegramChatId = 456L,
        username = "alex",
        firstName = "Alex",
        lastName = "Test"
    )

    private fun assertMatches(
        user: User,
        command: RegisterOrUpdateUserCommand
    ) {
        assertThat(user.telegramUserId)
            .isEqualTo(command.telegramUserId)

        assertThat(user.telegramChatId)
            .isEqualTo(command.telegramChatId)

        assertThat(user.username)
            .isEqualTo(command.username)

        assertThat(user.firstName)
            .isEqualTo(command.firstName)

        assertThat(user.lastName)
            .isEqualTo(command.lastName)
    }
}
