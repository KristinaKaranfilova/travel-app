-- Кратки описи на места (од Wikipedia/Wikidata), за да не се бараат при секој клик.
CREATE TABLE place_details_cache (
    cache_key  VARCHAR(400) PRIMARY KEY,
    details    JSONB     NOT NULL,
    found      BOOLEAN   NOT NULL,
    fetched_at TIMESTAMP NOT NULL
);

-- Знаменитостите сега имаат и координати, па старите записи без нив се бришат.
DELETE FROM landmark_cache;
