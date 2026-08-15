package ru.lebalexvla.slotbookingbot.booking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.slot.SlotStatus

class BookingTransitionsIntegrationTest : BookingWorkflowFixture() {
    @Test
    fun `duplicate cancellation and rejection never affect a newer booking`() {
        val offer = offer()
        val first = bookings.book(BookSlotCommand(offer.booker.id!!, offer.ids[0]))
        bookings.cancel(first, offer.booker.id!!)
        val second = bookings.book(BookSlotCommand(offer.booker.id!!, offer.ids[0]))
        bookings.cancel(first, offer.booker.id!!)
        expect(BusinessError.STALE_BOOKING) { bookings.confirm(first, offer.owner.id!!) }
        assertThat(queries.card(offer.owner.id!!, second).status).isEqualTo(BookingStatus.PENDING)
        bookings.reject(second, offer.owner.id!!)
        val third = bookings.book(BookSlotCommand(offer.booker.id!!, offer.ids[0]))
        bookings.reject(second, offer.owner.id!!)
        assertThat(queries.card(offer.owner.id!!, third).status).isEqualTo(BookingStatus.PENDING)
        assertThat(slotRepository.findById(offer.ids[0]).orElseThrow().status).isEqualTo(SlotStatus.PENDING_CONFIRMATION)
        assertThat(bookingRepository.findAllBySlotId(offer.ids[0])).hasSize(3)
    }

    @Test
    fun `confirmation is idempotent but a cancellation from an older card is rejected`() {
        val offer = offer()
        val id = bookings.book(BookSlotCommand(offer.booker.id!!, offer.ids[0]))
        bookings.confirm(id, offer.owner.id!!, BookingStatus.PENDING)
        val confirmedAt = bookingRepository.findById(id).orElseThrow().confirmedAt
        clock.now = clock.now.plusSeconds(30)
        bookings.confirm(id, offer.owner.id!!, BookingStatus.PENDING)
        assertThat(bookingRepository.findById(id).orElseThrow().confirmedAt).isEqualTo(confirmedAt)
        expect(BusinessError.STALE_BOOKING) { bookings.cancel(id, offer.booker.id!!, BookingStatus.PENDING) }
        bookings.cancel(id, offer.booker.id!!, BookingStatus.CONFIRMED)
        assertThat(queries.card(offer.owner.id!!, id).status).isEqualTo(BookingStatus.CANCELED)
    }

    @Test
    fun `pending requests cannot be confirmed at start and release into expired status`() {
        val offer = offer(2, 2)
        val first = bookings.book(BookSlotCommand(offer.booker.id!!, offer.ids[0]))
        val second = bookings.book(BookSlotCommand(offer.booker.id!!, offer.ids[1]))
        clock.now = slotRepository.findById(offer.ids[1]).orElseThrow().startAt
        expect(BusinessError.BOOKING_STARTED) { bookings.confirm(first, offer.owner.id!!) }
        bookings.reject(first, offer.owner.id!!)
        bookings.cancel(second, offer.booker.id!!)
        offer.ids.forEach { assertThat(slotRepository.findById(it).orElseThrow().status).isEqualTo(SlotStatus.EXPIRED) }
        assertThat(slots.getVisibleSlots(offer.booker.id!!).items).isEmpty()
    }

    @Test
    fun `confirmed meeting cannot be canceled at exact start and repeated confirmation is harmless`() {
        val offer = offer()
        val id = bookings.book(BookSlotCommand(offer.booker.id!!, offer.ids[0]))
        bookings.confirm(id, offer.owner.id!!)
        clock.now = slotRepository.findById(offer.ids[0]).orElseThrow().startAt
        expect(BusinessError.BOOKING_STARTED) { bookings.cancel(id, offer.owner.id!!) }
        bookings.confirm(id, offer.owner.id!!)
        assertThat(queries.card(offer.booker.id!!, id).status).isEqualTo(BookingStatus.CONFIRMED)
    }

    @Test
    fun `past available slots reject booking without changing the slot`() {
        val offer = offer()
        clock.now = slotRepository.findById(offer.ids[0]).orElseThrow().startAt
        expect(BusinessError.BOOKING_STARTED) { bookings.book(BookSlotCommand(offer.booker.id!!, offer.ids[0])) }
        assertThat(bookingRepository.findAllBySlotId(offer.ids[0])).isEmpty()
    }

    @Test
    fun `only owner can decide while both parties retain access after category removal`() {
        val offer = offer(group = true)
        val outsider = user()
        val id = bookings.book(BookSlotCommand(offer.booker.id!!, offer.ids[0]))
        categories.removeMember(offer.owner.id!!, offer.categoryId!!, offer.booker.id!!)
        categories.archiveCategory(offer.owner.id!!, offer.categoryId)
        expect(BusinessError.BOOKING_NOT_FOUND) { bookings.confirm(id, offer.booker.id!!) }
        expect(BusinessError.BOOKING_NOT_FOUND) { bookings.reject(id, outsider.id!!) }
        expect(BusinessError.BOOKING_NOT_FOUND) { bookings.cancel(id, outsider.id!!) }
        expect(BusinessError.BOOKING_NOT_FOUND) { queries.card(outsider.id!!, id) }
        bookings.confirm(id, offer.owner.id!!)
        assertThat(queries.card(offer.booker.id!!, id).status).isEqualTo(BookingStatus.CONFIRMED)
        assertThat(queries.list(offer.owner.id!!, BookingList.INCOMING).items.map { it.id }).containsExactly(id)
        assertThat(queries.list(outsider.id!!, BookingList.INCOMING).items).isEmpty()
    }

    @Test
    fun `pages include current requests first and keep history accessible`() {
        val offer = offer(10, 10)
        val ids = offer.ids.map { bookings.book(BookSlotCommand(offer.booker.id!!, it)) }
        bookings.confirm(ids[0], offer.owner.id!!)
        bookings.reject(ids[1], offer.owner.id!!)
        val first = queries.list(offer.owner.id!!, BookingList.INCOMING)
        val second = queries.list(offer.owner.id!!, BookingList.INCOMING, 1)
        assertThat(first.items).hasSize(8).allMatch { it.status == BookingStatus.PENDING }
        assertThat(first.hasNext).isTrue()
        assertThat(second.items.map { it.status }).containsExactly(BookingStatus.CONFIRMED, BookingStatus.REJECTED)
        assertThat(second.hasNext).isFalse()
        assertThat(queries.list(offer.booker.id!!, BookingList.MINE).items.map { it.id }).containsExactlyElementsOf(first.items.map { it.id })
    }

    @Test
    fun `simultaneous confirmations are idempotent and retain a single active booking`() {
        val offer = offer()
        val id = bookings.book(BookSlotCommand(offer.booker.id!!, offer.ids[0]))
        val results = concurrent(
            { bookings.confirm(id, offer.owner.id!!, BookingStatus.PENDING) },
            { bookings.confirm(id, offer.owner.id!!, BookingStatus.PENDING) }
        )
        assertThat(results).allMatch { it.isSuccess }
        assertThat(bookingRepository.findAllBySlotId(offer.ids[0])).hasSize(1)
        assertThat(slotRepository.findById(offer.ids[0]).orElseThrow().status).isEqualTo(SlotStatus.BOOKED)
    }
}
