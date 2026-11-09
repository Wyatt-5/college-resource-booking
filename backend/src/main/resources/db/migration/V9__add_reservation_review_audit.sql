-- Reviewer and decision are stored on the reservation itself so the booking log
-- can show "who decided what" without parsing audit_logs JSON.
-- Auto-confirmed item bookings carry decision=AUTO_CONFIRMED and no reviewer.
ALTER TABLE reservations
    ADD COLUMN reviewed_by BIGINT REFERENCES users(id),
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN decision VARCHAR(20)
        CHECK (decision IS NULL OR decision IN ('APPROVED', 'REJECTED', 'AUTO_CONFIRMED')),
    ADD COLUMN decision_reason VARCHAR(300);

CREATE INDEX idx_reservations_reviewer ON reservations(reviewed_by, reviewed_at DESC);

-- Backfill rows decided before this migration from the existing audit trail.
UPDATE reservations r
SET reviewed_by = a.actor_user_id,
    reviewed_at = a.created_at,
    decision = CASE WHEN r.status = 'REJECTED' THEN 'REJECTED' ELSE 'APPROVED' END,
    decision_reason = NULLIF(a.after_data ->> 'reason', '')
FROM (
    SELECT DISTINCT ON (target_id) target_id, actor_user_id, created_at, after_data
    FROM audit_logs
    WHERE action = 'RESERVATION_REVIEWED' AND target_type = 'RESERVATION'
    ORDER BY target_id, created_at DESC, id DESC
) a
WHERE a.target_id = r.id
  AND r.reviewed_by IS NULL;

-- Ordinary item bookings are confirmed by the system at submission time.
UPDATE reservations
SET decision = 'AUTO_CONFIRMED'
WHERE decision IS NULL
  AND resource_type = 'ITEM'
  AND status IN ('CONFIRMED', 'CANCELLED');
