package ru.lebalexvla.slotbookingbot.telegram.transport

import org.assertj.core.api.Assertions.assertThat
import org.telegram.telegrambots.meta.api.objects.*
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message
import ru.lebalexvla.slotbookingbot.telegram.booking.BookingAction
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramAction
import ru.lebalexvla.slotbookingbot.telegram.directory.DirectoryAction
import ru.lebalexvla.slotbookingbot.telegram.slot.SlotAction
import ru.lebalexvla.slotbookingbot.user.User
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.telegram.telegrambots.meta.api.objects.User as TelegramUser

class TelegramTestClient(
    private val consumer: TelegramUpdateConsumer,
    private val gateway: RecordingTelegramGateway
) {
    companion object {
        private val updateIds = AtomicInteger(1_000_000)
    }

    fun command(user: User, text: String) = consume(textUpdate(user, text).apply {
        message.entities = listOf(MessageEntity(EntityType.BOTCOMMAND, 0, text.substringBefore(' ').length))
    })

    fun text(user: User, text: String) = consume(textUpdate(user, text))
    fun click(user: User, action: TelegramAction) = consume(callbackUpdate(user, action))

    fun textUpdate(user: User, text: String) = Update().apply {
        updateId = updateIds.incrementAndGet()
        message = message(user).apply { this.text = text }
    }

    fun callbackUpdate(user: User, action: TelegramAction) = Update().apply {
        updateId = updateIds.incrementAndGet()
        callbackQuery = CallbackQuery().apply {
            id = UUID.randomUUID().toString()
            from = telegramUser(user)
            message = message(user)
            data = action.encode()
        }
    }

    fun deliver(update: Update) = consumer.consume(update)

    fun consume(update: Update): OutgoingTelegramMessage {
        val before = gateway.sentMessages.size
        deliver(update)
        assertThat(gateway.sentMessages).hasSize(before + 1)
        return gateway.sentMessages.last()
    }

    private fun message(user: User) = Message().apply {
        messageId = 1
        date = 1
        from = telegramUser(user)
        chat = Chat(user.telegramChatId, "private")
    }

    private fun telegramUser(user: User) = TelegramUser.builder()
        .id(user.telegramUserId).isBot(false).firstName(checkNotNull(user.firstName)).build()
}

inline fun <reified T : TelegramAction> OutgoingTelegramMessage.buttonAction(
    predicate: (T) -> Boolean = { true }
): T = keyboard.flatten()
    .mapNotNull { BookingAction.parse(it.callbackData) ?: SlotAction.parse(it.callbackData) ?: DirectoryAction.parse(it.callbackData) }
    .filterIsInstance<T>().first(predicate)
