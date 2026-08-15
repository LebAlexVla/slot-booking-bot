package ru.lebalexvla.slotbookingbot.telegram.common

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.lebalexvla.slotbookingbot.booking.request.BookingRequestService
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.slot.draft.SlotDraftService
import ru.lebalexvla.slotbookingbot.user.UserRepository
import java.time.Clock
import java.util.UUID

@Service
class TelegramDraftCoordinator(
    private val users: UserRepository,
    private val publications: SlotDraftService,
    private val requests: BookingRequestService,
    private val clock: Clock
) {
    @Transactional
    fun startPublication(userId: UUID, updateId: Int?) = locked(userId) {
        if (requests.current(userId)?.activeAt(clock.instant()) == true) {
            throw BusinessException(BusinessError.BOOKING_IN_PROGRESS)
        }
        publications.start(userId, updateId)
    }

    @Transactional
    fun startBooking(userId: UUID, slotId: UUID, updateId: Int?) = locked(userId) {
        val publication = publications.current(userId)
        if (publication?.active == true && publication.expiresAt.isAfter(clock.instant())) {
            throw BusinessException(BusinessError.PUBLICATION_IN_PROGRESS)
        }
        requests.start(userId, slotId, updateId)
    }

    private fun <T> locked(userId: UUID, action: () -> T): T {
        if (users.findForMutationById(userId) == null) throw BusinessException(BusinessError.USER_NOT_FOUND)
        return action()
    }
}
