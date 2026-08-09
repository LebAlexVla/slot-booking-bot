package ru.lebalexvla.slotbookingbot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.lebalexvla.slotbookingbot.telegram.TelegramBotProperties

@Testcontainers
@SpringBootTest(
    properties = [
        "telegram.bot.enabled=false",
        "telegram.bot.token=test-token"
    ]
)
class SlotBookingBotApplicationTest @Autowired constructor(
    private val applicationContext: ApplicationContext,
    private val properties: TelegramBotProperties
) {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-bookworm")
    }

    @Test
    fun `loads application context without starting Telegram polling`() {
        assertThat(properties.enabled).isFalse()
        assertThat(properties.token).isEqualTo("test-token")
        assertThat(
            applicationContext.getBeansOfType(
                TelegramBotsLongPollingApplication::class.java
            )
        ).isEmpty()
    }
}
