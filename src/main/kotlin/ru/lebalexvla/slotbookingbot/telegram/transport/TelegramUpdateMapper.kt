package ru.lebalexvla.slotbookingbot.telegram.transport

import java.util.Locale
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramActorProfile
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramEvent

@Component
class TelegramUpdateMapper {

    fun map(update: Update): TelegramEvent? {
        val event = if (update.callbackQuery != null) mapCallback(update.callbackQuery)
            else update.message?.let(::mapMessage)
        return when (event) {
            is TelegramEvent.Command -> event.copy(updateId = update.updateId)
            is TelegramEvent.Text -> event.copy(updateId = update.updateId)
            is TelegramEvent.Callback -> event.copy(updateId = update.updateId)
            null -> null
        }
    }

    private fun mapMessage(message: Message): TelegramEvent? {
        val actor = mapActor(
            user = message.from,
            chat = message.chat
        ) ?: return null
        val text = message.text?.takeIf(String::isNotEmpty) ?: return null
        val extractedCommand = message.extractCommand() ?: return null
        val command = extractedCommand.command

        if (command != null) {
            val name = command
                .removePrefix("/")
                .substringBefore('@')
                .lowercase(Locale.ROOT)
                .takeIf(String::isNotEmpty)
                ?: return null

            return TelegramEvent.Command(
                actor = actor,
                name = name,
                arguments = text
                    .drop(command.length)
                    .trim()
                    .takeIf(String::isNotEmpty)
            )
        }

        return TelegramEvent.Text(
            actor = actor,
            text = text
        )
    }

    private fun mapCallback(
        callbackQuery: CallbackQuery
    ): TelegramEvent? {
        val actor = mapActor(
            user = callbackQuery.from,
            chat = callbackQuery.message?.chat
        ) ?: return null
        val callbackQueryId = callbackQuery.id
            ?.takeIf(String::isNotBlank)
            ?: return null

        return TelegramEvent.Callback(
            actor = actor,
            callbackQueryId = callbackQueryId,
            data = callbackQuery.data
        )
    }

    /**
     * Telegram Bots SDK 10.2.1 computes entity text lazily in getEntities().
     * Keep that SDK detail and malformed entity handling at the adapter boundary.
     */
    private fun Message.extractCommand(): CommandExtraction? =
        try {
            getEntities()
            CommandExtraction(command)
        } catch (_: NullPointerException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }

    private fun mapActor(
        user: User?,
        chat: Chat?
    ): TelegramActorProfile? {
        if (chat?.isUserChat != true || user == null) {
            return null
        }

        val telegramUserId: Long? = user.id
        val chatId: Long? = chat.id
        val firstName: String? = user.firstName
        val isBot: Boolean? = user.isBot

        if (isBot != false) {
            return null
        }

        return TelegramActorProfile(
            telegramUserId = telegramUserId ?: return null,
            chatId = chatId ?: return null,
            username = user.userName,
            firstName = firstName ?: return null,
            lastName = user.lastName
        )
    }

    private data class CommandExtraction(
        val command: String?
    )
}
