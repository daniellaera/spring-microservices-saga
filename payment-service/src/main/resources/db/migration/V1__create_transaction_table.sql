CREATE TYPE payment_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED');

CREATE TABLE transaction
(
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT      NOT NULL,
    status       VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    processed_at TIMESTAMP            DEFAULT NOW(),
    created_at   TIMESTAMP            DEFAULT NOW()
);