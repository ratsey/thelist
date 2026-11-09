# Deploying the sync relay on a Kamatera VPS

**Status: stub.** These files are the plan, not a running service. Every host and domain in
`config.example` is a placeholder, nothing has been deployed, and no DNS record exists. Follow
the steps when you are ready to stand it up.

The stack is three containers: Caddy terminating TLS, the relay itself, and a named volume
holding the SQLite database. Only Caddy publishes ports; `8080` stays on the internal network.

## 1. Create the server

In the Kamatera console:

1. **New Server** → Ubuntu 24.04, the smallest size (1 vCPU / 1 GB is plenty — the relay idles
   at a few MB), any location close to the people sharing lists.
2. Give it a public IP, and add an SSH key.
3. In **Firewall**, allow inbound TCP `22`, `80` and `443`. Kamatera's firewall is separate from
   the VPS's own; a closed 80/443 here is the usual reason certificates fail.

## 2. Point a domain at it

Create an `A` record for something like `sync.example.com` → the VPS IP. Caddy needs this to
resolve before it can get a Let's Encrypt certificate, so let DNS propagate first.

## 3. Provision once

```bash
scp deploy/kamatera/setup-vps.sh root@<vps-ip>:/root/
ssh root@<vps-ip> 'bash /root/setup-vps.sh'
```

It installs Docker with the Compose plugin, enables `ufw` for 22/80/443, and creates
`/opt/thelist-sync`.

## 4. Ship the code

```bash
cp deploy/kamatera/config.example deploy/kamatera/deploy.env   # gitignored
$EDITOR deploy/kamatera/deploy.env                             # host, user, domain, email
./deploy/kamatera/deploy.sh
```

`deploy.sh` rsyncs `server/` and `deploy/` to `/opt/thelist-sync`, writes the `.env` from your
local config, rebuilds the image, restarts the stack and polls `https://<domain>/health` until
it answers. The first run takes a minute while the certificate is issued.

## 5. Point the app at it

Set the address once in `gradle.properties`:

```properties
syncServerUrl=https://sync.example.com
```

The share dialog then pre-fills the server field, so friends only need the code. Leave it empty
and everyone types the URL by hand — that is the state today.

## 6. Backups

The database lives in the `sync-data` volume, not in the checkout:

```bash
ssh root@<vps-ip> 'docker run --rm \
  -v thelist-sync_sync-data:/data -v /root:/backup alpine \
  tar czf /backup/thelist-sync-$(date +%F).tgz -C /data .'
```

Pull that file somewhere else on a schedule. Losing it loses every shared list, because the
server is the only copy once people join.

## 7. Operating notes

| Task | Command (from `/opt/thelist-sync/deploy/kamatera`) |
| --- | --- |
| Status | `docker compose ps` |
| Logs | `docker compose logs -f --tail=100` |
| Restart | `docker compose restart` |
| Upgrade | run `deploy.sh` again from the repo |
| Ports | `80/443` only; the relay is `expose`d, never published |

Upgrading replaces the container, not the volume, so data survives. If you change the volume
name in `docker-compose.yml`, you are starting from an empty database.

## What is intentionally not here

- No Terraform/Ansible: one box, one compose file.
- No secret in the repo: the only runtime values are the domain and an ACME email, written to a
  gitignored `.env` on the server by `deploy.sh`.
- No auth changes: the app's list codes and per-member tokens are the whole access model, as
  described in [../../server/README.md](../../server/README.md).
