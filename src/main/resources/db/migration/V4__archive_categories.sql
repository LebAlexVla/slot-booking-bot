ALTER TABLE categories ADD COLUMN archived_at TIMESTAMPTZ;

ALTER TABLE categories DROP CONSTRAINT categories_owner_id_name_key;

CREATE UNIQUE INDEX uq_categories_active_owner_name
    ON categories (owner_id, name) WHERE archived_at IS NULL;

CREATE OR REPLACE VIEW slot_visibility AS
SELECT s.id AS slot_id, ss.target_user_id AS user_id
FROM slots s
JOIN slot_sets ss ON ss.id = s.slot_set_id
WHERE ss.target_type = 'USER'

UNION ALL

SELECT s.id AS slot_id, cm.user_id
FROM slots s
JOIN slot_sets ss ON ss.id = s.slot_set_id
JOIN categories c ON c.id = ss.target_category_id AND c.archived_at IS NULL
JOIN category_members cm ON cm.category_id = c.id
WHERE ss.target_type = 'CATEGORY';
