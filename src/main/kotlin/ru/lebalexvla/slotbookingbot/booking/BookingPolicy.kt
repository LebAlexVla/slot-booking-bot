package ru.lebalexvla.slotbookingbot.booking

import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException

object BookingPolicy {
    const val MAX_COMMENT = 1000
    const val MAX_PLACE = 200

    fun text(value: String?, maxLength: Int): String? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized != null && (normalized.length > maxLength || '\u0000' in normalized)) {
            throw BusinessException(BusinessError.INVALID_BOOKING_TEXT)
        }
        return normalized
    }
}
