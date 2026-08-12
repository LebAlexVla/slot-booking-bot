package ru.lebalexvla.slotbookingbot.telegram.transport

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.EntityType
import org.telegram.telegrambots.meta.api.objects.message.InaccessibleMessage
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.MessageEntity
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActorProfile
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramEvent

class TelegramUpdateMapperTest {

    private val mapper = TelegramUpdateMapper()

    @Test
    fun `maps private command with bot mention and arguments`() {
        val command = "/START@SlotBookingBot"
        val update = messageUpdate(
            text = "$command   invite-token",
            entities = listOf(
                MessageEntity(
                    EntityType.BOTCOMMAND,
                    0,
                    command.length
                )
            )
        )

        assertThat(mapper.map(update)).isEqualTo(
            TelegramEvent.Command(
                updateId = update.updateId,
                actor = expectedProfile(),
                name = "start",
                arguments = "invite-token"
            )
        )
    }

    @Test
    fun `does not infer command without Telegram command entity`() {
        val update = messageUpdate(text = "/start")

        assertThat(mapper.map(update)).isEqualTo(
            TelegramEvent.Text(
                updateId = update.updateId,
                actor = expectedProfile(),
                text = "/start"
            )
        )
    }

    @Test
    fun `maps ordinary private text`() {
        val update = messageUpdate(text = "Available tomorrow")

        assertThat(mapper.map(update)).isEqualTo(
            TelegramEvent.Text(
                updateId = update.updateId,
                actor = expectedProfile(),
                text = "Available tomorrow"
            )
        )
    }

    @Test
    fun `maps callback actor from query sender`() {
        val callbackActor = telegramUser(
            id = 321L,
            firstName = "Maria",
            username = "maria"
        )
        val messageAuthor = telegramUser(
            id = 999L,
            firstName = "Slot bot",
            username = "slot_bot",
            isBot = true
        )
        val callbackMessage = message(
            text = "Menu",
            from = messageAuthor
        )
        val update = Update().apply {
            updateId = 2
            callbackQuery = CallbackQuery().apply {
                id = "callback-42"
                from = callbackActor
                message = callbackMessage
                data = "menu:home"
            }
        }

        assertThat(mapper.map(update)).isEqualTo(
            TelegramEvent.Callback(
                updateId = update.updateId,
                actor = TelegramActorProfile(
                    telegramUserId = 321L,
                    chatId = 456L,
                    username = "maria",
                    firstName = "Maria",
                    lastName = "Test"
                ),
                callbackQueryId = "callback-42",
                data = "menu:home"
            )
        )
    }

    @Test
    fun `maps private callback with inaccessible message`() {
        val update = Update().apply {
            updateId = 3
            callbackQuery = CallbackQuery().apply {
                id = "callback-42"
                from = telegramUser()
                message = InaccessibleMessage.builder()
                    .chat(Chat(456L, "private"))
                    .messageId(10)
                    .date(0)
                    .build()
                data = "menu:home"
            }
        }

        assertThat(mapper.map(update)).isEqualTo(
            TelegramEvent.Callback(
                updateId = update.updateId,
                actor = expectedProfile(),
                callbackQueryId = "callback-42",
                data = "menu:home"
            )
        )
    }

    @Test
    fun `maps private callback without data for acknowledgement`() {
        val update = Update().apply {
            updateId = 4
            callbackQuery = CallbackQuery().apply {
                id = "callback-42"
                from = telegramUser()
                message = message(text = "Menu")
            }
        }

        assertThat(mapper.map(update)).isEqualTo(
            TelegramEvent.Callback(
                updateId = update.updateId,
                actor = expectedProfile(),
                callbackQueryId = "callback-42",
                data = null
            )
        )
    }

    @Test
    fun `ignores events outside private chats`() {
        val groupMessage = messageUpdate(
            text = "/start",
            chat = Chat(-100L, "group")
        )
        val groupCallback = Update().apply {
            updateId = 3
            callbackQuery = CallbackQuery().apply {
                id = "callback-42"
                from = telegramUser()
                message = message(
                    text = "Menu",
                    chat = Chat(-100L, "supergroup")
                )
                data = "menu:home"
            }
        }

        assertThat(mapper.map(groupMessage)).isNull()
        assertThat(mapper.map(groupCallback)).isNull()
    }

    @Test
    fun `ignores malformed and bot-authored events`() {
        val callbackWithoutMessage = Update().apply {
            updateId = 4
            callbackQuery = CallbackQuery().apply {
                id = "callback-42"
                from = telegramUser()
                data = "menu:home"
            }
        }
        val messageFromBot = messageUpdate(
            text = "/start",
            from = telegramUser(isBot = true)
        )

        assertThat(mapper.map(Update())).isNull()
        assertThat(mapper.map(callbackWithoutMessage)).isNull()
        assertThat(mapper.map(messageFromBot)).isNull()
    }

    @Test
    fun `ignores message with malformed command entity`() {
        val update = messageUpdate(
            text = "/start",
            entities = listOf(
                MessageEntity(
                    EntityType.BOTCOMMAND,
                    0,
                    100
                )
            )
        )

        assertThat(mapper.map(update)).isNull()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `ignores message with null entity`() {
        val entities = listOf<MessageEntity?>(null) as List<MessageEntity>

        assertThat(
            mapper.map(
                messageUpdate(
                    text = "/start",
                    entities = entities
                )
            )
        ).isNull()
    }

    @Test
    fun `ignores command with empty normalized name`() {
        val update = messageUpdate(
            text = "/",
            entities = listOf(
                MessageEntity(
                    EntityType.BOTCOMMAND,
                    0,
                    1
                )
            )
        )

        assertThat(mapper.map(update)).isNull()
    }

    @Test
    fun `ignores empty text message`() {
        assertThat(mapper.map(messageUpdate(text = ""))).isNull()
    }

    private fun messageUpdate(
        text: String,
        from: User = telegramUser(),
        chat: Chat = Chat(456L, "private"),
        entities: List<MessageEntity>? = null
    ) = Update().apply {
        updateId = 1
        message = message(
            text = text,
            from = from,
            chat = chat,
            entities = entities
        )
    }

    private fun message(
        text: String,
        from: User = telegramUser(),
        chat: Chat = Chat(456L, "private"),
        entities: List<MessageEntity>? = null
    ): Message {
        val builder = Message.builder()
            .messageId(1)
            .date(1)
            .from(from)
            .chat(chat)
            .text(text)

        if (entities != null) {
            builder.entities(entities)
        }

        return builder.build()
    }

    private fun telegramUser(
        id: Long = 123L,
        firstName: String = "Alex",
        username: String = "alex",
        isBot: Boolean = false
    ) = User(id, firstName, isBot).apply {
        userName = username
        lastName = "Test"
    }

    private fun expectedProfile() = TelegramActorProfile(
        telegramUserId = 123L,
        chatId = 456L,
        username = "alex",
        firstName = "Alex",
        lastName = "Test"
    )
}
