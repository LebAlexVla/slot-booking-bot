CREATE TABLE booking_request_drafts (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    session_id UUID NOT NULL UNIQUE,
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    step VARCHAR(16) NOT NULL CHECK (step IN ('COMMENT', 'PLACE', 'REVIEW', 'SUBMITTED', 'CANCELED')),
    slot_id UUID NOT NULL REFERENCES slots(id),
    expires_at TIMESTAMPTZ NOT NULL,
    comment TEXT,
    proposed_place TEXT,
    booking_id UUID REFERENCES bookings(id),
    last_update_id INTEGER
);

CREATE INDEX idx_bookings_booked_by ON bookings(booked_by_user_id);
