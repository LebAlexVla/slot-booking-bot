package ru.lebalexvla.slotbookingbot.telegram.slot.publication

import org.springframework.stereotype.Component
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.slot.draft.DraftInput
import ru.lebalexvla.slotbookingbot.slot.draft.PublicationDraft
import ru.lebalexvla.slotbookingbot.slot.draft.PublicationStep
import ru.lebalexvla.slotbookingbot.slot.draft.SlotDraftService
import ru.lebalexvla.slotbookingbot.slot.SlotSetTarget
import ru.lebalexvla.slotbookingbot.slot.SlotSetTargetType
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramEvent
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramViews
import ru.lebalexvla.slotbookingbot.telegram.slot.PublicationAction
import ru.lebalexvla.slotbookingbot.telegram.slot.reference
import ru.lebalexvla.slotbookingbot.telegram.slot.SlotAction
import ru.lebalexvla.slotbookingbot.telegram.slot.TelegramSlotTime
import ru.lebalexvla.slotbookingbot.telegram.transport.OutgoingTelegramMessage

@Component
class TelegramPublicationDialog(
    private val drafts: SlotDraftService,
    private val recipients: TelegramRecipientMenu,
    private val views: TelegramPublicationViews
) {
    fun command(actor: TelegramActor, event: TelegramEvent.Command): OutgoingTelegramMessage? =
        when (event.name) {
            "new_slots" -> callback(actor, SlotAction.Start, event.updateId)
            "draft" -> callback(actor, SlotAction.Resume, event.updateId)
            "cancel" -> cancel(actor, event.updateId)
            "skip" -> views.prompt(actor, skip(actor, current(actor), event.updateId))
            else -> null
        }

    fun callback(actor: TelegramActor, action: PublicationAction, updateId: Int?): OutgoingTelegramMessage {
        val draft = when (action) {
            SlotAction.Start -> drafts.start(actor.userId, updateId)
            SlotAction.Resume -> current(actor)
            is SlotAction.Targets -> return recipients.list(actor, action)
            is SlotAction.SelectTarget -> drafts.selectTarget(
                actor.userId, action.draft.id, action.draft.revision, updateId, action.target()
            )
            is SlotAction.Skip -> skip(
                actor, drafts.inspect(actor.userId, action.draft.id, action.draft.revision), updateId
            )
            is SlotAction.EditIntervals -> drafts.editIntervals(
                actor.userId, action.draft.id, action.draft.revision, updateId
            )
            is SlotAction.Confirm -> drafts.confirm(actor.userId, action.draft.id, action.draft.revision, updateId)
            is SlotAction.Cancel -> drafts.cancel(actor.userId, action.draft.id, action.draft.revision, updateId)
        }
        return views.prompt(actor, draft)
    }

    fun text(actor: TelegramActor, text: String, updateId: Int?): OutgoingTelegramMessage? {
        val draft = drafts.current(actor.userId)?.takeIf { it.active } ?: return null
        if (draft.alreadyAccepted(updateId)) return views.prompt(actor, draft)
        val input = parseInput(draft, text)
            ?: return views.prompt(actor, draft, "На этом шаге используйте кнопки.")
        val updated = drafts.accept(actor.userId, draft.id, draft.revision, updateId, input)
        return views.prompt(actor, updated)
    }

    fun error(actor: TelegramActor, error: BusinessError): OutgoingTelegramMessage {
        val draft = drafts.current(actor.userId)
        return if (draft?.active == true) views.prompt(actor, draft, TelegramViews.errorText(error))
        else TelegramViews.error(actor.chatId, error)
    }

    private fun cancel(actor: TelegramActor, updateId: Int?): OutgoingTelegramMessage {
        val draft = drafts.current(actor.userId)?.takeIf { it.active }
            ?: return TelegramViews.menu(actor.chatId, "Активного черновика нет.")
        return callback(actor, SlotAction.Cancel(draft.reference()), updateId)
    }

    private fun skip(actor: TelegramActor, draft: PublicationDraft, updateId: Int?): PublicationDraft {
        if (draft.alreadyAccepted(updateId)) return draft
        val input = when (draft.step) {
            PublicationStep.PLACE -> DraftInput.Place(null)
            PublicationStep.DESCRIPTION -> DraftInput.Description(null)
            else -> throw BusinessException(BusinessError.STALE_DRAFT)
        }
        return drafts.accept(actor.userId, draft.id, draft.revision, updateId, input)
    }

    private fun parseInput(draft: PublicationDraft, text: String): DraftInput? = when (draft.step) {
        PublicationStep.INTERVALS -> DraftInput.Intervals(TelegramSlotTime.parse(text, draft.timeZone))
        PublicationStep.PLACE -> DraftInput.Place(text)
        PublicationStep.DESCRIPTION -> DraftInput.Description(text)
        PublicationStep.LIMIT -> DraftInput.Limit(
            text.trim().toIntOrNull() ?: throw BusinessException(BusinessError.INVALID_SLOT_LIMIT)
        )
        else -> null
    }

    private fun current(actor: TelegramActor): PublicationDraft =
        drafts.current(actor.userId) ?: throw BusinessException(BusinessError.DRAFT_NOT_FOUND)

    private fun PublicationDraft.alreadyAccepted(updateId: Int?): Boolean =
        updateId != null && lastUpdateId?.let { updateId <= it } == true

    private fun SlotAction.SelectTarget.target(): SlotSetTarget = when (type) {
        SlotSetTargetType.USER -> SlotSetTarget.UserTarget(id)
        SlotSetTargetType.CATEGORY -> SlotSetTarget.CategoryTarget(id)
    }
}
