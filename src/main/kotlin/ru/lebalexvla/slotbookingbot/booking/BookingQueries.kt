package ru.lebalexvla.slotbookingbot.booking

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.common.ListPage
import java.time.Clock
import java.util.UUID

@Service
@Transactional(readOnly = true)
class BookingQueries(private val bookings: BookingRepository, private val clock: Clock) {
    fun list(actorId: UUID, mode: BookingList, page: Int = 0): ListPage<BookingCard> =
        ListPage.from(bookings.findCards(actorId, mode == BookingList.INCOMING, clock.instant(), ListPage.request(page))) { it }

    fun card(actorId: UUID, bookingId: UUID): BookingCard =
        bookings.findCard(actorId, bookingId) ?: throw BusinessException(BusinessError.BOOKING_NOT_FOUND)
}
