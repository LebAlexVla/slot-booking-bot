package ru.lebalexvla.slotbookingbot.telegram.transport



class RecordingTelegramGateway : TelegramGateway {
    val sentMessages = mutableListOf<OutgoingTelegramMessage>()
    val answeredCallbacks = mutableListOf<String>()
    var callbackFailure: TelegramApiRequestException? = null
    var deliveryFailure: TelegramApiRequestException? = null

    override fun getBotUsername() = "slot_booking_test_bot"

    override fun sendMessage(message: OutgoingTelegramMessage) {
        deliveryFailure?.let { throw it }
        sentMessages += message
    }

    override fun acknowledgeCallback(callbackQueryId: String) {
        answeredCallbacks += callbackQueryId
        callbackFailure?.let { throw it }
    }
}
