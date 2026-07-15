CREATE TABLE refresh_tokens
(
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(512) NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expiry_date TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP             DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens (token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
