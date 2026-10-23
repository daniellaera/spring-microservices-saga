# Replay dead-lettered inventory results (payment-service)

## When

payment-service retries a failing inventory result on the retry topics (`inventory-topic-retry-N`, see
`@RetryableTopic` on `InventoryResultEventListener`). When the last attempt fails too, for example during a Stripe
outage longer than the retry budget, the result goes to **`inventory-topic-dlt`**. Then:

- nothing is recorded for the order: no transaction, no refund, nothing on `payment-topic`;
- the order stays **PENDING**;
- payment-service logs `DLT received failed message: …` once at ERROR and audits **`PAYMENT_DEAD_LETTERED`**
  (entity PAYMENT, entityId = the order id, payload `{"orderId":…,"error":"…"}`).

Nothing replays it automatically. Once the cause is gone (Stripe answers again), replay it by hand.

## Safe to repeat

Replaying is idempotent per order. payment-service records at most one transaction per order: an order that
already has one only gets its stored outcome published again, without calling Stripe, refunding or auditing. A
refund carries an idempotency key derived from the order id, so Stripe performs it once.

## Steps (infra-node1)

1. **Find the stuck orders.** Use the admin `/audit` page (search `PAYMENT_DEAD_LETTERED`), or:
   ```bash
   docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
     --topic inventory-topic-dlt --from-beginning --timeout-ms 10000 --property print.offset=true
   ```
   For each order id, check that it is still PENDING and has no transaction:
   ```bash
   U=$(docker exec postgres printenv POSTGRES_USER)
   docker exec postgres psql -U "$U" -d orderdb   -c "select id, status from orders where id in (<ids>)"
   docker exec postgres psql -U "$U" -d paymentdb -c "select order_id, status from transaction where order_id in (<ids>)"
   ```
2. **Check that the cause is gone.** The `error` of the audit or the log line says what failed (e.g.
   `Stripe unavailable: 503`). Check https://status.stripe.com, and make sure payment-service's recent logs
   show successful `Stripe: PaymentIntent … status` lines.
3. **Copy the records to replay** (values only: the inventory results have no key):
   ```bash
   docker exec kafka sh -c '/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
     --topic inventory-topic-dlt --from-beginning --timeout-ms 10000 > /tmp/dlt.jsonl'
   docker exec kafka sh -c 'grep -E "\"orderId\":(801|802)," /tmp/dlt.jsonl > /tmp/replay.jsonl; wc -l /tmp/replay.jsonl; cat /tmp/replay.jsonl'
   ```
   Check that `replay.jsonl` holds exactly one line per order to replay.
4. **Publish them to `inventory-topic` again:**
   ```bash
   docker exec kafka sh -c '/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
     --topic inventory-topic < /tmp/replay.jsonl'
   ```
5. **Verify,** per order:
   - a transaction exists in `paymentdb`;
   - the order is CONFIRMED or CANCELLED;
   - the audit trail shows `PAYMENT_SUCCEEDED`, or `PAYMENT_FAILED` (plus `PAYMENT_REFUNDED` when a captured
     payment was refunded).

   If a replay is dead-lettered again, a new `PAYMENT_DEAD_LETTERED` appears: stop and look at its error.
6. **Clean up:** `docker exec kafka rm -f /tmp/dlt.jsonl /tmp/replay.jsonl`. The records stay on
   `inventory-topic-dlt`, whose offsets are already committed, so they are not processed again; the audit trail
   records what was replayed.
