package ru.lebalexvla.slotbookingbot.telegram.directory

import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.lebalexvla.slotbookingbot.telegram.common.CompactId
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramButton

class DirectoryActionTest {
    @Test
    fun `buttons sent before package refactoring keep their wire format`() {
        val category = UUID(0, 0)
        val user = UUID(0, 1)
        val fixtures = mapOf(
            "m" to DirectoryAction.Menu,
            "c:3" to DirectoryAction.Contacts(3),
            "v:AAAAAAAAAAAAAAAAAAAAAA:2" to DirectoryAction.Category(category, 2),
            "+:AAAAAAAAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAQ" to DirectoryAction.AddMember(category, user)
        )
        fixtures.forEach { (payload, action) ->
            assertThat(DirectoryAction.parse(payload)).isEqualTo(action)
            assertThat(action.encode()).isEqualTo(payload)
        }
    }

    @Test
    fun `all actions round trip within Telegram callback limit`() {
        val category = UUID.randomUUID()
        val user = UUID.randomUUID()
        val actions = listOf(
            DirectoryAction.Menu, DirectoryAction.Help, DirectoryAction.Invite, DirectoryAction.NewCategory,
            DirectoryAction.Contacts(42), DirectoryAction.Categories(5), DirectoryAction.AddContact(user),
            DirectoryAction.Category(category, 3), DirectoryAction.Candidates(category, 2),
            DirectoryAction.AddMember(category, user), DirectoryAction.RemoveMember(category, user),
            DirectoryAction.ConfirmDeletion(category), DirectoryAction.DeleteCategory(category)
        )
        actions.forEach {
            assertThat(it.encode().toByteArray(Charsets.UTF_8).size).isLessThanOrEqualTo(64)
            assertThat(DirectoryAction.parse(it.encode())).isEqualTo(it)
        }
    }

    @Test
    fun `rejects malformed unsupported and oversized payloads`() {
        val id = CompactId.encode(UUID.randomUUID())
        listOf(null, "", "unknown", "m:extra", "a:bad", "x", "c:-1", "c:2147483648",
            "v:$id:bad", "+:$id", "+:$id:$id:extra", "p:$id:10001", "я".repeat(33))
            .forEach { assertThat(DirectoryAction.parse(it)).describedAs("payload %s", it).isNull() }
    }

    @Test
    fun `compact IDs are canonical url safe and lossless`() {
        repeat(100) {
            val id = UUID.randomUUID()
            val encoded = CompactId.encode(id)
            assertThat(encoded).matches("[A-Za-z0-9_-]{22}")
            assertThat(CompactId.decode(encoded)).isEqualTo(id)
        }
        listOf("", UUID.randomUUID().toString(), "=".repeat(22), "A".repeat(21) + "B")
            .forEach { assertThat(CompactId.decode(it)).isNull() }
    }

    @Test
    fun `button limit counts UTF8 bytes`() {
        TelegramButton("Кнопка", "я".repeat(32))
        assertThatThrownBy { TelegramButton("Кнопка", "я".repeat(33)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
