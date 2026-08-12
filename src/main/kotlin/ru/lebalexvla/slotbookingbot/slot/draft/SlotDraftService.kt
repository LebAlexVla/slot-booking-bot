package ru.lebalexvla.slotbookingbot.slot.draft

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.lebalexvla.slotbookingbot.category.CategoryRepository
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.contact.ContactRepository
import ru.lebalexvla.slotbookingbot.slot.PublishSlotSetCommand
import ru.lebalexvla.slotbookingbot.slot.SlotInterval
import ru.lebalexvla.slotbookingbot.slot.SlotPublicationPolicy
import ru.lebalexvla.slotbookingbot.slot.SlotPublicationProperties
import ru.lebalexvla.slotbookingbot.slot.SlotService
import ru.lebalexvla.slotbookingbot.slot.SlotSetTarget
import ru.lebalexvla.slotbookingbot.slot.SlotSetTargetType
import ru.lebalexvla.slotbookingbot.user.UserRepository
import ru.lebalexvla.slotbookingbot.user.toSummary
import java.time.Clock
import java.util.UUID

@Service
class SlotDraftService(
    private val drafts: SlotPublicationDraftRepository,
    private val users: UserRepository,
    private val contacts: ContactRepository,
    private val categories: CategoryRepository,
    private val publisher: SlotService,
    private val policy: SlotPublicationPolicy,
    private val properties: SlotPublicationProperties,
    private val clock: Clock
) {
    @Transactional
    fun start(ownerId: UUID, updateId: Int?): PublicationDraft {
        lockOwner(ownerId)
        val draft = drafts.findLockedByOwnerId(ownerId)
        if (draft != null && draft.expiresAt.isAfter(clock.instant()) && updateId != null &&
            draft.lastUpdateId?.let { updateId <= it } == true) return draft.toView()
        if (draft != null && draft.toView().active && draft.expiresAt.isAfter(clock.instant())) {
            return draft.toView()
        }
        val current = draft ?: SlotPublicationDraft(
            ownerId = ownerId, timeZone = properties.timeZone, expiresAt = clock.instant().plus(properties.draftTtl)
        )
        current.sessionId = UUID.randomUUID()
        current.revision = 0
        current.step = PublicationStep.RECIPIENT
        current.timeZone = properties.timeZone
        current.expiresAt = clock.instant().plus(properties.draftTtl)
        current.targetType = null
        current.targetUserId = null
        current.targetCategoryId = null
        current.targetLabel = null
        current.intervals.clear()
        current.place = null
        current.description = null
        current.maxBookingsPerUser = null
        current.publishedSetId = null
        current.lastUpdateId = updateId
        return drafts.saveAndFlush(current).toView()
    }

    @Transactional(readOnly = true)
    fun current(ownerId: UUID): PublicationDraft? = drafts.findById(ownerId).orElse(null)?.toView()

    @Transactional(readOnly = true)
    fun inspect(ownerId: UUID, id: UUID, revision: Int): PublicationDraft {
        val draft = drafts.findById(ownerId).orElseThrow { BusinessException(BusinessError.DRAFT_NOT_FOUND) }
        requireCurrent(draft, id, revision)
        return draft.toView()
    }

    @Transactional
    fun selectTarget(
        ownerId: UUID, id: UUID, revision: Int, updateId: Int?, target: SlotSetTarget
    ): PublicationDraft = mutate(ownerId, id, revision, updateId) { draft ->
        requireStep(draft, PublicationStep.RECIPIENT)
        when (target) {
            is SlotSetTarget.UserTarget -> {
                if (!contacts.existsByOwnerIdAndContactUserId(ownerId, target.userId)) {
                    throw BusinessException(BusinessError.CONTACT_REQUIRED)
                }
                val user = users.findById(target.userId)
                    .orElseThrow { BusinessException(BusinessError.USER_NOT_FOUND) }
                draft.targetType = SlotSetTargetType.USER
                draft.targetUserId = target.userId
                draft.targetCategoryId = null
                draft.targetLabel = user.toSummary().name
            }
            is SlotSetTarget.CategoryTarget -> {
                val category = categories.findLockedByIdAndOwnerIdAndArchivedAtIsNull(target.categoryId, ownerId)
                    ?: throw BusinessException(BusinessError.CATEGORY_NOT_FOUND)
                draft.targetType = SlotSetTargetType.CATEGORY
                draft.targetCategoryId = target.categoryId
                draft.targetUserId = null
                draft.targetLabel = category.name
            }
        }
        draft.step = PublicationStep.INTERVALS
    }

    @Transactional
    fun accept(
        ownerId: UUID, id: UUID, revision: Int, updateId: Int?, input: DraftInput
    ): PublicationDraft = mutate(ownerId, id, revision, updateId) { draft ->
        when (input) {
            is DraftInput.Intervals -> {
                requireStep(draft, PublicationStep.INTERVALS)
                val intervals = policy.intervals(input.value)
                draft.intervals.clear()
                draft.intervals.addAll(intervals.map { DraftInterval(it.startAt, it.endAt) })
                draft.step = PublicationStep.PLACE
            }
            is DraftInput.Place -> {
                requireStep(draft, PublicationStep.PLACE)
                draft.place = policy.text(input.value, SlotPublicationPolicy.MAX_PLACE)
                draft.step = PublicationStep.DESCRIPTION
            }
            is DraftInput.Description -> {
                requireStep(draft, PublicationStep.DESCRIPTION)
                draft.description = policy.text(input.value, SlotPublicationPolicy.MAX_DESCRIPTION)
                draft.step = PublicationStep.LIMIT
            }
            is DraftInput.Limit -> {
                requireStep(draft, PublicationStep.LIMIT)
                draft.maxBookingsPerUser = policy.limit(input.value, draft.intervals.size)
                draft.step = PublicationStep.REVIEW
            }
        }
    }

    @Transactional
    fun editIntervals(ownerId: UUID, id: UUID, revision: Int, updateId: Int?): PublicationDraft =
        mutate(ownerId, id, revision, updateId) {
            requireStep(it, PublicationStep.REVIEW)
            it.step = PublicationStep.INTERVALS
        }

    @Transactional
    fun cancel(ownerId: UUID, id: UUID, revision: Int, updateId: Int?): PublicationDraft =
        mutate(ownerId, id, revision, updateId) { it.step = PublicationStep.CANCELED }

    @Transactional
    fun confirm(ownerId: UUID, id: UUID, revision: Int, updateId: Int?): PublicationDraft {
        lockOwner(ownerId)
        val draft = locked(ownerId)
        if (draft.sessionId == id && draft.step == PublicationStep.PUBLISHED) return draft.toView()
        return change(draft, id, revision, updateId) {
            requireStep(it, PublicationStep.REVIEW)
            val view = it.toView()
            val setId = publisher.publish(PublishSlotSetCommand(
                ownerId, checkNotNull(view.target), view.place, view.description,
                checkNotNull(view.maxBookingsPerUser), view.intervals
            ))
            it.publishedSetId = setId
            it.step = PublicationStep.PUBLISHED
        }
    }

    private fun mutate(
        ownerId: UUID, id: UUID, revision: Int, updateId: Int?, operation: (SlotPublicationDraft) -> Unit
    ): PublicationDraft {
        lockOwner(ownerId)
        return change(locked(ownerId), id, revision, updateId, operation)
    }

    private fun change(
        draft: SlotPublicationDraft, id: UUID, revision: Int, updateId: Int?,
        operation: (SlotPublicationDraft) -> Unit
    ): PublicationDraft {
        if (draft.sessionId != id) throw BusinessException(BusinessError.STALE_DRAFT)
        // A repeated or delayed Telegram update must not fill the next input field.
        if (updateId != null && draft.lastUpdateId?.let { updateId <= it } == true) return draft.toView()
        requireCurrent(draft, id, revision)
        operation(draft)
        draft.revision++
        draft.lastUpdateId = updateId ?: draft.lastUpdateId
        draft.expiresAt = clock.instant().plus(properties.draftTtl)
        return draft.toView()
    }

    private fun lockOwner(ownerId: UUID) {
        if (users.findForPublicationById(ownerId) == null) throw BusinessException(BusinessError.USER_NOT_FOUND)
    }

    private fun locked(ownerId: UUID): SlotPublicationDraft =
        drafts.findLockedByOwnerId(ownerId) ?: throw BusinessException(BusinessError.DRAFT_NOT_FOUND)

    private fun requireCurrent(draft: SlotPublicationDraft, id: UUID, revision: Int) {
        if (draft.sessionId != id || draft.revision != revision || !draft.toView().active) {
            throw BusinessException(BusinessError.STALE_DRAFT)
        }
        if (!draft.expiresAt.isAfter(clock.instant())) throw BusinessException(BusinessError.DRAFT_EXPIRED)
    }

    private fun requireStep(draft: SlotPublicationDraft, step: PublicationStep) {
        if (draft.step != step) throw BusinessException(BusinessError.STALE_DRAFT)
    }

    private fun SlotPublicationDraft.toView() = PublicationDraft(
        sessionId, revision, step, timeZone, expiresAt,
        when (targetType) {
            SlotSetTargetType.USER -> SlotSetTarget.UserTarget(checkNotNull(targetUserId))
            SlotSetTargetType.CATEGORY -> SlotSetTarget.CategoryTarget(checkNotNull(targetCategoryId))
            null -> null
        },
        targetLabel, intervals.map { SlotInterval(it.startAt, it.endAt) },
        place, description, maxBookingsPerUser, publishedSetId, lastUpdateId
    )
}
