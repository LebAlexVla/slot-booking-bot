package ru.lebalexvla.slotbookingbot.telegram.directory

import java.util.UUID
import ru.lebalexvla.slotbookingbot.telegram.common.CallbackData
import ru.lebalexvla.slotbookingbot.telegram.common.CompactId
import ru.lebalexvla.slotbookingbot.telegram.common.TelegramAction

sealed interface DirectoryAction : TelegramAction {
    data object Menu : DirectoryAction
    data object Help : DirectoryAction
    data object Invite : DirectoryAction
    data object NewCategory : DirectoryAction
    data class Contacts(val page: Int = 0) : DirectoryAction
    data class Categories(val page: Int = 0) : DirectoryAction
    data class AddContact(val userId: UUID) : DirectoryAction
    data class Category(val id: UUID, val page: Int = 0) : DirectoryAction
    data class Candidates(val id: UUID, val page: Int = 0) : DirectoryAction
    data class AddMember(val categoryId: UUID, val userId: UUID) : DirectoryAction
    data class RemoveMember(val categoryId: UUID, val userId: UUID) : DirectoryAction
    data class ConfirmDeletion(val id: UUID) : DirectoryAction
    data class DeleteCategory(val id: UUID) : DirectoryAction

    override fun encode(): String = when (this) {
        Menu -> "m"
        Help -> "h"
        Invite -> "i"
        NewCategory -> "n"
        is Contacts -> "c:$page"
        is Categories -> "g:$page"
        is AddContact -> "a:${CompactId.encode(userId)}"
        is Category -> "v:${CompactId.encode(id)}:$page"
        is Candidates -> "p:${CompactId.encode(id)}:$page"
        is AddMember -> "+:${CompactId.encode(categoryId)}:${CompactId.encode(userId)}"
        is RemoveMember -> "-:${CompactId.encode(categoryId)}:${CompactId.encode(userId)}"
        is ConfirmDeletion -> "d:${CompactId.encode(id)}"
        is DeleteCategory -> "x:${CompactId.encode(id)}"
    }

    companion object {
        fun parse(data: String?): DirectoryAction? {
            val payload = CallbackData.parse(data) ?: return null
            return when (payload.size) {
                1 -> navigation(payload[0])
                2 -> singleArgument(payload)
                3 -> twoArguments(payload)
                else -> null
            }
        }

        private fun navigation(code: String?): DirectoryAction? = when (code) {
            "m" -> Menu
            "h" -> Help
            "i" -> Invite
            "n" -> NewCategory
            else -> null
        }

        private fun singleArgument(payload: CallbackData): DirectoryAction? {
            return when (payload[0]) {
                "c" -> Contacts(payload.page(1) ?: return null)
                "g" -> Categories(payload.page(1) ?: return null)
                "a" -> AddContact(payload.id(1) ?: return null)
                "d" -> ConfirmDeletion(payload.id(1) ?: return null)
                "x" -> DeleteCategory(payload.id(1) ?: return null)
                else -> null
            }
        }

        private fun twoArguments(payload: CallbackData): DirectoryAction? {
            return when (payload[0]) {
                "v" -> Category(payload.id(1) ?: return null, payload.page(2) ?: return null)
                "p" -> Candidates(payload.id(1) ?: return null, payload.page(2) ?: return null)
                "+" -> AddMember(payload.id(1) ?: return null, payload.id(2) ?: return null)
                "-" -> RemoveMember(payload.id(1) ?: return null, payload.id(2) ?: return null)
                else -> null
            }
        }
    }
}
