package ru.lebalexvla.slotbookingbot.telegram.booking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.lebalexvla.slotbookingbot.booking.BookingDecision
import ru.lebalexvla.slotbookingbot.booking.BookingList
import ru.lebalexvla.slotbookingbot.booking.BookingStatus
import ru.lebalexvla.slotbookingbot.common.ListPage
import ru.lebalexvla.slotbookingbot.telegram.common.CompactId
import ru.lebalexvla.slotbookingbot.telegram.common.DraftReference
import java.util.UUID

class BookingActionTest {
    @Test
    fun `actions round trip within Telegram size constraints`() {
        val id = UUID.randomUUID()
        val ref = DraftReference(id, Int.MAX_VALUE)
        val actions = listOf(
            BookingAction.Begin(id), BookingAction.Resume, BookingAction.Skip(ref),
            BookingAction.Submit(ref), BookingAction.Discard(ref),
            BookingAction.Listing(BookingList.MINE, ListPage.MAX_NUMBER),
            BookingAction.Card(id, BookingList.INCOMING, ListPage.MAX_NUMBER)
        ) + BookingDecision.entries.map {
            BookingAction.Decide(id, it, BookingStatus.CONFIRMED, BookingList.INCOMING, ListPage.MAX_NUMBER)
        }
        actions.forEach {
            assertThat(it.encode().toByteArray().size).isLessThanOrEqualTo(64)
            assertThat(BookingAction.parse(it.encode())).isEqualTo(it)
        }
    }

    @Test
    fun `malformed or forged callback fields are rejected`() {
        val id = CompactId.encode(UUID.randomUUID())
        listOf(null, "", "b", "b:r:extra", "b:n:bad", "b:l:z:0", "b:l:m:-1",
            "b:k:$id:-1", "b:s:$id:2147483648", "b:v:$id:m:10001",
            "b:d:$id:OTHER:PENDING:m:0", "b:d:$id:CANCEL:CANCELED:m:0", "b:" + "я".repeat(33))
            .forEach { assertThat(BookingAction.parse(it)).describedAs(it).isNull() }
    }
}
