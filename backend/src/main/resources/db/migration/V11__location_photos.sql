CREATE TABLE location_images (
    location_id BIGINT PRIMARY KEY REFERENCES locations(id) ON DELETE CASCADE,
    content BYTEA NOT NULL,
    content_type VARCHAR(30) NOT NULL CHECK (content_type IN ('image/jpeg','image/png'))
);
