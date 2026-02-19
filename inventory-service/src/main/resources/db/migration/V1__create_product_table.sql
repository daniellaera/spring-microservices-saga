CREATE TABLE product
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    quantity   INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMP             DEFAULT NOW()
);