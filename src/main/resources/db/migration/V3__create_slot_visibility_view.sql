CREATE VIEW slot_visibility AS

SELECT
    s.id AS slot_id,
    ss.target_user_id AS user_id
FROM slots s
         JOIN slot_sets ss ON ss.id = s.slot_set_id
WHERE ss.target_type = 'USER'

UNION ALL

SELECT
    s.id AS slot_id,
    cm.user_id
FROM slots s
         JOIN slot_sets ss ON ss.id = s.slot_set_id
         JOIN category_members cm
              ON cm.category_id = ss.target_category_id
WHERE ss.target_type = 'CATEGORY';