package ru.lebalexvla.slotbookingbot.telegram

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.meta.generics.TelegramClient

@Configuration
@EnableConfigurationProperties(TelegramBotProperties::class)
class TelegramConfiguration {

    @Bean
    fun telegramClient(properties: TelegramBotProperties): TelegramClient =
        OkHttpTelegramClient(properties.token)

    @Bean(destroyMethod = "close")
    fun telegramBotsLongPollingApplication(
        properties: TelegramBotProperties,
        updateConsumer: TelegramUpdateConsumer
    ): TelegramBotsLongPollingApplication {
        return TelegramBotsLongPollingApplication().apply {
            registerBot(properties.token, updateConsumer)
        }
    }

}