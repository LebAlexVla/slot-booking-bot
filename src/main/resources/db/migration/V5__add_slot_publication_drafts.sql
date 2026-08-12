CREATE TABLE slot_publication_drafts (
    owner_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    session_id UUID NOT NULL UNIQUE,
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    step VARCHAR(16) NOT NULL CHECK (step IN ('RECIPIENT', 'INTERVALS', 'PLACE', 'DESCRIPTION', 'LIMIT', 'REVIEW', 'PUBLISHED', 'CANCELED')),
    time_zone TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    target_type VARCHAR(16),
    target_user_id UUID REFERENCES users(id),
    target_category_id UUID REFERENCES categories(id),
    target_label TEXT,
    place TEXT,
    description TEXT,
    max_bookings_per_user INTEGER CHECK (max_bookings_per_user > 0),
    published_set_id UUID REFERENCES slot_sets(id),
    last_update_id INTEGER,
    CHECK (
        (target_type IS NULL AND target_user_id IS NULL AND target_category_id IS NULL)
        OR (target_type = 'USER' AND target_user_id IS NOT NULL AND target_category_id IS NULL)
        OR (target_type = 'CATEGORY' AND target_category_id IS NOT NULL AND target_user_id IS NULL)
    )
);

CREATE TABLE slot_publication_intervals (
    owner_id UUID NOT NULL REFERENCES slot_publication_drafts(owner_id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL CHECK (end_at > start_at),
    PRIMARY KEY (owner_id, position)
);

CREATE INDEX idx_slot_sets_owner_id ON slot_sets(owner_id);
CREATE INDEX idx_slots_slot_set_id ON slots(slot_set_id);
