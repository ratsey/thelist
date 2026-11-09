#!/usr/bin/env bash
# Ships server/ and this stack to the Kamatera VPS, rebuilds it and waits for HTTPS health.
# Requires deploy.env (copy config.example). Nothing runs until you invoke it yourself.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)

if [ ! -f "$here/deploy.env" ]; then
  echo "Missing $here/deploy.env — copy config.example and fill it in." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
. "$here/deploy.env"
set +a

: "${VPS_HOST:?VPS_HOST is required}"
: "${VPS_USER:=root}"
: "${SYNC_DOMAIN:?SYNC_DOMAIN is required}"

if [ "$SYNC_DOMAIN" = "sync.example.com" ]; then
  echo "SYNC_DOMAIN is still the placeholder. Point a DNS A record at $VPS_HOST first." >&2
  exit 1
fi

remote=/opt/trolley-sync
target="$VPS_USER@$VPS_HOST"

echo "== shipping to $target:$remote =="
rsync -az --delete \
  --exclude node_modules \
  --exclude '*.db' --exclude '*.db-wal' --exclude '*.db-shm' \
  "$root/server" "$root/deploy" "$target:$remote/"

echo "== rebuilding on the VPS =="
ssh "$target" bash -s <<REMOTE
set -euo pipefail
cd "$remote/deploy/kamatera"
printf 'SYNC_DOMAIN=%s\nACME_EMAIL=%s\n' "$SYNC_DOMAIN" "${ACME_EMAIL:-}" > .env
docker compose up -d --build
docker compose ps
REMOTE

echo "== waiting for https://$SYNC_DOMAIN/health =="
for _ in $(seq 1 30); do
  if curl -fsS --max-time 5 "https://$SYNC_DOMAIN/health" >/dev/null 2>&1; then
    echo "Live: https://$SYNC_DOMAIN"
    echo "Now set syncServerUrl=https://$SYNC_DOMAIN in gradle.properties."
    exit 0
  fi
  sleep 4
done

echo "Not answering yet. Certificates can take a minute on first run; check:" >&2
echo "  ssh $target 'cd $remote/deploy/kamatera && docker compose logs --tail=50'" >&2
exit 1
