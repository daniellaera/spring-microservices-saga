-- Delivery attempts per outbox row: a row that can never be sent is marked FAILED after a few attempts instead
-- of being retried on every relay tick forever. `published` stays, written together with status, so the
-- previous image keeps working against this schema.
ALTER TABLE outbox_events
    ADD COLUMN attempts   INT         NOT NULL DEFAULT 0,
    ADD COLUMN status     VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN last_error TEXT;

UPDATE outbox_events SET status = 'PUBLISHED' WHERE published;
