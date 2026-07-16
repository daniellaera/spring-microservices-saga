CREATE TABLE audit_events (
  id BIGSERIAL PRIMARY KEY,
  event_type VARCHAR(100) NOT NULL,
  user_email VARCHAR(255) NOT NULL,
  entity_type VARCHAR(50) NOT NULL,
  entity_id VARCHAR(255),
  payload TEXT,
  ip_address VARCHAR(45),
  service_name VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_user_email
  ON audit_events(user_email);
CREATE INDEX idx_audit_event_type
  ON audit_events(event_type);
CREATE INDEX idx_audit_created_at
  ON audit_events(created_at DESC);
CREATE INDEX idx_audit_entity_id
  ON audit_events(entity_id);
