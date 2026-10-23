# Maintenance: move Kafka and Grafana data onto volumes

Status: executed on 2026-10-23 (16:03–16:11 UTC, backups in `/srv/online-shop/maintenance/20261023`). Host: infra-node1, compose project `online-shop` in `/srv/online-shop`.

## Why

Found on 2026-10-23 during the audit-page incident: two stateful containers keep their data in the
container's writable layer, so **recreating the container deletes the data**. A plain restart keeps it;
`docker compose up -d` after any change to the service (config, image) recreates it.

| Service | Data today | Size (2026-10-23) | Lost on recreate |
|---|---|---|---|
| kafka (`apache/kafka:4.2.0`) | `/tmp/kafka-logs` (default `log.dirs`; the compose service sets none) | 97 MB | every topic, every consumer-group offset, the KRaft metadata log |
| grafana (`grafana/otel-lgtm:latest`) | `/data` (`GF_PATHS_DATA=/data/grafana/data`, plus `/data/{prometheus,tempo,loki,pyroscope}`) | 3.4 GB (pyroscope 2.3 GB) | dashboards and users made in the UI (`grafana.db`), metrics, traces, logs, profiles |

The Kafka container does have a volume at `/var/lib/kafka/data`, but it is an anonymous volume from the
image's `VOLUME` line, and nothing writes to it (4 KB).

Kafka's KRaft `cluster.id` is `MkU3OEVBNTcwNTJENDM2Qg` (`/tmp/kafka-logs/meta.properties`). If Kafka
starts on an empty directory, the image formats new storage and comes up healthy and empty: **no error
tells you the data is gone**. That is why the order of the steps below matters.

## The change (prepare as a PR, merge it during the window)

`docker-compose.yml`:

```yaml
  kafka:
    environment:
      # ...existing variables...
      KAFKA_LOG_DIRS: /var/lib/kafka/data
    volumes:
      - kafka-data:/var/lib/kafka/data

  grafana:
    # pinned to the image running since 2026-05; :latest would upgrade Grafana on the data we move
    image: grafana/otel-lgtm@sha256:10f48eb2f8670134df542177bb19536c55421b089e43f9dfc2a27d4c078204d8
    volumes:
      - grafana-data:/data

volumes:
  postgres-data:
  uptime-kuma-data:
  kafka-data:
  grafana-data:
```

**Do not merge this PR before the window.** Each deploy downloads `docker-compose.yml` from `main` into
`/srv/online-shop`. The deploy itself never recreates kafka or grafana (it only names changed app
services), but anyone running `docker compose up -d` on the host afterwards would recreate Kafka on the
empty new volume. In the window, the merged file is copied in by hand (step K4), so you control when it
takes effect.

## Expected downtime

| Part | Shop impact | Duration |
|---|---|---|
| Kafka | Shop unavailable (gateway stopped, all Kafka clients stopped) | ≈ 10 min of work, plan a 20 min window |
| Grafana | None on the shop; telemetry gap (OTLP data sent while it is down is dropped) | ≈ 5–10 min (3.4 GB copy) |

Do Kafka and Grafana in the same window or separately; they don't depend on each other.

## Before the window

- [ ] Announce the window. Freeze merges to `main` during it: every merge triggers a deploy.
- [ ] Pause the Uptime Kuma monitors for the shop services (otherwise Telegram alerts fire).
- [ ] Disk: at least 2× the data free on `/` (needs ≈ 7.5 GB; 13 GB free on 2026-10-23): `df -h /`.
- [ ] `.env` cluster id matches the on-disk one (prints only the comparison):
  ```bash
  cd /srv/online-shop
  [ "$(grep '^KAFKA_CLUSTER_ID=' .env | cut -d= -f2)" = "$(docker exec kafka grep '^cluster.id=' /tmp/kafka-logs/meta.properties | cut -d= -f2)" ] \
    && echo "cluster id OK" || echo "MISMATCH: stop here"
  ```
- [ ] The running Grafana image is the one the PR pins:
  `docker image inspect -f '{{.RepoDigests}}' "$(docker inspect -f '{{.Image}}' grafana)"`
  (must list `sha256:10f48eb2…`; if not, update the pin in the PR first).
- [ ] Your user's uid is 1000, like the image's `appuser` (`id -u`). This only matters for the rollback copy.

All commands below run on infra-node1, in `/srv/online-shop`, with:

```bash
cd /srv/online-shop
M=/srv/online-shop/maintenance/$(date -u +%Y%m%d); mkdir -p "$M"
K=/opt/kafka/bin; BS=--bootstrap-server=localhost:9092
APPS="auth-service order-service inventory-service payment-service notification-service cart-service audit-trail-service"
cp docker-compose.yml "$M/docker-compose.before.yml"
```

## Kafka

### K1. Pre-checks: nothing in flight

```bash
# no saga in progress
docker exec postgres psql -U "$(docker exec postgres printenv POSTGRES_USER)" -d orderdb -tAc \
  "select count(*) from orders where status = 'PENDING'"                        # expect 0
# outbox drained (the relay polls every 5 s)
docker exec postgres psql -U "$(docker exec postgres printenv POSTGRES_USER)" -d orderdb -tAc \
  "select count(*) from outbox_events where not published"                      # expect 0
# every consumer group caught up: rows whose LAG is not 0, except "-" on empty topics (the retry/DLT ones)
docker exec kafka $K/kafka-consumer-groups.sh $BS --describe --all-groups \
  | awk '$3 ~ /^[0-9]+$/ && $6 != "0" && !($6 == "-" && $5 == "0")'             # expect no output
```

If PENDING orders exist, wait for them to finish (they confirm or cancel within seconds), then rerun.

### K2. Quiesce

```bash
docker compose stop gateway-service          # no new user traffic from here on
sleep 15                                     # outbox relay + consumers finish
# rerun the three K1 checks: all must still be 0
docker compose stop $APPS                    # stop every Kafka producer/consumer
```

The app services must be stopped, not only the gateway: auth-service publishes audit events with a
fail-fast producer, so they would be silently dropped while Kafka is down.

### K3. Record the state to compare later, then stop Kafka and back it up

```bash
docker exec kafka $K/kafka-topics.sh $BS --list                     > "$M/kafka-topics.before"
docker exec kafka $K/kafka-get-offsets.sh $BS                       > "$M/kafka-offsets.before"
docker exec kafka $K/kafka-consumer-groups.sh $BS --describe --all-groups \
  | awk '$3 ~ /^[0-9]+$/ {print $1, $2, $3, $4}' | sort                             > "$M/kafka-groups.before"
docker compose stop -t 60 kafka              # controlled shutdown, flushes the logs
docker cp kafka:/tmp/kafka-logs "$M/kafka-logs"
tar -C "$M" -czf "$M/kafka-logs.tgz" kafka-logs
du -sh "$M/kafka-logs"                       # ≈ 97 MB
```

### K4. Apply the new compose file and fill the volume

Merge the PR now. Its deploy names no changed service, so it restarts nothing and its smoke job has nothing
to check. It must not prune containers: the gateway, the apps and Kafka are stopped at this point, and a
`docker container prune` would delete them (the same PR drops it from `main.yml`). Take the file from
`main` exactly as the deploy job does, so the next deploy changes nothing:

```bash
curl -sSf http://192.168.1.103:3000/daniellaera/spring-saga-store/raw/branch/main/docker-compose.yml -o docker-compose.yml
diff "$M/docker-compose.before.yml" docker-compose.yml        # only the kafka/grafana/volumes lines
docker compose up --no-start --no-deps kafka                  # creates online-shop_kafka-data and a new, stopped container
docker run --rm -v online-shop_kafka-data:/dst -v "$M/kafka-logs":/src:ro alpine \
  sh -c 'cp -a /src/. /dst/ && chown -R 1000:1000 /dst && ls /dst | head -3 && cat /dst/meta.properties'
```

`meta.properties` must show `cluster.id=MkU3OEVBNTcwNTJENDM2Qg`. If it doesn't, stop and roll back.

### K5. Start Kafka and verify

```bash
docker compose start kafka
until [ "$(docker inspect -f '{{.State.Health.Status}}' kafka)" = healthy ]; do sleep 5; done
docker exec kafka grep '^log.dirs' /opt/kafka/config/server.properties   # log.dirs=/var/lib/kafka/data
docker exec kafka ls /tmp/kafka-logs 2>&1                                 # No such file or directory
docker exec kafka $K/kafka-topics.sh $BS --list | diff "$M/kafka-topics.before" -    # no output
docker exec kafka $K/kafka-get-offsets.sh $BS   | diff "$M/kafka-offsets.before" -   # no output
docker exec kafka $K/kafka-consumer-groups.sh $BS --describe --all-groups \
  | awk '$3 ~ /^[0-9]+$/ {print $1, $2, $3, $4}' | sort | diff "$M/kafka-groups.before" -           # no output
```

Then prove it survives the operation that used to lose it:

```bash
docker compose up -d --force-recreate --no-deps kafka
until [ "$(docker inspect -f '{{.State.Health.Status}}' kafka)" = healthy ]; do sleep 5; done
# rerun the three diffs: still no output
```

### K6. Bring the shop back

```bash
docker compose start $APPS
for s in $APPS; do until [ "$(docker inspect -f '{{.State.Health.Status}}' $s)" = healthy ]; do sleep 5; done; echo "$s healthy"; done
docker compose start gateway-service
bash scripts/post-deploy-smoke.sh gateway-service $APPS     # from a checkout of main
```

End-to-end check: place one order on https://shop-staging.daniellaera.com (Stripe test card). Then confirm:

- it reaches `CONFIRMED`;
- its four events (ORDER_CREATED, STOCK_DEDUCTED, PAYMENT_SUCCEEDED, ORDER_CONFIRMED) are on the admin /audit page;
- the K1 lag check shows 0 again.

Unpause the Uptime Kuma monitors.

### Kafka rollback

Use this if K4 or K5 fails (cluster id wrong, Kafka unhealthy, any diff not empty). The app services are
still stopped, so nothing new was produced.

- **Volume problem, compose fine:** `docker compose stop kafka`, empty and refill the volume from
  `$M/kafka-logs` (the K4 `docker run` with `rm -rf /dst/*` first), then repeat K5.
- **Back to the old layout:**
  ```bash
  docker compose stop kafka
  cp "$M/docker-compose.before.yml" docker-compose.yml
  docker compose up --no-start --no-deps kafka            # container without a data volume again
  docker cp -a "$M/kafka-logs" kafka:/tmp/                # -a keeps uid 1000 = appuser
  docker compose start kafka                              # then the K5 diffs, then K6
  ```
  Then revert the compose PR on `main` before anything deploys again.

`$M/kafka-logs.tgz` is the copy of record; keep it for a week.

## Grafana

Grafana runs as root in the container, so no ownership changes are needed.

### G1. Record the state

```bash
curl -s 'http://localhost:3000/api/search?type=dash-db' | python3 -c 'import sys,json; d=json.load(sys.stdin); print(len(d)); [print(x["uid"], x["title"]) for x in d]' > "$M/grafana-dashboards.before"
curl -s http://localhost:3000/api/datasources | python3 -c 'import sys,json; [print(x["uid"], x["type"], x["name"]) for x in json.load(sys.stdin)]' > "$M/grafana-datasources.before"
cat "$M/grafana-dashboards.before"
```

If the API returns 401, anonymous access is off: add `-u admin:<password>` to every curl.

### G2. Stop, back up, fill the volume, start

```bash
docker compose stop grafana
docker cp grafana:/data "$M/grafana-data"                    # 3.4 GB
du -sh "$M/grafana-data"
docker compose up --no-start --no-deps grafana               # pinned image, online-shop_grafana-data
docker run --rm -v online-shop_grafana-data:/dst -v "$M/grafana-data":/src:ro alpine sh -c 'cp -a /src/. /dst/'
docker compose start grafana
until [ "$(docker inspect -f '{{.State.Health.Status}}' grafana)" = healthy ]; do sleep 5; done
```

This uses the compose file applied in K4. If Grafana is migrated on its own, before Kafka, apply the file
first as in K4, and then touch only `grafana`: until Kafka is migrated, the file points Kafka at an empty
volume, so never run a bare `docker compose up -d`.

### G3. Verify

```bash
diff "$M/grafana-dashboards.before"  <(curl -s 'http://localhost:3000/api/search?type=dash-db' | python3 -c 'import sys,json; d=json.load(sys.stdin); print(len(d)); [print(x["uid"], x["title"]) for x in d]')
diff "$M/grafana-datasources.before" <(curl -s http://localhost:3000/api/datasources | python3 -c 'import sys,json; [print(x["uid"], x["type"], x["name"]) for x in json.load(sys.stdin)]')
docker image inspect -f '{{.RepoDigests}}' "$(docker inspect -f '{{.Image}}' grafana)"   # the pinned digest
docker compose up -d --force-recreate --no-deps grafana     # survives a recreate: rerun both diffs
```

Then open Grafana and check:

- a dashboard shows metrics from **before** the window (Prometheus history survived);
- a trace from before the window opens in Tempo;
- new data arrives within a minute.

### Grafana rollback

```bash
docker compose stop grafana
cp "$M/docker-compose.before.yml" docker-compose.yml     # or keep the new file and refill the volume from $M/grafana-data
docker compose up --no-start --no-deps grafana
docker cp "$M/grafana-data/." grafana:/data/
docker compose start grafana                             # then G3
```

With the old file, the image is `:latest` again, and that tag may now point to a newer Grafana than the
data was written with. Roll back to the pinned file (refill the volume) whenever possible.

## Afterwards

- `docker ps --size`: kafka's and grafana's writable layers are now small. The old anonymous Kafka
  volumes can be removed once you're satisfied (`docker volume ls -qf dangling=true`, check each one before
  removing).
- Delete `$M/kafka-logs` and `$M/grafana-data` after a week; keep `kafka-logs.tgz` until then too.
