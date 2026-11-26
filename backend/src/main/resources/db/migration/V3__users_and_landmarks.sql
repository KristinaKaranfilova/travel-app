-- Корисници. Лозинката НЕ се чува, туку само нејзиниот BCrypt хеш.
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(200) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- Секое ново патување припаѓа на корисник (старите, од пред најавата, немаат сопственик).
ALTER TABLE trip ADD COLUMN user_id BIGINT REFERENCES app_user (id) ON DELETE CASCADE;
CREATE INDEX idx_trip_user ON trip (user_id, created_at DESC);

-- Кеш за знаменитости (слики) по локација, за Wikidata да не се прашува секој пат.
-- location_key = координатите заокружени на 2 децимали, на пр. "41.12,20.80"
CREATE TABLE landmark_cache (
    location_key VARCHAR(30) PRIMARY KEY,
    landmarks    JSONB     NOT NULL,
    fetched_at   TIMESTAMP NOT NULL
);
