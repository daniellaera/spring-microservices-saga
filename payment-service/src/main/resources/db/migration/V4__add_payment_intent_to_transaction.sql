-- The Stripe intent that paid for the order (SUCCESS rows only). An intent pays for one order at most.
-- Existing rows keep NULL, which the partial index ignores. Prod had no intent shared by two orders on 2026-10-22.
ALTER TABLE transaction ADD COLUMN payment_intent_id VARCHAR(255);

CREATE UNIQUE INDEX uk_transaction_payment_intent_id ON transaction (payment_intent_id)
    WHERE payment_intent_id IS NOT NULL;
