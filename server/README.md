# thelist-sync

The sync relay behind The List's shared lists. One Node process, one SQLite file, no
dependencies — `node:http` and `node:sqlite` are part of Node itself.

## Run it

```bash
node index.js                       # :8080, ./thelist-sync.db
PORT=9000 DB_PATH=/data/db node index.js
```

Node 22.13+ (24 recommended). With Docker:

```bash
docker build -t thelist-sync .
docker run -p 8080:8080 -v thelist-data:/data thelist-sync
```

Put it behind HTTPS before using it from a phone. A TLS-terminating reverse proxy is enough:

```nginx
server {
  server_name lists.example.com;
  location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
  }
}
```

Then in the app: cloud icon → server address `https://lists.example.com` → **Start a shared
list**. Share the 8-character code. The other person enters the same address, taps
**Join with a code**, and the list appears.

## How it stores data

Operations only — never a merged row. Each op is the full entity as JSON plus `updatedAt`, and
every list has a monotonically increasing `seq`. Devices push their outbox and pull
`seq > lastSeen`, then apply last-write-wins. That is why joining can rebuild state from
scratch, and why two devices editing at once converge without a conflict screen.

Tables: `lists`, `members` (token per member), `ops`, `counters`.

## Security model

- An 8-character list code plus a random 256-bit member token. The code is the invite secret:
  anyone who has it can join, so treat it like a shared password and rotate by starting a new
  list if it leaks.
- **Use HTTPS.** Bearer tokens travel in the `Authorization` header.
- Ops are opaque JSON to the server; there is no account system, no email, no password reset.
- The server sees store names, item names and prices. If that matters to you, self-host it or
  point the app at your own network.

## Backups

Everything is in `DB_PATH` (plus `-wal`/`-shm`). Copy the SQLite file, or run
`sqlite3 thelist-sync.db ".backup backup.db"` while it is running.

## API

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/health` | – | liveness |
| POST | `/v1/lists` | – | create list, returns code + token |
| POST | `/v1/lists/:code/join` | – | join with a code, returns a new token |
| GET | `/v1/lists/:code/ops?since=N` | Bearer | pull operations after `N` |
| POST | `/v1/lists/:code/ops` | Bearer | push up to 200 operations |

## Frozen contract

Fixed for v1. Installed apps and codes already in the wild depend on each of these, so changing
one needs a compatibility story rather than an edit.

| Thing | Value |
| --- | --- |
| Protocol | `1`, reported as `protocol` by `GET /health` |
| Payload shape | every op payload carries `"v": 1` |
| Entities | `item`, `receipt`, `line` |
| Invite code | 8 chars from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (32 symbols; no `0`, `O`, `1`, `I`) |
| Member token | 24 random bytes, hex |
| Push batch | 200 ops |
| Pull page | 500 ops |
| Conflicts | last-write-wins on payload `updatedAt` (device clock); ties go to the incoming op |

The payload is opaque JSON here — the relay never inspects a `v`, it only stores and orders it —
but it is written on every row so shape changes are recognisable in the data itself.

## Tests

```bash
node --test test/
```

The suite starts the server on an ephemeral port and covers create/join, member isolation,
authorisation failures, duplicate op suppression and incremental pulls.
