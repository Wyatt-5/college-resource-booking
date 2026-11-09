CREATE TABLE user_details (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    department VARCHAR(100) NOT NULL DEFAULT '',
    email VARCHAR(254) NOT NULL DEFAULT '',
    phone VARCHAR(30) NOT NULL DEFAULT '',
    avatar BYTEA,
    avatar_content_type VARCHAR(20)
);
