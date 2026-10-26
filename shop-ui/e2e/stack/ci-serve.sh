#!/usr/bin/env bash
# CI variant of compose.yml, for pr.yml: the job container can't bind-mount its workspace into sibling
# containers, so the same two nginx servers run inside it (Debian's nginx, as root): the production build
# behind the real config/nginx.conf on :80, and the stub gateway on :8080. There is no Docker DNS here for
# nginx.conf's resolver to ask (and nginx's resolver ignores /etc/hosts), so this copy of the config points
# $gateway at 127.0.0.1:8080; an IP needs no resolving, and everything else in the config is the real one.
# Needs a production build in dist/ (ng build --configuration production).
set -euo pipefail
cd "$(dirname "$0")/../.."

rm -rf /usr/share/nginx/html
mkdir -p /usr/share/nginx/html /stub
cp -r dist/shop-ui/browser/. /usr/share/nginx/html/
cp e2e/stack/audit-events.json e2e/stack/timeline-events.json /stub/
rm -f /etc/nginx/sites-enabled/default
sed 's/set $gateway gateway-service:8080;/set $gateway 127.0.0.1:8080;/' config/nginx.conf > /etc/nginx/conf.d/shop-ui.conf
grep -q 'set $gateway 127.0.0.1:8080;' /etc/nginx/conf.d/shop-ui.conf
cp e2e/stack/gateway-stub.conf /etc/nginx/conf.d/gateway-stub.conf

nginx -t
nginx
curl -sSf -o /dev/null http://localhost/login && echo "shop-ui served on http://localhost"
