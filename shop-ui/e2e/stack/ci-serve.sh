#!/usr/bin/env bash
# CI variant of compose.yml, for pr.yml: the job container can't bind-mount its workspace into sibling
# containers, so the same two nginx servers run inside it (Debian's nginx, as root): the production build
# behind the real config/nginx.conf on :80, and the stub gateway on :8080, reachable as gateway-service.
# Needs a production build in dist/ (ng build --configuration production).
set -euo pipefail
cd "$(dirname "$0")/../.."

rm -rf /usr/share/nginx/html
mkdir -p /usr/share/nginx/html /stub
cp -r dist/shop-ui/browser/. /usr/share/nginx/html/
cp e2e/stack/audit-events.json /stub/
rm -f /etc/nginx/sites-enabled/default
cp config/nginx.conf /etc/nginx/conf.d/shop-ui.conf
cp e2e/stack/gateway-stub.conf /etc/nginx/conf.d/gateway-stub.conf
grep -q ' gateway-service$' /etc/hosts || echo '127.0.0.1 gateway-service' >> /etc/hosts

nginx -t
nginx
curl -sSf -o /dev/null http://localhost/login && echo "shop-ui served on http://localhost"
