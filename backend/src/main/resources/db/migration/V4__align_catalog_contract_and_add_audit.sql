-- Additive migration: never rewrite V3 or truncate existing descriptions/names.
ALTER TABLE items ADD COLUMN max_borrow_days INTEGER CHECK (max_borrow_days > 0);
UPDATE items SET max_borrow_days = max_borrow_hours / 24
WHERE max_borrow_hours IS NOT NULL AND max_borrow_hours % 24 = 0;
-- Non-whole-day legacy rules remain in max_borrow_hours for explicit manual review.
ALTER TABLE items DROP CONSTRAINT items_max_advance_days_check;
ALTER TABLE items ADD CONSTRAINT items_max_advance_days_check CHECK (max_advance_days >= 0);

ALTER TABLE item_units DROP CONSTRAINT item_units_unit_code_key;
ALTER TABLE item_units ADD CONSTRAINT item_units_item_code_key UNIQUE (item_id, unit_code);
ALTER TABLE item_units DROP CONSTRAINT item_units_status_check;
UPDATE item_units SET status = 'DISABLED' WHERE status = 'INACTIVE';
ALTER TABLE item_units ADD CONSTRAINT item_units_status_check
CHECK (status IN ('AVAILABLE','CHECKED_OUT','RETURN_SUBMITTED','MAINTENANCE','DISABLED','LOST'));

ALTER TABLE item_images ADD COLUMN file_name VARCHAR(255) NOT NULL DEFAULT 'image';

CREATE TABLE catalog_idempotency (
    actor VARCHAR(200) NOT NULL,
    action VARCHAR(100) NOT NULL,
    request_key VARCHAR(200) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (actor, action, request_key)
);

-- Module outbox: audit integration consumes these events; not a second global audit API.
CREATE TABLE catalog_audit_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor VARCHAR(200) NOT NULL,
    action VARCHAR(100) NOT NULL,
    object_id BIGINT NOT NULL,
    before_state TEXT NOT NULL DEFAULT '',
    after_state TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMPTZ
);
