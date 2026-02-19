CREATE TYPE order_status AS ENUM ('PENDING', 'CONFIRMED', 'CANCELLED');

CREATE TABLE orders
(
    id           BIGSERIAL PRIMARY KEY,
    product_name VARCHAR(255) NOT NULL,
    quantity     INTEGER      NOT NULL,
    status       VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP             DEFAULT NOW()
);