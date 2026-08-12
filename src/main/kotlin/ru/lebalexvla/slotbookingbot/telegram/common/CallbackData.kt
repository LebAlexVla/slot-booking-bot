package ru.lebalexvla.slotbookingbot.telegram.common

import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import ru.lebalexvla.slotbookingbot.common.ListPage

/**
 * Two UUIDs fit in Telegram's 64-byte callback payload without exposing database IDs in the UI.
 * Encoding is not authorization: every operation still checks its actor in the service.
 */
object CompactId {
    fun encode(id: UUID): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        ByteBuffer.allocate(16).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).array()
    )

    fun decode(value: String): UUID? {
        if (value.length != 22) return null
        return try {
            val bytes = Base64.getUrlDecoder().decode(value)
            if (bytes.size != 16) return null
            val buffer = ByteBuffer.wrap(bytes)
            UUID(buffer.long, buffer.long).takeIf { encode(it) == value }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

interface TelegramAction {
    fun encode(): String
}

/** Validates the envelope once; action codecs validate their own argument count and meaning. */
class CallbackData private constructor(private val parts: List<String>) {
    val size: Int get() = parts.size

    operator fun get(index: Int): String? = parts.getOrNull(index)
    fun id(index: Int): UUID? = get(index)?.let(CompactId::decode)
    fun number(index: Int, maximum: Int = Int.MAX_VALUE): Int? =
        get(index)?.toIntOrNull()?.takeIf { it in 0..maximum }

    fun page(index: Int): Int? = number(index, ListPage.MAX_NUMBER)

    companion object {
        fun parse(data: String?): CallbackData? {
            if (data == null || data.toByteArray(Charsets.UTF_8).size !in 1..64) return null
            return CallbackData(data.split(':'))
        }
    }
}
