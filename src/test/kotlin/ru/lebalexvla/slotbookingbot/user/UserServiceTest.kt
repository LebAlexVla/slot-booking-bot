package ru.lebalexvla.slotbookingbot.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserServiceTest {

    private val userRepository: UserRepository = mock()
    private val userService = UserService(userRepository)

    @Test
    fun `creates user when user does not exist`() {
        val command = command()

        whenever(
            userRepository.findByTelegramUserId(command.telegramUserId)
        ).thenReturn(null)

        userService.registerOrUpdate(command)

        argumentCaptor<User>().apply {
            verify(userRepository).save(capture())
            assertMatches(firstValue, command)
        }
    }

    @Test
    fun `updates existing user`() {
        val user = User(
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

        userService.registerOrUpdate(command)

        assertMatches(user, command)

        verify(userRepository, never()).save(user)
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