package ru.lebalexvla.slotbookingbot.telegram.transport



interface TelegramGateway {
    fun sendMessage(message: OutgoingTelegramMessage)
    fun acknowledgeCallback(callbackQueryId: String)
    fun getBotUsername(): String
}

data class OutgoingTelegramMessage(
    val chatId: Long,
    val text: String,
    val keyboard: List<List<TelegramButton>> = emptyList()
) {
    init {
        require(text.isNotBlank() && text.length <= 4096) { "Invalid Telegram message text" }
        require(keyboard.all { it.isNotEmpty() }) { "Keyboard rows must not be empty" }
    }
}

data class TelegramButton(val text: String, val callbackData: String) {
    init {
        require(text.isNotBlank()) { "Button text must not be blank" }
        require(callbackData.toByteArray(Charsets.UTF_8).size in 1..64) { "Invalid callback data size" }
    }
}
