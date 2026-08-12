package ru.lebalexvla.slotbookingbot.slot

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.lebalexvla.slotbookingbot.category.CategoryRepository
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.common.ListPage
import ru.lebalexvla.slotbookingbot.contact.ContactRepository
import ru.lebalexvla.slotbookingbot.user.UserRepository
import java.time.Clock
import java.util.UUID

@Service
class SlotService(
    private val slotSetRepository: SlotSetRepository,
    private val slotRepository: SlotRepository,
    private val userRepository: UserRepository,
    private val contactRepository: ContactRepository,
    private val categoryRepository: CategoryRepository,
    private val policy: SlotPublicationPolicy,
    private val clock: Clock
) {
    @Transactional
    fun publish(command: PublishSlotSetCommand): UUID {
        // Serialize publication by owner without blocking FK references from other owners.
        val owner = userRepository.findForPublicationById(command.ownerId)
            ?: throw BusinessException(BusinessError.USER_NOT_FOUND)
        val intervals = policy.intervals(command.slots)
        val limit = policy.limit(command.maxBookingsPerUser, intervals.size)
        val place = policy.text(command.place, SlotPublicationPolicy.MAX_PLACE)
        val description = policy.text(command.description, SlotPublicationPolicy.MAX_DESCRIPTION)

        val slotSet = when (val target = command.target) {
            is SlotSetTarget.UserTarget -> {
                if (!contactRepository.existsByOwnerIdAndContactUserId(command.ownerId, target.userId)) {
                    throw BusinessException(BusinessError.CONTACT_REQUIRED)
                }
                SlotSet(
                    owner = owner, targetType = SlotSetTargetType.USER,
                    targetUser = userRepository.findById(target.userId)
                        .orElseThrow { BusinessException(BusinessError.USER_NOT_FOUND) },
                    place = place, description = description, maxBookingsPerUser = limit
                )
            }
            is SlotSetTarget.CategoryTarget -> {
                val category = categoryRepository.findLockedByIdAndOwnerIdAndArchivedAtIsNull(
                    target.categoryId, command.ownerId
                ) ?: throw BusinessException(BusinessError.CATEGORY_NOT_FOUND)
                SlotSet(
                    owner = owner, targetType = SlotSetTargetType.CATEGORY, targetCategory = category,
                    place = place, description = description, maxBookingsPerUser = limit
                )
            }
        }
        if (intervals.any { slotRepository.overlaps(command.ownerId, it.startAt, it.endAt) }) {
            throw BusinessException(BusinessError.SLOT_OVERLAP)
        }
        val saved = slotSetRepository.save(slotSet)
        slotRepository.saveAll(intervals.map { Slot(slotSet = saved, startAt = it.startAt, endAt = it.endAt) })
        return checkNotNull(saved.id)
    }

    @Transactional(readOnly = true)
    fun getVisibleSlots(userId: UUID, page: Int = 0): ListPage<SlotCard> =
        ListPage.from(slotRepository.findVisibleSlots(userId, clock.instant(), ListPage.request(page))) { it.toCard() }

    @Transactional(readOnly = true)
    fun getVisibleSlot(userId: UUID, slotId: UUID): SlotCard =
        slotRepository.findVisibleSlot(userId, slotId, clock.instant())?.toCard()
            ?: throw BusinessException(BusinessError.SLOT_NOT_AVAILABLE)

    @Transactional(readOnly = true)
    fun getOwnedSlots(ownerId: UUID, page: Int = 0): ListPage<SlotCard> =
        ListPage.from(slotRepository.findOwnedSlots(ownerId, clock.instant(), ListPage.request(page))) { it.toCard() }

    @Transactional(readOnly = true)
    fun getOwnedSlot(ownerId: UUID, slotId: UUID): SlotCard =
        slotRepository.findOwnedSlot(ownerId, slotId)?.toCard()
            ?: throw BusinessException(BusinessError.SLOT_NOT_AVAILABLE)
}
