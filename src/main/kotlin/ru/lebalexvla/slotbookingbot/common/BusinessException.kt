package ru.lebalexvla.slotbookingbot.common

class BusinessException(val error: BusinessError) : RuntimeException(error.name)

enum class BusinessError {
    USER_NOT_FOUND,
    SELF_CONTACT,
    CATEGORY_NOT_FOUND,
    INVALID_CATEGORY_NAME,
    CATEGORY_NAME_TAKEN,
    CONTACT_REQUIRED,
    INVALID_SLOT_INTERVALS,
    INVALID_SLOT_FORMAT,
    INVALID_LOCAL_TIME,
    SLOT_IN_PAST,
    SLOT_OVERLAP,
    INVALID_SLOT_LIMIT,
    INVALID_SLOT_TEXT,
    SLOT_NOT_AVAILABLE,
    DRAFT_NOT_FOUND,
    DRAFT_EXPIRED,
    STALE_DRAFT
}
