package ru.lebalexvla.slotbookingbot.slot

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration
import java.time.ZoneId

@ConfigurationProperties("booking.publication")
data class SlotPublicationProperties(
    val timeZone: String = "Europe/Moscow",
    val draftTtl: Duration = Duration.ofHours(24)
) {
    init {
        ZoneId.of(timeZone)
        require(!draftTtl.isNegative && !draftTtl.isZero) { "Draft TTL must be positive" }
    }
}

@Configuration
@EnableConfigurationProperties(SlotPublicationProperties::class)
class SlotPublicationConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
