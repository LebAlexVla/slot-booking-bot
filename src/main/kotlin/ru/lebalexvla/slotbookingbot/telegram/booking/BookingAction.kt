package ru.lebalexvla.slotbookingbot.telegram.booking

import ru.lebalexvla.slotbookingbot.booking.BookingDecision
import ru.lebalexvla.slotbookingbot.booking.BookingList
import ru.lebalexvla.slotbookingbot.booking.BookingStatus
import ru.lebalexvla.slotbookingbot.booking.request.BookingRequest
import ru.lebalexvla.slotbookingbot.telegram.common.CallbackData
import ru.lebalexvla.slotbookingbot.telegram.common.CompactId
import ru.lebalexvla.slotbookingbot.telegram.common.DraftReference
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramAction
import java.util.UUID

fun BookingRequest.reference() = DraftReference(id, revision)

sealed interface BookingAction : TelegramAction {
    data class Listing(val mode: BookingList, val page: Int = 0) : BookingAction
    data class Card(val id: UUID, val mode: BookingList = BookingList.MINE, val page: Int = 0) : BookingAction
    data class Decide(
        val id: UUID, val decision: BookingDecision, val expected: BookingStatus,
        val mode: BookingList, val page: Int = 0
    ) : BookingAction
    data class Begin(val slotId: UUID) : BookingRequestAction
    data object Resume : BookingRequestAction
    data class Skip(val draft: DraftReference) : BookingRequestAction
    data class Submit(val draft: DraftReference) : BookingRequestAction
    data class Discard(val draft: DraftReference) : BookingRequestAction

    override fun encode(): String {
        fun mode(value: BookingList) = if (value == BookingList.MINE) "m" else "i"
        fun ref(value: DraftReference) = "${CompactId.encode(value.id)}:${value.revision}"
        return when (this) {
            is Listing -> "b:l:${mode(mode)}:$page"
            is Card -> "b:v:${CompactId.encode(id)}:${mode(mode)}:$page"
            is Decide -> "b:d:${CompactId.encode(id)}:${decision.name}:${expected.name}:${mode(mode)}:$page"
            is Begin -> "b:n:${CompactId.encode(slotId)}"
            Resume -> "b:r"
            is Skip -> "b:k:${ref(draft)}"
            is Submit -> "b:s:${ref(draft)}"
            is Discard -> "b:c:${ref(draft)}"
        }
    }

    companion object {
        fun parse(data: String?): BookingAction? {
            val payload = CallbackData.parse(data) ?: return null
            if (payload[0] != "b") return null
            return when (payload.size) {
                2 -> if (payload[1] == "r") Resume else null
                3 -> if (payload[1] == "n") Begin(payload.id(2) ?: return null) else null
                4 -> shortAction(payload)
                5 -> card(payload)
                7 -> decision(payload)
                else -> null
            }
        }

        private fun shortAction(payload: CallbackData): BookingAction? {
            if (payload[1] == "l") return Listing(payload.mode(2) ?: return null, payload.page(3) ?: return null)
            val ref = DraftReference(payload.id(2) ?: return null, payload.number(3) ?: return null)
            return when (payload[1]) {
                "k" -> Skip(ref)
                "s" -> Submit(ref)
                "c" -> Discard(ref)
                else -> null
            }
        }

        private fun card(payload: CallbackData): Card? {
            if (payload[1] != "v") return null
            return Card(payload.id(2) ?: return null, payload.mode(3) ?: return null, payload.page(4) ?: return null)
        }

        private fun decision(payload: CallbackData): Decide? {
            if (payload[1] != "d") return null
            val id = payload.id(2) ?: return null
            val decision = BookingDecision.entries.firstOrNull { it.name == payload[3] } ?: return null
            val expected = BookingStatus.entries.firstOrNull {
                it.name == payload[4] && it in setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED)
            } ?: return null
            return Decide(id, decision, expected, payload.mode(5) ?: return null, payload.page(6) ?: return null)
        }

        private fun CallbackData.mode(index: Int) = when (get(index)) {
            "m" -> BookingList.MINE
            "i" -> BookingList.INCOMING
            else -> null
        }
    }
}

sealed interface BookingRequestAction : BookingAction
