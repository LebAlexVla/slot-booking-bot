package ru.lebalexvla.slotbookingbot.telegram

import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import ru.lebalexvla.slotbookingbot.category.CategoryService
import ru.lebalexvla.slotbookingbot.common.BusinessError
import ru.lebalexvla.slotbookingbot.common.BusinessException
import ru.lebalexvla.slotbookingbot.common.ListPage
import ru.lebalexvla.slotbookingbot.contact.ContactService
import ru.lebalexvla.slotbookingbot.telegram.common.CompactId
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActor
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActorProfile
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActorResolver
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramEvent
import ru.lebalexvla.slotbookingbot.telegram.directory.DirectoryAction
import ru.lebalexvla.slotbookingbot.telegram.directory.TelegramCategoryMenu
import ru.lebalexvla.slotbookingbot.telegram.directory.TelegramContactMenu
import ru.lebalexvla.slotbookingbot.telegram.directory.TelegramDirectoryHandler
import ru.lebalexvla.slotbookingbot.telegram.slot.publication.TelegramPublicationDialog
import ru.lebalexvla.slotbookingbot.telegram.slot.TelegramSlotMenu
import ru.lebalexvla.slotbookingbot.telegram.transport.RecordingTelegramGateway
import ru.lebalexvla.slotbookingbot.telegram.transport.TelegramApiRequestException
import ru.lebalexvla.slotbookingbot.user.UserSummary

class TelegramEventHandlerTest {
    private val actorResolver: TelegramActorResolver = mock()
    private val contacts: ContactService = mock()
    private val categories: CategoryService = mock()
    private val gateway = RecordingTelegramGateway()
    private val profile = TelegramActorProfile(123L, 456L, "alex", "Alex", null)
    private val actor = TelegramActor(UUID.randomUUID(), profile.chatId)
    private val handler = TelegramEventHandler(
        actorResolver, gateway,
        TelegramRouter(
            TelegramDirectoryHandler(TelegramContactMenu(contacts, gateway), TelegramCategoryMenu(categories)),
            mock<TelegramSlotMenu>(), mock<TelegramPublicationDialog>()
        )
    )

    @BeforeEach
    fun setUp() {
        whenever(actorResolver.resolveAndSync(profile)).thenReturn(actor)
    }

    @Test
    fun `start synchronizes actor and displays working menu`() {
        command("start")
        verify(actorResolver).resolveAndSync(profile)
        assertThat(gateway.sentMessages.single().keyboard.flatten().map { DirectoryAction.parse(it.callbackData) })
            .contains(DirectoryAction.Contacts(), DirectoryAction.Categories(), DirectoryAction.Invite)
    }

    @Test
    fun `unknown command and ordinary text provide navigation`() {
        command("unknown")
        handler.handle(TelegramEvent.Text(profile, "Hello"))
        assertThat(gateway.sentMessages).hasSize(2).allSatisfy {
            assertThat(it.keyboard).isNotEmpty()
            assertThat(it.chatId).isEqualTo(profile.chatId)
        }
        verifyNoInteractions(contacts, categories)
    }

    @Test
    fun `malformed callback is acknowledged and does not call services`() {
        callback("+:" + UUID.randomUUID())
        assertThat(gateway.answeredCallbacks).containsExactly("callback-42")
        assertThat(gateway.sentMessages.single().text).contains("недействительна")
        verifyNoInteractions(contacts, categories)
    }

    @Test
    fun `callback is acknowledged before synchronization failure and user gets safe response`() {
        whenever(actorResolver.resolveAndSync(profile)).thenAnswer {
            assertThat(gateway.answeredCallbacks).containsExactly("callback-42")
            throw IllegalStateException("Database details")
        }
        callback(DirectoryAction.Menu.encode())
        assertThat(gateway.sentMessages.single().text).contains("Попробуйте").doesNotContain("Database")
    }

    @Test
    fun `callback acknowledgement failure does not block the action`() {
        gateway.callbackFailure = TelegramApiRequestException(TelegramApiException("Unavailable"))
        callback(DirectoryAction.Menu.encode())
        verify(actorResolver).resolveAndSync(profile)
        assertThat(gateway.sentMessages.single().keyboard).isNotEmpty()
    }

    @Test
    fun `invite works for a user without username and requires confirmation`() {
        command("invite")
        val link = gateway.sentMessages.last().text
        assertThat(link).contains("https://t.me/slot_booking_test_bot?start=contact_" + CompactId.encode(actor.userId))

        val contact = UserSummary(UUID.randomUUID(), "Мария", null)
        whenever(contacts.getCandidate(actor.userId, contact.id)).thenReturn(contact)
        command("start", "contact_" + CompactId.encode(contact.id))
        assertThat(gateway.sentMessages.last().keyboard.flatten().first().callbackData)
            .isEqualTo(DirectoryAction.AddContact(contact.id).encode())
        assertThat(gateway.sentMessages.last().text).contains("Мария")
    }

    @Test
    fun `contact callback uses authenticated actor and returns refreshed list`() {
        val contact = UserSummary(UUID.randomUUID(), "Мария", null)
        whenever(contacts.addContact(actor.userId, contact.id)).thenReturn(contact)
        whenever(contacts.getContacts(actor.userId, 0)).thenReturn(ListPage(listOf(contact), 0, false))
        callback(DirectoryAction.AddContact(contact.id).encode())
        verify(contacts).addContact(actor.userId, contact.id)
        assertThat(gateway.sentMessages.last().text).contains("Мария", "теперь в ваших контактах")
    }

    @Test
    fun `business rejection is translated to actionable response`() {
        val id = UUID.randomUUID()
        whenever(categories.getCategory(actor.userId, id))
            .thenThrow(BusinessException(BusinessError.CATEGORY_NOT_FOUND))
        callback(DirectoryAction.Category(id).encode())
        assertThat(gateway.sentMessages.single().text).contains("Категория недоступна")
    }

    @Test
    fun `invalid deep link cannot mutate directory`() {
        command("start", "contact_bad")
        assertThat(gateway.sentMessages.single().text).contains("Ссылка недействительна")
        verifyNoInteractions(contacts, categories)
    }

    @Test
    fun `delivery failure propagates to consumer boundary`() {
        gateway.deliveryFailure = TelegramApiRequestException(TelegramApiException("Unavailable"))
        assertThatThrownBy { command("menu") }.isInstanceOf(TelegramApiRequestException::class.java)
    }

    private fun command(name: String, arguments: String? = null) =
        handler.handle(TelegramEvent.Command(profile, name, arguments))

    private fun callback(data: String) =
        handler.handle(TelegramEvent.Callback(profile, "callback-42", data))
}
