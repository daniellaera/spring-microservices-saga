-- The Stripe refund that gave back the captured payment of an order that ended FAILED. Existing rows keep NULL.
-- Prod on 2026-10-23: 65 rows (64 SUCCESS, 1 FAILED), no refund column yet.
ALTER TABLE transaction ADD COLUMN refund_id VARCHAR(255);
