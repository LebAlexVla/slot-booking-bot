package ru.lebalexvla.slotbookingbot.common

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice

data class ListPage<T>(val items: List<T>, val number: Int, val hasNext: Boolean) {
    companion object {
        const val SIZE = 8
        const val MAX_NUMBER = 10_000

        fun request(number: Int): PageRequest {
            require(number in 0..MAX_NUMBER) { "Invalid page number" }
            return PageRequest.of(number, SIZE)
        }

        fun <T : Any, R> from(slice: Slice<T>, transform: (T) -> R): ListPage<R> =
            ListPage(slice.content.map(transform), slice.number, slice.hasNext())
    }
}
