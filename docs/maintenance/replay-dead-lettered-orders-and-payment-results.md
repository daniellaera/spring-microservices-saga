# Replay dead-lettered orders and payment results (inventory-service, order-service)

## When

inventory-service and order-service retry a failing record on their own retry topics (`@RetryableTopic` on
`OrderEventListener` and `PaymentEventListener` in inventory-service, and on `PaymentEventListener` in
order-service; 5 attempts over about 80 s). An unreadable record skips the retries. When the last attempt fails,
the record goes to the service's dead-letter topic. The service logs `DLT received failed message: …` once at
ERROR and audits it:

| Dead-letter topic             | Service           | Audit event                       | What is left undone                                            |
|-------------------------------|-------------------|-----------------------------------|----------------------------------------------------------------|
| `orders-topic-inventory-dlt`  | inventory-service | `STOCK_RESERVATION_DEAD_LETTERED` | no stock reserved and no inventory result sent: the order stays **PENDING** |
| `payment-topic-inventory-dlt` | inventory-service | `STOCK_RESTORE_DEAD_LETTERED`     | after a FAILED payment, the reserved stock is **not released**   |
| `payment-topic-order-dlt`     | order-service     | `ORDER_DEAD_LETTERED`             | the payment result is not applied: the order stays **PENDING**  |

Each audit event has entity ORDER, entityId and correlationId set to the order id (so it shows on the order's saga
timeline, `/audit/orders/<id>`), and payload `{"orderId":…,"error":"…"}`. For a record that cannot be read, the
order id is empty and the error starts with the parse failure. Such a record cannot be replayed as it is: look at
it and fix it by hand, or drop it.

Nothing replays these automatically. Once the cause is gone (the error says what failed: usually the database),
replay them by hand. For an inventory result dead-lettered by payment-service, see
[replay-dead-lettered-inventory-results.md](replay-dead-lettered-inventory-results.md).

## Safe to repeat

- **Stock reservation** (`orders-topic`): skipped when the order already has a row in `stock_reservation`
  (logged `stock already reserved for orderId … — redelivery, skipping`). The order's stock is therefore reserved
  at most once.
- **Stock release** (`payment-topic`, FAILED): skipped when the order is in `compensation_log`, and a no-op for
  a SUCCESS result or an order without reservations.
- **Payment result** (`payment-topic`, order-service): ignored when the order is no longer PENDING.

## Steps (infra-node1)

1. **Find the records.** Use the admin `/audit` page (event type filter, or search `DEAD_LETTERED`), or read the
   dead-letter topic:
   ```bash
   T=orders-topic-inventory-dlt   # or payment-topic-inventory-dlt, payment-topic-order-dlt
   docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
     --topic "$T" --from-beginning --timeout-ms 10000 --property print.offset=true
   ```
2. **Check each order's state** (`<ids>` = the order ids to replay):
   ```bash
   U=$(docker exec postgres printenv POSTGRES_USER)
   docker exec postgres psql -U "$U" -d orderdb -c "select id, status from orders where id in (<ids>)"
   docker exec postgres psql -U "$U" -d inventorydb \
     -c "select order_id, product_name, quantity from stock_reservation where order_id in (<ids>)" \
     -c "select order_id from compensation_log where order_id in (<ids>)"
   ```
   - `orders-topic-inventory-dlt`: replay only PENDING orders **without** a `stock_reservation` row. A PENDING
     order that has one had its stock reserved but its inventory result never sent; a replay is skipped and will
     not help. Stop and look at the inventory-service logs for that order.
   - `payment-topic-inventory-dlt`: replay the FAILED results of orders that have `stock_reservation` rows and
     no `compensation_log` row. Anything else would be skipped anyway.
   - `payment-topic-order-dlt`: replay only orders that are still PENDING.
3. **Check that the cause is gone.** The audit's `error`, or the log line, says what failed (e.g. a database
   that was unreachable). Make sure the service's recent logs no longer show it.
4. **Copy the records to replay.** Copy the values only, since these records have no key:
   ```bash
   docker exec kafka sh -c "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
     --topic $T --from-beginning --timeout-ms 10000 > /tmp/dlt.jsonl"
   docker exec kafka sh -c 'grep -E "\"orderId\":(901|903)," /tmp/dlt.jsonl > /tmp/replay.jsonl; wc -l /tmp/replay.jsonl; cat /tmp/replay.jsonl'
   ```
   Check that `replay.jsonl` holds exactly one line per order to replay.
5. **Publish them to the source topic again.** Use `orders-topic` for `orders-topic-inventory-dlt`, and
   `payment-topic` for the other two:
   ```bash
   SOURCE=orders-topic   # or payment-topic
   docker exec kafka sh -c "/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
     --topic $SOURCE < /tmp/replay.jsonl"
   ```
   The source topic has every consumer of the record, not only the service that dead-lettered it. Each of the
   other consumers has already handled it and skips it (see **Safe to repeat**).
   - On `orders-topic`, inventory-service is the only consumer.
   - On `payment-topic`, both inventory-service and order-service consume. The replay is a no-op for whichever of
     them had already handled the record.
6. **Verify,** per order:
   - reservation replayed: `stock_reservation` rows (or a REJECTED inventory result), then the saga continues.
     The timeline shows `STOCK_DEDUCTED`, then `PAYMENT_…` and `ORDER_CONFIRMED` or `ORDER_CANCELLED`;
   - release replayed: a `compensation_log` row and `STOCK_RESTORED` on the timeline;
   - payment result replayed: the order is CONFIRMED or CANCELLED, with `ORDER_CONFIRMED` or `ORDER_CANCELLED`
     on the timeline.

   If a replay is dead-lettered again, a new `…_DEAD_LETTERED` event appears: stop and look at its error.
7. **Clean up** with `docker exec kafka rm -f /tmp/dlt.jsonl /tmp/replay.jsonl`. The records stay on the
   dead-letter topic, whose offsets are already committed, so they are not processed again. The audit trail
   records what was replayed.
