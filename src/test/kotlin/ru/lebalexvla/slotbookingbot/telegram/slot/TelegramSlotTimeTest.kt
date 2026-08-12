package ru.lebalexvla.slotbookingbot.telegram.slot

import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException

class TelegramSlotTimeTest {
    @Test
    fun `converts explicit local dates to UTC and back`() {
        val intervals = TelegramSlotTime.parse("2030-01-02 10:00-11:00\n\n2030-01-02 11:00 - 12:00", "Europe/Moscow")
        assertThat(intervals).hasSize(2)
        assertThat(intervals.first().startAt).isEqualTo(Instant.parse("2030-01-02T07:00:00Z"))
        assertThat(TelegramSlotTime.format(intervals.first(), "Europe/Moscow"))
            .isEqualTo("02.01.2030 10:00 — 02.01.2030 11:00")
    }

    @Test
    fun `rejects impossible dates times and ambiguous input formats`() {
        for (input in listOf("2030-02-29 10:00-11:00", "2030-01-01 24:00-25:00", "tomorrow 10-11", "01.01.2030 10:00-11:00")) {
            expect(BusinessError.INVALID_SLOT_FORMAT) { TelegramSlotTime.parse(input, "Europe/Moscow") }
        }
    }

    @Test
    fun `rejects both missing and ambiguous DST local times`() {
        for (input in listOf("2030-03-31 02:15-03:15", "2030-10-27 02:15-03:15")) {
            expect(BusinessError.INVALID_LOCAL_TIME) { TelegramSlotTime.parse(input, "Europe/Berlin") }
        }
    }

    private fun expect(error: BusinessError, action: () -> Unit) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }
}
