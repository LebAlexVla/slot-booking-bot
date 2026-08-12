package ru.lebalexvla.slotbookingbot.slot

import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import java.time.Clock

@Component
class SlotPublicationPolicy(private val clock: Clock) {
    fun intervals(intervals: List<SlotInterval>): List<SlotInterval> {
        if (intervals.size !in 1..MAX_INTERVALS || intervals.any { !it.endAt.isAfter(it.startAt) }) {
            throw BusinessException(BusinessError.INVALID_SLOT_INTERVALS)
        }
        val now = clock.instant()
        if (intervals.any { !it.startAt.isAfter(now) }) throw BusinessException(BusinessError.SLOT_IN_PAST)
        val sorted = intervals.sortedBy { it.startAt }
        if (sorted.zipWithNext().any { (first, second) -> first.endAt.isAfter(second.startAt) }) {
            throw BusinessException(BusinessError.SLOT_OVERLAP)
        }
        return sorted
    }

    fun limit(value: Int, intervalCount: Int): Int {
        if (value !in 1..intervalCount) throw BusinessException(BusinessError.INVALID_SLOT_LIMIT)
        return value
    }

    fun text(value: String?, maxLength: Int): String? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized != null && (normalized.length > maxLength || '\u0000' in normalized)) {
            throw BusinessException(BusinessError.INVALID_SLOT_TEXT)
        }
        return normalized
    }

    companion object {
        const val MAX_INTERVALS = 20
        const val MAX_PLACE = 200
        const val MAX_DESCRIPTION = 1000
    }
}
