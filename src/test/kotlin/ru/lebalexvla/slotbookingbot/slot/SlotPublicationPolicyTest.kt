package ru.lebalexvla.slotbookingbot.slot

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SlotPublicationPolicyTest {
    private val now = Instant.parse("2030-01-01T00:00:00Z")
    private val policy = SlotPublicationPolicy(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `sorts intervals and permits adjacent boundaries`() {
        val first = interval(1, 2)
        val second = interval(2, 3)
        assertThat(policy.intervals(listOf(second, first))).containsExactly(first, second)
    }

    @Test
    fun `rejects empty excessive and reversed intervals`() {
        for (input in listOf(emptyList(), List(21) { interval(it + 1, it + 2) }, listOf(interval(2, 1)), listOf(interval(1, 1)))) {
            expect(BusinessError.INVALID_SLOT_INTERVALS) { policy.intervals(input) }
        }
        assertThat(policy.intervals(List(20) { interval(it + 1, it + 2) })).hasSize(20)
    }

    @Test
    fun `rejects intervals starting now or in the past`() {
        expect(BusinessError.SLOT_IN_PAST) { policy.intervals(listOf(interval(0, 1))) }
        expect(BusinessError.SLOT_IN_PAST) { policy.intervals(listOf(interval(-1, 1))) }
    }

    @Test
    fun `rejects duplicate partial and contained overlaps`() {
        for (second in listOf(interval(1, 4), interval(3, 5), interval(2, 3))) {
            expect(BusinessError.SLOT_OVERLAP) { policy.intervals(listOf(interval(1, 4), second)) }
        }
    }

    @Test
    fun `limit is positive and no greater than set size`() {
        assertThat(policy.limit(1, 2)).isEqualTo(1)
        assertThat(policy.limit(2, 2)).isEqualTo(2)
        for (value in listOf(-1, 0, 3)) expect(BusinessError.INVALID_SLOT_LIMIT) { policy.limit(value, 2) }
    }

    @Test
    fun `optional text is normalized bounded and safe for PostgreSQL`() {
        assertThat(policy.text("  Кафе  ", 4)).isEqualTo("Кафе")
        assertThat(policy.text("   ", 4)).isNull()
        assertThat(policy.text(null, 4)).isNull()
        expect(BusinessError.INVALID_SLOT_TEXT) { policy.text("длиннее", 4) }
        expect(BusinessError.INVALID_SLOT_TEXT) { policy.text("a\u0000b", 4) }
    }

    private fun interval(start: Int, end: Int) = SlotInterval(now.plusSeconds(start * 3600L), now.plusSeconds(end * 3600L))

    private fun expect(error: BusinessError, action: () -> Unit) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }
}
