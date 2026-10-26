-- Maintenance: replace the raw refresh tokens in old USER_LOGOUT audit events with "sha256:<12 hex>"
--
-- Status: prepared on 2026-10-26, not run. Database: auditdb (audit-trail-service), table audit_events.
--
-- Why: until #45 (2c35f58, deployed on 2026-10-25 at about 19:00 UTC), auth-service recorded the refresh token
-- that a logout revoked as the USER_LOGOUT payload. Since then it records "sha256:" followed by the first 12 hex
-- digits of the token's SHA-256 (KafkaAuditTrail.sha256Prefix: UTF-8 bytes, lowercase hex). This script gives
-- the older rows the same form. The tokens are revoked, but a credential has no place in an audit trail. The
-- prefix still matches a token one holds to its logout. It is a deliberate exception to the table being
-- append-only: only the payload of these rows changes.
--
-- The payload column holds the JSON the producer wrote, so a string keeps its quotes:
--   before  "6f1c2a4e-0b7d-4c1e-9a3f-2d5b8e7c1a90"      after  "sha256:7cab038fd3a5"
-- A row is rewritten only when its payload is a JSON string with no quote or backslash inside (a refresh token
-- is a UUID), because then dropping the outer quotes gives the token exactly. Rows already hashed, and any
-- other payload, are counted and left alone. Nothing here prints a token: the samples show ids, times and the
-- new values only.
--
-- Run it from a checkout, against infra-node1.
--   1. Dry run (the default). It prints the counts, then runs the UPDATE in a transaction that it rolls back,
--      so "UPDATE n" says how many rows the real run will change. Nothing is kept.
--        ssh infra-node1 'docker exec -i postgres psql -U "$(docker exec postgres printenv POSTGRES_USER)" -d auditdb' \
--          < docs/maintenance/hash-refresh-tokens-in-old-logout-events.sql
--      Check that "raw token" rows stop before the deploy above. A raw token logged after it means an old
--      auth-service was still running. Look into that before applying.
--   2. Apply: the same command with  -v apply=true  after  -d auditdb . The last count must show no "raw token"
--      row. Running it again changes nothing (UPDATE 0).
--
-- What this does not reach:
--   - audit-topic in Kafka keeps the original messages until retention removes them (retention.ms 7 days,
--      delete policy; checked on 2026-10-26). The last segment holding a raw token is gone by 2026-11-08 at the
--      latest. Until then, do not reset audit-trail-service's consumer group to the earliest offset: it would
--      record those events, raw tokens included, again.
--   - /srv/online-shop/maintenance/20261023/kafka-logs/audit-topic-0 on infra-node1 is a copy of audit-topic
--      taken before the Kafka volume move (docs/maintenance/kafka-and-grafana-volumes.md). It holds the raw
--      tokens of every logout before 2026-10-23 16:04 UTC. BACKLOG.md already has it deleted on 2026-10-30.

\set ON_ERROR_STOP on
\if :{?apply}
\else
  \set apply false
\endif

\echo
\echo '1. USER_LOGOUT events by payload form, before:'
SELECT CASE
         WHEN payload ~ '^"sha256:[0-9a-f]{12}"$' THEN 'already hashed'
         WHEN payload ~ '^"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"$' THEN 'raw token (UUID)'
         WHEN payload ~ '^"[^"\\]+"$' THEN 'raw token (other shape)'
         ELSE 'other: left alone'
       END AS form,
       count(*) AS events, min(created_at) AS first, max(created_at) AS last
FROM audit_events
WHERE event_type = 'USER_LOGOUT'
GROUP BY 1
ORDER BY 1;

BEGIN;

\echo '2. Rewriting the raw tokens:'
UPDATE audit_events
SET payload = '"sha256:'
              || left(encode(sha256(convert_to(substr(payload, 2, length(payload) - 2), 'UTF8')), 'hex'), 12)
              || '"'
WHERE event_type = 'USER_LOGOUT'
  AND payload ~ '^"[^"\\]+"$'
  AND payload !~ '^"sha256:[0-9a-f]{12}"$';

\echo '3. After (in this transaction):'
SELECT CASE
         WHEN payload ~ '^"sha256:[0-9a-f]{12}"$' THEN 'already hashed'
         WHEN payload ~ '^"[^"\\]+"$' THEN 'raw token'
         ELSE 'other: left alone'
       END AS form,
       count(*) AS events, min(created_at) AS first, max(created_at) AS last
FROM audit_events
WHERE event_type = 'USER_LOGOUT'
GROUP BY 1
ORDER BY 1;

\echo '4. The 5 oldest, as they are now:'
SELECT id, created_at, payload
FROM audit_events
WHERE event_type = 'USER_LOGOUT'
ORDER BY created_at, id
LIMIT 5;

\if :apply
  COMMIT;
  \echo 'APPLIED: committed.'
\else
  ROLLBACK;
  \echo 'DRY RUN: rolled back, nothing changed. Run again with -v apply=true to keep it.'
\endif
