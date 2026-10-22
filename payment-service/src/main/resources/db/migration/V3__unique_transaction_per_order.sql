-- One payment transaction per order: a redelivered inventory result must not record a second one.
-- Should duplicates exist, keep the earliest row per order (lowest id) and drop the others first.
-- Prod had none on 2026-10-22 (63 rows, 63 distinct orders).
DELETE FROM transaction later
    USING transaction earliest
    WHERE later.order_id = earliest.order_id
      AND later.id > earliest.id;

ALTER TABLE transaction ADD CONSTRAINT uk_transaction_order_id UNIQUE (order_id);
