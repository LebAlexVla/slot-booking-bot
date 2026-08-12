package ru.lebalexvla.slotbookingbot.telegram.slot

import java.time.DateTimeException
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.slot.SlotInterval

object TelegramSlotTime {
    private val input = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
    private val display = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")
    private val line = Regex("""(\d{4}-\d{2}-\d{2})[ \t]+(\d{2}:\d{2})[ \t]*-[ \t]*(\d{2}:\d{2})""")

    fun parse(text: String, timeZone: String): List<SlotInterval> =
        text.trim().lines().filter { it.isNotBlank() }.map {
            val match = line.matchEntire(it.trim()) ?: throw BusinessException(BusinessError.INVALID_SLOT_FORMAT)
            val (date, start, end) = match.destructured
            SlotInterval(instant("$date $start", timeZone), instant("$date $end", timeZone))
        }

    private fun instant(text: String, timeZone: String): Instant {
        val local = try {
            LocalDateTime.parse(text, input)
        } catch (_: DateTimeException) {
            throw BusinessException(BusinessError.INVALID_SLOT_FORMAT)
        }
        val offsets = ZoneId.of(timeZone).rules.getValidOffsets(local)
        if (offsets.size != 1) throw BusinessException(BusinessError.INVALID_LOCAL_TIME)
        return local.toInstant(offsets.single())
    }

    fun format(value: Instant, timeZone: String): String = display.withZone(ZoneId.of(timeZone)).format(value)

    fun format(interval: SlotInterval, timeZone: String): String =
        "${format(interval.startAt, timeZone)} — ${format(interval.endAt, timeZone)}"
}
