package ru.lebalexvla.slotbookingbot.telegram.booking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import ru.lebalexvla.slotbookingbot.booking.*
import ru.lebalexvla.slotbookingbot.booking.request.BookingRequestStep
import ru.lebalexvla.slotbookingbot.slot.SlotStatus
import ru.lebalexvla.slotbookingbot.telegram.slot.SlotAction
import ru.lebalexvla.slotbookingbot.telegram.transport.*

class TelegramBookingIntegrationTest : BookingWorkflowFixture() {
    @Autowired private lateinit var consumer: TelegramUpdateConsumer
    private fun client() = TelegramTestClient(consumer, gateway)

    @Test
    fun `two users book confirm and cancel entirely through Telegram updates`() {
        val offer = offer(group = true)
        val bot = client()
        val available = bot.command(offer.booker, "/slots")
        val slot = bot.click(offer.booker, available.buttonAction<SlotAction.Card>())
        bot.click(offer.booker, slot.buttonAction<BookingAction.Begin>())
        val comment = bot.textUpdate(offer.booker, "Секретный комментарий")
        assertThat(bot.consume(comment).text).contains("Предложите своё место")
        assertThat(bot.consume(comment).text).contains("Предложите своё место")
        assertThat(requests.current(offer.booker.id!!)!!.proposedPlace).isNull()
        val preview = bot.text(offer.booker, "Библиотека")
        assertThat(preview.text).contains("Проверьте заявку", "Секретный комментарий", "Библиотека")
        val pending = bot.click(offer.booker, preview.buttonAction<BookingAction.Submit>())
        val cancelPending = pending.buttonAction<BookingAction.Decide>()
        val id = cancelPending.id
        assertThat(pending.text).contains("Ожидает подтверждения", "Альтернативное место ещё не согласовано")
        assertThat(bot.command(offer.booker, "/bookings").text).contains("Ожидает подтверждения")
        assertThat(bot.click(offer.booker, BookingAction.Decide(
            id, BookingDecision.CONFIRM, BookingStatus.PENDING, BookingList.MINE
        )).text).contains("Бронь недоступна")
        val inbox = bot.command(offer.owner, "/requests")
        val ownerCard = bot.click(offer.owner, inbox.buttonAction<BookingAction.Card>())
        val confirm = ownerCard.buttonAction<BookingAction.Decide> { it.decision == BookingDecision.CONFIRM }
        assertThat(bot.click(offer.owner, confirm).text).contains("Подтверждена")
        assertThat(bot.click(offer.owner, confirm).text).contains("Подтверждена")
        val staleCancel = bot.click(offer.booker, cancelPending)
        assertThat(staleCancel.text).contains("Статус брони изменился", "Подтверждена")
        val canceled = bot.click(offer.booker, staleCancel.buttonAction<BookingAction.Decide>())
        assertThat(canceled.text).contains("Отменена")
        val outsider = user()
        assertThat(bot.click(outsider, BookingAction.Card(id)).text)
            .contains("Бронь недоступна").doesNotContain("Секретный комментарий", "Библиотека")
        assertThat(bot.command(offer.owner, "/requests").text).contains("Отменена")
        assertThat(slotRepository.findById(offer.ids[0]).orElseThrow().status).isEqualTo(SlotStatus.AVAILABLE)
    }

    @Test
    fun `rejection frees slot and repeating an old decision does not damage its new booking`() {
        val offer = offer()
        val bot = client()
        fun request(): OutgoingTelegramMessage {
            bot.click(offer.booker, BookingAction.Begin(offer.ids[0]))
            bot.command(offer.booker, "/skip")
            val preview = bot.command(offer.booker, "/skip")
            return bot.click(offer.booker, preview.buttonAction<BookingAction.Submit>())
        }
        val first = request().buttonAction<BookingAction.Decide>().id
        val ownerCard = bot.click(offer.owner, BookingAction.Card(first, BookingList.INCOMING))
        val reject = ownerCard.buttonAction<BookingAction.Decide> { it.decision == BookingDecision.REJECT }
        assertThat(bot.click(offer.owner, reject).text).contains("Отклонена")
        val next = request()
        assertThat(bot.click(offer.owner, reject).text).contains("Отклонена")
        assertThat(queries.card(offer.booker.id!!, next.buttonAction<BookingAction.Decide>().id).status)
            .isEqualTo(BookingStatus.PENDING)
        assertThat(bot.click(offer.booker, next.buttonAction<BookingAction.Decide>()).text).contains("Отменена")
    }

    @Test
    fun `delivery failure and a new adapter preserve submission result without a duplicate`() {
        val offer = offer()
        val bot = client()
        bot.click(offer.booker, BookingAction.Begin(offer.ids[0]))
        bot.command(offer.booker, "/skip")
        bot.command(offer.booker, "/menu")
        val resumed = client().command(offer.booker, "/draft")
        assertThat(resumed.text).contains("Предложите своё место")
        val review = bot.click(offer.booker, resumed.buttonAction<BookingAction.Skip>())
        val submit = bot.callbackUpdate(offer.booker, review.buttonAction<BookingAction.Submit>())
        gateway.deliveryFailure = TelegramApiRequestException(TelegramApiException("Unavailable"))
        bot.deliver(submit)
        val result = requests.current(offer.booker.id!!)!!
        assertThat(result.step).isEqualTo(BookingRequestStep.SUBMITTED)
        gateway.deliveryFailure = null
        assertThat(client().consume(submit).text).contains("Ожидает подтверждения")
        assertThat(bookingRepository.findAllBySlotId(offer.ids[0])).hasSize(1)
        assertThat(bot.command(offer.booker, "/booking_draft").text).contains("Ожидает подтверждения")
    }

    @Test
    fun `only the active dialogue consumes text skip cancel and draft commands`() {
        val offer = offer()
        val bot = client()
        bot.command(offer.booker, "/new_slots")
        assertThat(bot.click(offer.booker, BookingAction.Begin(offer.ids[0])).text).contains("завершите создание слотов")
        bot.command(offer.booker, "/cancel")
        bot.click(offer.booker, BookingAction.Begin(offer.ids[0]))
        assertThat(bot.command(offer.booker, "/new_slots").text).contains("отправьте текущую заявку")
        assertThat(bot.command(offer.booker, "/draft").text).contains("Добавьте комментарий")
        assertThat(bot.text(offer.booker, "x".repeat(1001)).text).contains("Комментарий — до 1000")
        val place = bot.text(offer.booker, "x".repeat(1000))
        assertThat(place.text).contains("Предложите своё место")
        val preview = bot.text(offer.booker, "я".repeat(200))
        assertThat(preview.text.length).isLessThanOrEqualTo(4096)
        assertThat(bot.command(offer.booker, "/cancel").text).contains("Создание заявки отменено")
        assertThat(bot.command(offer.booker, "/new_slots").text).contains("Кому доступны слоты")
    }

    @Test
    fun `stale slot and exhausted limit are understandable and preserve unsent draft`() {
        val offer = offer(2)
        val bot = client()
        bot.click(offer.booker, BookingAction.Begin(offer.ids[0]))
        bot.command(offer.booker, "/skip")
        val preview = bot.command(offer.booker, "/skip")
        val other = bookings.book(BookSlotCommand(offer.booker.id!!, offer.ids[1]))
        assertThat(bot.click(offer.booker, preview.buttonAction<BookingAction.Submit>()).text).contains("Лимит ваших бронирований")
        assertThat(requests.current(offer.booker.id!!)!!.step).isEqualTo(BookingRequestStep.REVIEW)
        bookings.cancel(other, offer.booker.id!!)
        assertThat(bot.click(offer.booker, preview.buttonAction<BookingAction.Submit>()).text).contains("Ожидает подтверждения")
    }

    @Test
    fun `incoming pages navigate and expired request offers rejection without confirmation`() {
        val offer = offer(10, 10)
        val ids = offer.ids.map { bookings.book(BookSlotCommand(offer.booker.id!!, it)) }
        val bot = client()
        val first = bot.command(offer.owner, "/requests")
        val second = bot.click(offer.owner, first.buttonAction<BookingAction.Listing> { it.page == 1 })
        assertThat(second.text).contains("страница 2")
        assertThat(second.keyboard.flatten().mapNotNull { BookingAction.parse(it.callbackData) }
            .filterIsInstance<BookingAction.Card>()).hasSize(2)
        clock.now = slotRepository.findById(offer.ids[0]).orElseThrow().startAt
        val expired = bot.click(offer.owner, BookingAction.Card(ids[0], BookingList.INCOMING))
        assertThat(expired.text).contains("Срок заявки истёк")
        val decisions = expired.keyboard.flatten().mapNotNull { BookingAction.parse(it.callbackData) }
            .filterIsInstance<BookingAction.Decide>()
        assertThat(decisions.map { it.decision }).containsExactly(BookingDecision.REJECT)
        bot.click(offer.owner, decisions.single())
        assertThat(slotRepository.findById(offer.ids[0]).orElseThrow().status).isEqualTo(SlotStatus.EXPIRED)
    }
}
