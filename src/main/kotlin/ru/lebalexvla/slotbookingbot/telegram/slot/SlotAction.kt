package ru.lebalexvla.slotbookingbot.telegram.slot

import java.util.UUID
import ru.lebalexvla.slotbookingbot.slot.draft.PublicationDraft
import ru.lebalexvla.slotbookingbot.slot.SlotSetTargetType
import ru.lebalexvla.slotbookingbot.telegram.common.CallbackData
import ru.lebalexvla.slotbookingbot.telegram.common.CompactId
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramAction

data class DraftReference(val id: UUID, val revision: Int)
fun PublicationDraft.reference() = DraftReference(id, revision)

enum class SlotListMode(val code: String) { AVAILABLE("a"), OWN("o") }

sealed interface SlotAction : TelegramAction {
    data object Start : PublicationAction
    data object Resume : PublicationAction
    data class Listing(val mode: SlotListMode, val page: Int = 0) : SlotAction
    data class Card(val mode: SlotListMode, val id: UUID, val page: Int = 0) : SlotAction
    data class Targets(val draft: DraftReference, val type: SlotSetTargetType, val page: Int = 0) : PublicationAction
    data class SelectTarget(val draft: DraftReference, val type: SlotSetTargetType, val id: UUID) : PublicationAction
    data class Skip(val draft: DraftReference) : PublicationAction
    data class EditIntervals(val draft: DraftReference) : PublicationAction
    data class Confirm(val draft: DraftReference) : PublicationAction
    data class Cancel(val draft: DraftReference) : PublicationAction

    override fun encode(): String {
        fun ref(value: DraftReference) = "${CompactId.encode(value.id)}:${value.revision}"
        fun type(value: SlotSetTargetType) = if (value == SlotSetTargetType.USER) "u" else "c"
        return when (this) {
            Start -> "s:new"
            Resume -> "s:resume"
            is Listing -> "s:${mode.code}:$page"
            is Card -> "s:v:${mode.code}:${CompactId.encode(id)}:$page"
            is Targets -> "s:l:${ref(draft)}:${type(type)}:$page"
            is SelectTarget -> "s:t:${ref(draft)}:${type(type)}:${CompactId.encode(id)}"
            is Skip -> "s:k:${ref(draft)}"
            is EditIntervals -> "s:e:${ref(draft)}"
            is Confirm -> "s:p:${ref(draft)}"
            is Cancel -> "s:c:${ref(draft)}"
        }
    }

    companion object {
        fun parse(data: String?): SlotAction? {
            val payload = CallbackData.parse(data) ?: return null
            if (payload[0] != "s") return null
            return when (payload.size) {
                2 -> when (payload[1]) {
                    "new" -> Start
                    "resume" -> Resume
                    else -> null
                }
                3 -> Listing(payload.mode(1) ?: return null, payload.page(2) ?: return null)
                4 -> draftAction(payload)
                5 -> card(payload)
                6 -> recipientAction(payload)
                else -> null
            }
        }

        private fun draftAction(payload: CallbackData): PublicationAction? {
            val draft = payload.draft() ?: return null
            return when (payload[1]) {
                "k" -> Skip(draft)
                "e" -> EditIntervals(draft)
                "p" -> Confirm(draft)
                "c" -> Cancel(draft)
                else -> null
            }
        }

        private fun card(payload: CallbackData): Card? {
            if (payload[1] != "v") return null
            val mode = payload.mode(2) ?: return null
            val id = payload.id(3) ?: return null
            val page = payload.page(4) ?: return null
            return Card(mode, id, page)
        }

        private fun recipientAction(payload: CallbackData): PublicationAction? {
            val draft = payload.draft() ?: return null
            val type = when (payload[4]) {
                "u" -> SlotSetTargetType.USER
                "c" -> SlotSetTargetType.CATEGORY
                else -> return null
            }
            return when (payload[1]) {
                "l" -> Targets(draft, type, payload.page(5) ?: return null)
                "t" -> SelectTarget(draft, type, payload.id(5) ?: return null)
                else -> null
            }
        }

        private fun CallbackData.draft(): DraftReference? {
            val id = id(2) ?: return null
            val revision = number(3) ?: return null
            return DraftReference(id, revision)
        }

        private fun CallbackData.mode(index: Int): SlotListMode? =
            SlotListMode.entries.firstOrNull { it.code == get(index) }
    }
}

sealed interface PublicationAction : SlotAction
