ALTER TABLE businesses
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE businesses
    ADD COLUMN IF NOT EXISTS owner_id UUID;

UPDATE businesses
SET owner_id = (
    SELECT id
    FROM users
    WHERE role = 'ADMIN'
    ORDER BY created_at
    LIMIT 1
)
WHERE owner_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM businesses WHERE owner_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot migrate businesses: create an ADMIN user to own existing businesses';
    END IF;
END $$;

ALTER TABLE businesses
    ALTER COLUMN owner_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_business_owner'
    ) THEN
        ALTER TABLE businesses
            ADD CONSTRAINT fk_business_owner FOREIGN KEY (owner_id) REFERENCES users(id);
    END IF;
END $$;
