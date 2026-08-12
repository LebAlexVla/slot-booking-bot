package ru.lebalexvla.slotbookingbot.telegram.common

import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.lebalexvla.slotbookingbot.user.RegisterOrUpdateUserCommand
import ru.lebalexvla.slotbookingbot.user.UserService

class TelegramActorResolverTest {

    private val userService: UserService = mock()
    private val actorResolver = TelegramActorResolver(userService)

    @Test
    fun `registers profile and returns internal actor`() {
        val userId = UUID.randomUUID()
        val profile = TelegramActorProfile(
            telegramUserId = 123L,
            chatId = 456L,
            username = "alex",
            firstName = "Alex",
            lastName = "Test"
        )

        whenever(userService.registerOrUpdate(any()))
            .thenReturn(userId)

        val actor = actorResolver.resolveAndSync(profile)

        assertThat(actor).isEqualTo(
            TelegramActor(
                userId = userId,
                chatId = profile.chatId
            )
        )
        argumentCaptor<RegisterOrUpdateUserCommand>().apply {
            verify(userService).registerOrUpdate(capture())
            assertThat(firstValue.telegramUserId)
                .isEqualTo(profile.telegramUserId)
            assertThat(firstValue.telegramChatId)
                .isEqualTo(profile.chatId)
            assertThat(firstValue.username).isEqualTo(profile.username)
            assertThat(firstValue.firstName).isEqualTo(profile.firstName)
            assertThat(firstValue.lastName).isEqualTo(profile.lastName)
        }
    }
}
