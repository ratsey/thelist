/**
 * Trolley sync server.
 *
 * A deliberately small relay for shared lists: each list has a share code, each member a
 * token, and every change is an immutable operation. Clients push their outbox and pull
 * everything newer than the last sequence they saw, resolving conflicts themselves
 * (last-write-wins on `updatedAt`).
 *
 * No dependencies beyond Node itself: `node:http` + `node:sqlite`.
 *
 *   node index.js                    # listens on :8080, database in ./trolley-sync.db
 *   PORT=9000 DB_PATH=/data/db node index.js
 */
import http from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { randomBytes } from 'node:crypto';
import { pathToFileURL } from 'node:url';

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const MAX_BODY_BYTES = 1_000_000;
const MAX_OPS_PER_PUSH = 200;
const MAX_PULL = 500;
const ENTITIES = new Set(['item', 'receipt', 'line']);

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function makeCode() {
  let code = '';
  for (let i = 0; i < 8; i += 1) {
    code += CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)];
  }
  return code;
}

function makeToken() {
  return randomBytes(24).toString('hex');
}

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    'Cache-Control': 'no-store',
  });
  res.end(payload);
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new HttpError(413, 'body_too_large');
    chunks.push(chunk);
  }
  if (chunks.length === 0) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new HttpError(400, 'invalid_json');
  }
}

export function createApp({ dbPath = process.env.DB_PATH || './trolley-sync.db' } = {}) {
  const db = new DatabaseSync(dbPath);

  db.exec(`
    PRAGMA journal_mode = WAL;
    PRAGMA foreign_keys = ON;
    CREATE TABLE IF NOT EXISTS lists (
      code TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      createdAt INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS members (
      token TEXT PRIMARY KEY,
      code TEXT NOT NULL,
      name TEXT NOT NULL,
      createdAt INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS counters (
      code TEXT PRIMARY KEY,
      seq INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS ops (
      id TEXT PRIMARY KEY,
      code TEXT NOT NULL,
      seq INTEGER NOT NULL,
      deviceId TEXT,
      entity TEXT NOT NULL,
      entityId TEXT NOT NULL,
      payload TEXT NOT NULL,
      updatedAt INTEGER NOT NULL,
      createdAt INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS ops_by_code_seq ON ops (code, seq);
    CREATE INDEX IF NOT EXISTS members_by_code ON members (code);
  `);

  const q = {
    insertList: db.prepare('INSERT INTO lists (code, name, createdAt) VALUES (?, ?, ?)'),
    listByCode: db.prepare('SELECT * FROM lists WHERE code = ?'),
    insertMember: db.prepare('INSERT INTO members (token, code, name, createdAt) VALUES (?, ?, ?, ?)'),
    memberByToken: db.prepare('SELECT * FROM members WHERE token = ?'),
    counter: db.prepare('SELECT seq FROM counters WHERE code = ?'),
    setCounter: db.prepare(
      'INSERT INTO counters (code, seq) VALUES (?, ?) ON CONFLICT(code) DO UPDATE SET seq = excluded.seq',
    ),
    insertOp: db.prepare(
      `INSERT OR IGNORE INTO ops (id, code, seq, deviceId, entity, entityId, payload, updatedAt, createdAt)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    ),
    opsSince: db.prepare(
      `SELECT id, entity, entityId, payload, updatedAt, seq
       FROM ops WHERE code = ? AND seq > ? ORDER BY seq ASC LIMIT ?`,
    ),
  };

  function authenticate(req, code) {
    const header = req.headers.authorization || '';
    if (!header.startsWith('Bearer ')) return null;
    const token = header.slice('Bearer '.length).trim();
    if (!token) return null;
    const member = q.memberByToken.get(token);
    if (!member || member.code !== code) return null;
    return member;
  }

  function currentSeq(code) {
    return q.counter.get(code)?.seq ?? 0;
  }

  const routes = [
    {
      match: (method, path) => method === 'GET' && path === '/health',
      handle: async (req, res) => sendJson(res, 200, { ok: true, service: 'trolley-sync' }),
    },
    {
      match: (method, path) => method === 'POST' && path === '/v1/lists',
      handle: async (req, res) => {
        const body = await readJson(req);
        const memberName = String(body.memberName ?? 'Someone').slice(0, 40);
        const listName = String(body.listName ?? 'Shared list').slice(0, 60);

        let code = makeCode();
        for (let attempt = 0; attempt < 5 && q.listByCode.get(code); attempt += 1) {
          code = makeCode();
        }
        const token = makeToken();
        const now = Date.now();

        db.exec('BEGIN');
        try {
          q.insertList.run(code, listName, now);
          q.insertMember.run(token, code, memberName, now);
          q.setCounter.run(code, 0);
          db.exec('COMMIT');
        } catch (error) {
          db.exec('ROLLBACK');
          throw error;
        }

        sendJson(res, 201, { code, token, listName });
      },
    },
    {
      match: (method, path, parts) =>
        method === 'POST' && parts.length === 4 && parts[1] === 'v1' && parts[2] === 'lists' && parts[4 - 1] === 'join'
          ? false
          : false,
      handle: async () => {},
    },
  ];
  routes.pop();

  const server = http.createServer((req, res) => {
    handleRequest(req, res).catch((error) => {
      if (res.headersSent) return;
      if (error instanceof HttpError) {
        sendJson(res, error.status, { error: error.message });
        return;
      }
      console.error('unhandled', error);
      sendJson(res, 500, { error: 'server_error' });
    });
  });

  async function handleRequest(req, res) {
    const url = new URL(req.url || '/', 'http://localhost');
    const path = url.pathname.replace(/\/+$/, '') || '/';
    const parts = path.split('/').filter(Boolean);
    const method = req.method || 'GET';

    for (const route of routes) {
      if (route.match(method, path)) {
        await route.handle(req, res, parts, url);
        return;
      }
    }

    // POST /v1/lists/:code/join
    if (method === 'POST' && parts.length === 4 && parts[0] === 'v1' && parts[1] === 'lists' && parts[3] === 'join') {
      const code = parts[2].toUpperCase();
      if (!q.listByCode.get(code)) throw new HttpError(404, 'unknown_list');
      const body = await readJson(req);
      const memberName = String(body.memberName ?? 'Someone').slice(0, 40);
      const token = makeToken();
      q.insertMember.run(token, code, memberName, Date.now());
      const list = q.listByCode.get(code);
      sendJson(res, 200, { code, token, listName: list.name });
      return;
    }

    // /v1/lists/:code/ops
    if (parts.length === 4 && parts[0] === 'v1' && parts[1] === 'lists' && parts[3] === 'ops') {
      const code = parts[2].toUpperCase();
      if (!q.listByCode.get(code)) throw new HttpError(404, 'unknown_list');
      const member = authenticate(req, code);
      if (!member) throw new HttpError(401, 'unauthorized');

      if (method === 'GET') {
        const since = Number.parseInt(url.searchParams.get('since') || '0', 10) || 0;
        const rows = q.opsSince.all(code, since, MAX_PULL);
        const seq = rows.length > 0 ? rows[rows.length - 1].seq : since;
        sendJson(res, 200, {
          seq,
          ops: rows.map((row) => ({
            id: row.id,
            entity: row.entity,
            entityId: row.entityId,
            payload: row.payload,
            updatedAt: row.updatedAt,
          })),
        });
        return;
      }

      if (method === 'POST') {
        const body = await readJson(req);
        const deviceId = String(body.deviceId ?? '').slice(0, 64);
        const incoming = Array.isArray(body.ops) ? body.ops.slice(0, MAX_OPS_PER_PUSH) : [];
        const now = Date.now();

        let seq = currentSeq(code);
        let accepted = 0;

        db.exec('BEGIN');
        try {
          for (const op of incoming) {
            if (!op || typeof op.id !== 'string' || !ENTITIES.has(op.entity)) continue;
            if (typeof op.entityId !== 'string' || typeof op.payload !== 'string') continue;
            seq += 1;
            const result = q.insertOp.run(
              op.id,
              code,
              seq,
              deviceId,
              op.entity,
              op.entityId,
              op.payload,
              Number(op.updatedAt) || now,
              now,
            );
            if (result.changes > 0) accepted += 1;
          }
          q.setCounter.run(code, seq);
          db.exec('COMMIT');
        } catch (error) {
          db.exec('ROLLBACK');
          throw error;
        }

        sendJson(res, 200, { accepted, seq });
        return;
      }
    }

    sendJson(res, 404, { error: 'not_found' });
  }

  server.closeDb = () => db.close();
  return server;
}

const isMain = process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url;
if (isMain) {
  const port = Number(process.env.PORT || 8080);
  const server = createApp();
  server.listen(port, '0.0.0.0', () => {
    console.log(`trolley-sync listening on :${port}`);
  });
}
