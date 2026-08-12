package ru.lebalexvla.slotbookingbot.telegram.slot

import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.lebalexvla.slotbookingbot.common.ListPage
import ru.lebalexvla.slotbookingbot.slot.SlotSetTargetType
import ru.lebalexvla.slotbookingbot.telegram.common.CompactId

class SlotActionTest {
    private val id = UUID.randomUUID()
    private val ref = DraftReference(UUID.randomUUID(), Int.MAX_VALUE)

    @Test
    fun `published buttons retain their payload after separating publication from browsing`() {
        val draft = DraftReference(UUID(0, 0), 7)
        val target = UUID(0, 1)
        val fixtures = mapOf(
            "s:new" to SlotAction.Start,
            "s:o:3" to SlotAction.Listing(SlotListMode.OWN, 3),
            "s:v:a:AAAAAAAAAAAAAAAAAAAAAQ:2" to SlotAction.Card(SlotListMode.AVAILABLE, target, 2),
            "s:t:AAAAAAAAAAAAAAAAAAAAAA:7:u:AAAAAAAAAAAAAAAAAAAAAQ" to
                SlotAction.SelectTarget(draft, SlotSetTargetType.USER, target),
            "s:p:AAAAAAAAAAAAAAAAAAAAAA:7" to SlotAction.Confirm(draft)
        )
        fixtures.forEach { (payload, action) ->
            assertThat(SlotAction.parse(payload)).isEqualTo(action)
            assertThat(action.encode()).isEqualTo(payload)
        }
    }

    @Test
    fun `all callbacks round trip within Telegram payload budget`() {
        val actions = listOf(
            SlotAction.Start, SlotAction.Resume,
            SlotAction.Listing(SlotListMode.AVAILABLE, ListPage.MAX_NUMBER),
            SlotAction.Listing(SlotListMode.OWN),
            SlotAction.Card(SlotListMode.OWN, id, ListPage.MAX_NUMBER),
            SlotAction.Targets(ref, SlotSetTargetType.USER, ListPage.MAX_NUMBER),
            SlotAction.Targets(ref, SlotSetTargetType.CATEGORY),
            SlotAction.SelectTarget(ref, SlotSetTargetType.USER, id),
            SlotAction.SelectTarget(ref, SlotSetTargetType.CATEGORY, id),
            SlotAction.Skip(ref), SlotAction.EditIntervals(ref), SlotAction.Confirm(ref), SlotAction.Cancel(ref)
        )
        actions.forEach {
            assertThat(it.encode().toByteArray().size).isLessThanOrEqualTo(64)
            assertThat(SlotAction.parse(it.encode())).isEqualTo(it)
        }
    }

    @Test
    fun `malformed callbacks fail without throwing`() {
        val encoded = CompactId.encode(id)
        listOf(null, "", "s", "s:", "s:new:extra", "s:a:-1", "s:o:100000000", "s:v:x:$encoded:0",
            "s:p:$encoded:-1", "s:p:bad:1", "s:t:$encoded:1:z:$encoded",
            "s:p:$encoded:2147483648", "s:" + "я".repeat(33), "d:new")
            .forEach { assertThat(SlotAction.parse(it)).describedAs(it).isNull() }
    }
}
