CREATE TABLE users
(
    id               UUID PRIMARY KEY,
    telegram_user_id BIGINT      NOT NULL UNIQUE,
    telegram_chat_id BIGINT      NOT NULL,
    username         TEXT,
    first_name       TEXT        NOT NULL,
    last_name        TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE contacts
(
    id              UUID PRIMARY KEY,
    owner_id        UUID        NOT NULL REFERENCES users (id),
    contact_user_id UUID        NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (owner_id, contact_user_id),
    CHECK (owner_id <> contact_user_id)
);

CREATE TABLE categories
(
    id         UUID PRIMARY KEY,
    owner_id   UUID        NOT NULL REFERENCES users (id),
    name       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (owner_id, name)
);

CREATE TABLE category_members
(
    category_id UUID        NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users (id),
    added_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (category_id, user_id)
);

CREATE TABLE slot_sets
(
    id                    UUID PRIMARY KEY,
    owner_id              UUID        NOT NULL REFERENCES users (id),
    target_type           VARCHAR(16) NOT NULL,
    target_user_id        UUID REFERENCES users (id),
    target_category_id    UUID REFERENCES categories (id),
    place                 TEXT,
    description           TEXT,
    max_bookings_per_user INTEGER     NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (target_type IN ('USER', 'CATEGORY')),

    CHECK (
        (
            target_type = 'USER'
                AND target_user_id IS NOT NULL
                AND target_category_id IS NULL
            )
            OR
        (
            target_type = 'CATEGORY'
                AND target_category_id IS NOT NULL
                AND target_user_id IS NULL
            )
        ),

    CHECK (max_bookings_per_user > 0)
);

CREATE TABLE slots
(
    id          UUID PRIMARY KEY,
    slot_set_id UUID        NOT NULL REFERENCES slot_sets (id) ON DELETE CASCADE,
    start_at    TIMESTAMPTZ NOT NULL,
    end_at      TIMESTAMPTZ NOT NULL,
    status      VARCHAR(32) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (end_at > start_at),

    CHECK (
        status IN (
                   'AVAILABLE',
                   'PENDING_CONFIRMATION',
                   'BOOKED',
                   'CANCELED',
                   'EXPIRED'
            )
        )
);

CREATE TABLE bookings
(
    id                UUID PRIMARY KEY,
    slot_id           UUID        NOT NULL REFERENCES slots (id) ON DELETE CASCADE,
    booked_by_user_id UUID        NOT NULL REFERENCES users (id),
    status            VARCHAR(16) NOT NULL,
    proposed_place    TEXT,
    comment           TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at      TIMESTAMPTZ,
    canceled_at       TIMESTAMPTZ,

    CHECK (
        status IN (
                   'PENDING',
                   'CONFIRMED',
                   'REJECTED',
                   'CANCELED'
            )
        )
);

CREATE TABLE notification_settings
(
    user_id                            UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    notify_on_slot_booked              BOOLEAN     NOT NULL,
    notify_on_booking_canceled         BOOLEAN     NOT NULL,
    notify_on_available_slot_published BOOLEAN     NOT NULL,
    notify_on_slot_day                 BOOLEAN     NOT NULL,
    reminder_before_minutes            INTEGER,
    updated_at                         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (
        reminder_before_minutes IS NULL
            OR reminder_before_minutes >= 0
        )
);

CREATE UNIQUE INDEX uq_bookings_one_active_per_slot
    ON bookings (slot_id)
    WHERE status IN ('PENDING', 'CONFIRMED');