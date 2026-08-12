package ru.lebalexvla.slotbookingbot.category

import java.util.UUID

data class CategorySummary(val id: UUID, val name: String)

fun Category.toSummary() = CategorySummary(checkNotNull(id), name)
