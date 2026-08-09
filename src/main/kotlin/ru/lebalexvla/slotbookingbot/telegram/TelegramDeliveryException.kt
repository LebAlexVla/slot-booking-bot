package ru.lebalexvla.slotbookingbot.telegram

import org.telegram.telegrambots.meta.exceptions.TelegramApiException

class TelegramDeliveryException(
    cause: TelegramApiException
) : RuntimeException("Failed to send Telegram message", cause)
