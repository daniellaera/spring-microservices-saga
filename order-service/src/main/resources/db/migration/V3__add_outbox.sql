CREATE TABLE IF NOT EXISTS outbox_events (
    id           UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id BIGINT  NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT    NOT NULL,
    published    BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP
);
