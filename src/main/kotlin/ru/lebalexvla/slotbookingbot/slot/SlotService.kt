package ru.lebalexvla.slotbookingbot.slot

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.lebalexvla.slotbookingbot.category.CategoryRepository
import ru.lebalexvla.slotbookingbot.contact.ContactRepository
import ru.lebalexvla.slotbookingbot.user.User
import ru.lebalexvla.slotbookingbot.user.UserRepository
import java.time.Instant
import java.util.UUID

@Service
class SlotService(
    private val slotSetRepository: SlotSetRepository,
    private val slotRepository: SlotRepository,
    private val userRepository: UserRepository,
    private val contactRepository: ContactRepository,
    private val categoryRepository: CategoryRepository
) {

    @Transactional
    fun publish(command: PublishSlotSetCommand): UUID {
        validate(command)

        val owner = getUser(command.ownerId, "Owner not found")
        val slotSet = createSlotSet(command, owner)
        val savedSlotSet = slotSetRepository.save(slotSet)

        slotRepository.saveAll(
            command.slots.map { interval ->
                interval.toSlot(savedSlotSet)
            }
        )

        return savedSlotSet.id!!
    }

    @Transactional(readOnly = true)
    fun getVisibleSlots(
        userId: UUID,
        now: Instant = Instant.now()
    ): List<VisibleSlot> =
        slotRepository.findVisibleSlots(userId, now)
            .map { it.toVisibleSlot() }

    private fun createSlotSet(
        command: PublishSlotSetCommand,
        owner: User
    ): SlotSet =
        when (val target = command.target) {
            is SlotSetTarget.UserTarget ->
                createUserTargetedSlotSet(command, owner, target)

            is SlotSetTarget.CategoryTarget ->
                createCategoryTargetedSlotSet(command, owner, target)
        }

    private fun createUserTargetedSlotSet(
        command: PublishSlotSetCommand,
        owner: User,
        target: SlotSetTarget.UserTarget
    ): SlotSet {
        require(
            contactRepository.existsByOwnerIdAndContactUserId(
                command.ownerId,
                target.userId
            )
        ) {
            "Target user is not a contact"
        }

        val targetUser = getUser(
            target.userId,
            "Target user not found"
        )

        return SlotSet(
            owner = owner,
            targetType = SlotSetTargetType.USER,
            targetUser = targetUser,
            place = command.place,
            description = command.description,
            maxBookingsPerUser = command.maxBookingsPerUser
        )
    }

    private fun createCategoryTargetedSlotSet(
        command: PublishSlotSetCommand,
        owner: User,
        target: SlotSetTarget.CategoryTarget
    ): SlotSet {
        val category = categoryRepository.findByIdAndOwnerId(
            target.categoryId,
            command.ownerId
        ) ?: throw IllegalArgumentException("Category not found")

        return SlotSet(
            owner = owner,
            targetType = SlotSetTargetType.CATEGORY,
            targetCategory = category,
            place = command.place,
            description = command.description,
            maxBookingsPerUser = command.maxBookingsPerUser
        )
    }

    private fun getUser(
        id: UUID,
        errorMessage: String
    ): User =
        userRepository.findById(id)
            .orElseThrow {
                IllegalArgumentException(errorMessage)
            }

    private fun validate(command: PublishSlotSetCommand) {
        require(command.maxBookingsPerUser > 0) {
            "Max bookings per user must be positive"
        }

        require(command.slots.isNotEmpty()) {
            "Slot set must contain at least one slot"
        }

        require(
            command.slots.all {
                it.endAt.isAfter(it.startAt)
            }
        ) {
            "Slot end time must be after start time"
        }
    }

    private fun SlotInterval.toSlot(
        slotSet: SlotSet
    ) = Slot(
        slotSet = slotSet,
        startAt = startAt,
        endAt = endAt
    )

    private fun VisibleSlotProjection.toVisibleSlot() =
        VisibleSlot(
            id = id,
            slotSetId = slotSetId,
            startAt = startAt,
            endAt = endAt,
            place = place,
            description = description
        )
}