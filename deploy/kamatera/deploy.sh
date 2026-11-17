#!/usr/bin/env bash
# Ships server/ and the relay stack to the Kamatera VPS, rebuilds it, wires Nginx
# (co-hosted with AfPins) and waits for HTTPS health.
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

remote=/opt/thelist-sync
target="$VPS_USER@$VPS_HOST"

echo "== shipping to $target:$remote =="
# rsync is unavailable on this Windows host; ship via tar over ssh.
tar czf -   --exclude='node_modules' --exclude='*.db' --exclude='*.db-wal' --exclude='*.db-shm'   -C "$root" server deploy   | ssh "$target" "sudo mkdir -p '$remote' && sudo tar xzf - -C '$remote/'"
echo "== rebuilding relay + wiring Nginx on the VPS =="
ssh "$target" "sudo bash -s -- '$SYNC_DOMAIN' '${ACME_EMAIL:-}' '$remote'" <<'REMOTE'
set -euo pipefail
SYNC_DOMAIN="$1"
ACME_EMAIL="$2"
remote="$3"
mkdir -p /opt/thelist-downloads
chown opsadmin:opsadmin /opt/thelist-downloads
cd "$remote/deploy/kamatera"
printf 'SYNC_DOMAIN=%s\nACME_EMAIL=%s\n' "$SYNC_DOMAIN" "$ACME_EMAIL" > .env
docker compose up -d --build
docker compose ps

# Install the Nginx vhost (substitute only the domain; keep nginx $vars intact).
sed 's/\$SYNC_DOMAIN/'"$SYNC_DOMAIN"'/g' nginx-thelist.conf | tee /etc/nginx/sites-available/thelist-sync > /dev/null
ln -sf /etc/nginx/sites-available/thelist-sync /etc/nginx/sites-enabled/thelist-sync
nginx -t
systemctl reload nginx

# Obtain/refresh the TLS certificate via the existing certbot.
if command -v certbot >/dev/null 2>&1; then
  certbot --nginx -d "$SYNC_DOMAIN" --non-interactive --agree-tos \
    ${ACME_EMAIL:+-m "$ACME_EMAIL"} --redirect || true
  systemctl reload nginx
else
  echo "certbot not installed; install it and run: certbot --nginx -d $SYNC_DOMAIN" >&2
fi
REMOTE

echo "== waiting for https://$SYNC_DOMAIN/health =="
for _ in $(seq 1 40); do
  if curl -fsS --max-time 5 "https://$SYNC_DOMAIN/health" >/dev/null 2>&1; then
    echo "Live: https://$SYNC_DOMAIN"
    echo "Now set syncServerUrl=https://$SYNC_DOMAIN in gradle.properties and push."
    exit 0
  fi
  sleep 4
done

echo "Not answering yet. Check:" >&2
echo "  ssh $target 'cd $remote/deploy/kamatera && docker compose logs --tail=50'" >&2
echo "  ssh $target 'nginx -t && systemctl status nginx --no-pager'" >&2
exit 1
