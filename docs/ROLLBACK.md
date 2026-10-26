# Rolling back a service

Every service image that `main.yml` builds is pushed to Docker Hub twice: as `:latest` and as the short SHA
(7 characters) of the `main` commit that triggered the run, e.g. `daniellaera/order-service:aa1b900`.
Production (`infra-node1`, `/srv/online-shop`) runs `:latest`, as referenced by `docker-compose.yml`.

Rolling back means pointing `:latest` at an earlier SHA tag and restarting that one service.

Only services that changed in a run are rebuilt, so a service has SHA tags only for the commits that touched it.
Images built before SHA tagging was introduced have no SHA tag and cannot be rolled back this way.

## 1. Find the SHA to go back to

- The deploy log of each `main.yml` run lists what it deployed, one line per restarted service:
  `Deployed order-service: daniellaera/order-service:<sha> (= :latest), running daniellaera/order-service@sha256:...`
- Or list the commits that touched the service; the SHA tag is the merge commit on `main`:
  `git log --oneline --first-parent main -- order-service/`
- Check that the tag exists: `docker buildx imagetools inspect daniellaera/order-service:<sha>`

## 2. Check the database before going back

Flyway migrations are forward-only. If the version you are leaving added a migration, make sure the older
code still starts against the newer schema (`ddl-auto: validate`) before rolling back, or roll forward with a
fix instead.

## 3. Point `:latest` at the old image (Docker Hub)

From any machine logged in to Docker Hub with push rights (`docker login`):

```bash
docker buildx imagetools create -t daniellaera/order-service:latest daniellaera/order-service:<sha>
```

This retags in the registry without pulling or rebuilding. It is what makes the rollback durable: every deploy
runs `docker compose pull`, which would otherwise bring back the newer `:latest`.

## 4. Restart the service on infra-node1

```bash
cd /srv/online-shop
docker compose -f docker-compose.yml pull order-service
docker compose -f docker-compose.yml up -d --no-deps order-service
```

## 5. Verify

```bash
docker ps --filter name=order-service --format '{{.Names}}\t{{.Status}}'
docker image inspect --format '{{index .RepoDigests 0}}' "$(docker inspect --format '{{.Image}}' order-service)"
docker buildx imagetools inspect daniellaera/order-service:<sha>   # same digest as above
```

The container must reach `(healthy)`, and the running digest must be the one of the SHA tag.

## Afterwards

The next `main` build of that service pushes a new `:latest` and deploys it. Revert or fix the offending
change on `main` before anything else touches that service.

### Emergency: no Docker Hub access

On infra-node1 alone, `docker pull daniellaera/order-service:<sha>`,
`docker tag daniellaera/order-service:<sha> daniellaera/order-service:latest`, then
`docker compose -f docker-compose.yml up -d --no-deps order-service` (without `--pull`). This lasts only until
the next deploy pulls `:latest` again, so do step 3 as soon as possible.

## Redeploying after a failed `main` run

A `main.yml` run whose tests fail builds and deploys nothing (since #37). Gitea has no re-run API, and pushing
again only rebuilds what that push changed. To deploy everything changed since the last good deploy, dispatch
`main.yml` on `main` with `base` set to the **full** SHA of that commit (paths-filter cannot resolve a short one):

```bash
curl -X POST -H "Authorization: token $GITEA_TOKEN" -H "Content-Type: application/json" \
  http://192.168.1.103:3000/api/v1/repos/daniellaera/spring-saga-store/actions/workflows/main.yml/dispatches \
  -d '{"ref":"main","inputs":{"base":"<full 40-character sha>"}}'
```

Or from the Actions tab: "Build and Deploy Online Shop" → "Run workflow". Done on 2026-10-25 (run 265) for the
#36 merge, whose own run (259) had failed on a flaky test.

## Every API call answers 502 after a deploy, and the services are healthy

The site (`GET /`) works, the services' health checks are UP, and the post-deploy smoke check fails on
`GET /products` with 502. shop-ui's log (`docker logs shop-ui`) says which of two causes it is.

**`connect() failed (111: Connection refused)` with an `upstream: "http://172.18.0.N:8080/…"` that is not the
gateway's current address** (`docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
gateway-service`): shop-ui's nginx is using a stale address for the gateway. Since the fix for the deploy of
5267479 (2026-10-25), which recreated gateway-service on a new IP and left the whole API down until a manual
reload, `shop-ui/config/nginx.conf` resolves `gateway-service` through Docker's DNS per request and caches it for
10 s. So after the gateway is recreated, this lasts at most 10 s past the gateway's own startup. If it lasts
longer, the running shop-ui image predates that fix, or its config lost the `resolver`/`set $gateway` lines
(`docker exec shop-ui cat /etc/nginx/conf.d/default.conf`). A rollback does not help, because no service image
is at fault. Make nginx resolve again, on infra-node1:

```bash
docker exec shop-ui nginx -s reload
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: application/json' https://shop-staging.daniellaera.com/products   # 401
```

**`gateway-service could not be resolved`**: Docker's DNS at `127.0.0.11` has no `gateway-service`, because the
gateway container is not running or not on `app-network`. Check `docker ps -a --filter name=gateway-service` and
`docker compose -f docker-compose.yml up -d --no-deps gateway-service`. shop-ui needs no reload; it resolves
again on the next request.

Do not "fix" this by reloading nginx after every deploy: it would hide a broken resolver from the smoke check,
which is how the next stale address would be found.
