ALTER TABLE category_members
    ADD COLUMN id UUID;

UPDATE category_members
SET id = gen_random_uuid()
WHERE id IS NULL;

ALTER TABLE category_members
    ALTER COLUMN id SET NOT NULL;

ALTER TABLE category_members
    DROP CONSTRAINT category_members_pkey;

ALTER TABLE category_members
    ADD CONSTRAINT category_members_pkey
        PRIMARY KEY (id);

ALTER TABLE category_members
    ADD CONSTRAINT uq_category_members_category_user
        UNIQUE (category_id, user_id);