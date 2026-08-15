package ru.lebalexvla.slotbookingbot.booking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.lebalexvla.slotbookingbot.booking.request.BookingRequest
import ru.lebalexvla.slotbookingbot.booking.request.BookingRequestStep
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.slot.SlotStatus

class BookingRequestIntegrationTest : BookingWorkflowFixture() {
    @Test
    fun `simultaneous submissions commit one booking and a durable result`() {
        val offer = offer()
        val draft = ready(offer)
        assertThat(slotRepository.findById(offer.ids[0]).orElseThrow().status).isEqualTo(SlotStatus.AVAILABLE)
        val results = concurrent({ submit(offer, draft) }, { submit(offer, draft) })
        assertThat(results).allMatch { it.isSuccess }
        val ids = results.map { (it.getOrThrow() as BookingRequest).bookingId }.distinct()
        assertThat(ids).hasSize(1)
        assertThat(requests.current(offer.booker.id!!)!!.bookingId).isEqualTo(ids.single())
        assertThat(bookingRepository.findAllBySlotId(offer.ids[0])).hasSize(1)
        val card = queries.card(offer.owner.id!!, ids.single()!!)
        assertThat(card.comment).isEqualTo("Комментарий")
        assertThat(card.proposedPlace).isEqualTo("Библиотека")
    }

    @Test
    fun `old submission cannot recreate a canceled booking or affect a new request`() {
        val offer = offer()
        val old = ready(offer)
        val submitted = submit(offer, old)
        bookings.cancel(submitted.bookingId!!, offer.booker.id!!)
        assertThat(submit(offer, old).bookingId).isEqualTo(submitted.bookingId)
        val fresh = requests.start(offer.booker.id!!, offer.ids[0], null)
        expect(BusinessError.STALE_DRAFT) { submit(offer, old) }
        assertThat(requests.current(offer.booker.id!!)!!.id).isEqualTo(fresh.id)
        assertThat(bookingRepository.findAllBySlotId(offer.ids[0])).hasSize(1)
    }

    @Test
    fun `a losing request keeps its draft and does not partially commit`() {
        val offer = offer(group = true)
        val other = user()
        contacts.addContact(offer.owner.id!!, other.id!!)
        categories.addMember(offer.owner.id!!, offer.categoryId!!, other.id!!)
        val draft = ready(offer)
        val winner = bookings.book(BookSlotCommand(other.id!!, offer.ids[0]))
        expect(BusinessError.SLOT_TAKEN) { submit(offer, draft) }
        assertThat(requests.current(offer.booker.id!!)).isEqualTo(draft)
        assertThat(bookingRepository.findAllBySlotId(offer.ids[0]).map { it.id }).containsExactly(winner)
    }

    @Test
    fun `limit and visibility are checked again on submission`() {
        val offer = offer(2, group = true)
        val draft = ready(offer)
        val otherBooking = bookings.book(BookSlotCommand(offer.booker.id!!, offer.ids[1]))
        expect(BusinessError.BOOKING_LIMIT_REACHED) { submit(offer, draft) }
        bookings.cancel(otherBooking, offer.booker.id!!)
        categories.removeMember(offer.owner.id!!, offer.categoryId!!, offer.booker.id!!)
        expect(BusinessError.SLOT_NOT_AVAILABLE) { submit(offer, draft) }
        assertThat(requests.current(offer.booker.id!!)).isEqualTo(draft)
    }

    @Test
    fun `time is rechecked at submission even for a live draft`() {
        val offer = offer()
        clock.now = slotRepository.findById(offer.ids[0]).orElseThrow().startAt.minusSeconds(60)
        val draft = ready(offer)
        clock.now = clock.now.plusSeconds(60)
        expect(BusinessError.BOOKING_STARTED) { submit(offer, draft) }
        assertThat(bookingRepository.findAllBySlotId(offer.ids[0])).isEmpty()
    }

    @Test
    fun `expiry cancellation and ownership protect requests and their buttons`() {
        val offer = offer()
        val outsider = user().id!!
        val draft = ready(offer)
        expect(BusinessError.BOOKING_DRAFT_NOT_FOUND) { requests.submit(outsider, draft.id, draft.revision, null) }
        clock.now = draft.expiresAt
        expect(BusinessError.DRAFT_EXPIRED) { submit(offer, draft) }
        val fresh = requests.start(offer.booker.id!!, offer.ids[0], null)
        expect(BusinessError.STALE_DRAFT) { submit(offer, draft) }
        val canceled = requests.cancel(offer.booker.id!!, fresh.id, fresh.revision, null)
        assertThat(canceled.step).isEqualTo(BookingRequestStep.CANCELED)
        assertThat(canceled.comment).isNull()
        assertThat(bookingRepository.findAllBySlotId(offer.ids[0])).isEmpty()
    }

    @Test
    fun `replayed inputs do not fill a different field and invalid values leave the draft unchanged`() {
        val offer = offer()
        val first = requests.start(offer.booker.id!!, offer.ids[0], 1)
        expect(BusinessError.INVALID_BOOKING_TEXT) {
            requests.accept(offer.booker.id!!, first.id, first.revision, 2, "x".repeat(1001))
        }
        assertThat(requests.current(offer.booker.id!!)).isEqualTo(first)
        val place = requests.accept(offer.booker.id!!, first.id, first.revision, 3, "  Текст  ")
        assertThat(place.comment).isEqualTo("Текст")
        assertThat(requests.accept(offer.booker.id!!, first.id, first.revision, 3, "Текст")).isEqualTo(place)
        expect(BusinessError.INVALID_BOOKING_TEXT) {
            requests.accept(offer.booker.id!!, place.id, place.revision, 4, "a\u0000b")
        }
        expect(BusinessError.INVALID_BOOKING_TEXT) {
            requests.accept(offer.booker.id!!, place.id, place.revision, 4, "x".repeat(201))
        }
        val review = requests.accept(offer.booker.id!!, place.id, place.revision, 5, null)
        assertThat(review.proposedPlace).isNull()
        assertThat(requests.accept(offer.booker.id!!, place.id, place.revision, 5, null)).isEqualTo(review)
    }

    @Test
    fun `publication and booking cannot start two active Telegram dialogues concurrently`() {
        val offer = offer()
        val actor = offer.booker.id!!
        val results = concurrent(
            { coordinator.startPublication(actor, 1) },
            { coordinator.startBooking(actor, offer.ids[0], 2) }
        )
        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        val error = results.single { it.isFailure }.exceptionOrNull()
        assertThat(error).isInstanceOf(BusinessException::class.java)
        assertThat((error as BusinessException).error)
            .isIn(BusinessError.PUBLICATION_IN_PROGRESS, BusinessError.BOOKING_IN_PROGRESS)
        val count = listOf(
            publications.current(actor)?.let { it.active && it.expiresAt.isAfter(clock.now) } == true,
            requests.current(actor)?.activeAt(clock.now) == true
        ).count { it }
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `a different slot does not replace an unfinished request`() {
        val offer = offer(2)
        val draft = ready(offer)
        expect(BusinessError.BOOKING_IN_PROGRESS) { requests.start(offer.booker.id!!, offer.ids[1], null) }
        assertThat(requests.current(offer.booker.id!!)).isEqualTo(draft)
    }
}
