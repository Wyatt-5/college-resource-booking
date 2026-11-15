CREATE TABLE announcement_reads (
    user_id BIGINT NOT NULL REFERENCES users(id),
    announcement_id BIGINT NOT NULL REFERENCES announcements(id),
    seen_updated_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, announcement_id)
);
