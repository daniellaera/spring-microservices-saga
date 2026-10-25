-- The saga's correlation id (the order id) on every saga event, for the per-order timeline. Nullable: events
-- recorded before it, and events outside a saga, have none. Existing rows are not backfilled (append-only table):
-- the timeline query matches them by entity_type / entity_id instead.
ALTER TABLE audit_events ADD COLUMN correlation_id VARCHAR(255);

CREATE INDEX idx_audit_correlation_id
  ON audit_events(correlation_id, created_at);
