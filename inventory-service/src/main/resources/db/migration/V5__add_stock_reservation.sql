CREATE TABLE IF NOT EXISTS stock_reservation (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     BIGINT       NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity     INT          NOT NULL,
    reserved_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (order_id, product_name)
);
