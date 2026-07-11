CREATE TABLE IF NOT EXISTS order_items (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders(id),
    product_name  VARCHAR(255) NOT NULL,
    quantity      INTEGER NOT NULL,
    price         DECIMAL(10,2) NOT NULL,
    total_amount  DECIMAL(10,2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
