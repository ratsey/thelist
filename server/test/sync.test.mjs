import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createApp } from '../index.js';

async function startServer() {
  const dir = mkdtempSync(join(tmpdir(), 'thelist-sync-'));
  const server = createApp({ dbPath: join(dir, 'test.db') });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  return {
    base: `http://127.0.0.1:${port}`,
    close: async () => {
      server.closeDb();
      await new Promise((resolve) => server.close(resolve));
    },
  };
}

function op(id, name, updatedAt) {
  return {
    id,
    entity: 'item',
    entityId: 'item-1',
    payload: JSON.stringify({ id: 'item-1', name, updatedAt }),
    updatedAt,
  };
}

test('members of the same list see each other\'s changes', async (t) => {
  const server = await startServer();
  t.after(() => server.close());

  const created = await fetch(`${server.base}/v1/lists`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ memberName: 'Sam', listName: 'Groceries' }),
  }).then((r) => r.json());

  assert.equal(created.code.length, 8);
  assert.ok(created.token.length > 20);

  const joined = await fetch(`${server.base}/v1/lists/${created.code}/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ memberName: 'Alex' }),
  }).then((r) => r.json());

  assert.equal(joined.code, created.code);
  assert.notEqual(joined.token, created.token);

  const pushed = await fetch(`${server.base}/v1/lists/${created.code}/ops`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${created.token}`,
    },
    body: JSON.stringify({ deviceId: 'phone-a', ops: [op('op-1', 'Milk', 1000)] }),
  }).then((r) => r.json());

  assert.equal(pushed.accepted, 1);
  assert.ok(pushed.seq > 0);

  const pulled = await fetch(`${server.base}/v1/lists/${created.code}/ops?since=0`, {
    headers: { Authorization: `Bearer ${joined.token}` },
  }).then((r) => r.json());

  assert.equal(pulled.ops.length, 1);
  assert.equal(pulled.ops[0].entityId, 'item-1');
  assert.equal(JSON.parse(pulled.ops[0].payload).name, 'Milk');
  assert.equal(pulled.seq, pushed.seq);
});

test('joins are rejected for lists that do not exist', async (t) => {
  const server = await startServer();
  t.after(() => server.close());

  const response = await fetch(`${server.base}/v1/lists/NOPE1234/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ memberName: 'Alex' }),
  });

  assert.equal(response.status, 404);
});

test('a token from another list cannot read this list', async (t) => {
  const server = await startServer();
  t.after(() => server.close());

  const create = (name) =>
    fetch(`${server.base}/v1/lists`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ memberName: name, listName: 'Groceries' }),
    }).then((r) => r.json());

  const first = await create('Sam');
  const second = await create('Alex');

  const response = await fetch(`${server.base}/v1/lists/${first.code}/ops?since=0`, {
    headers: { Authorization: `Bearer ${second.token}` },
  });

  assert.equal(response.status, 401);
});

test('replaying the same operation does not duplicate it', async (t) => {
  const server = await startServer();
  t.after(() => server.close());

  const created = await fetch(`${server.base}/v1/lists`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ memberName: 'Sam', listName: 'Groceries' }),
  }).then((r) => r.json());

  const push = (body) =>
    fetch(`${server.base}/v1/lists/${created.code}/ops`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${created.token}`,
      },
      body: JSON.stringify(body),
    }).then((r) => r.json());

  await push({ deviceId: 'phone-a', ops: [op('op-1', 'Milk', 1000)] });
  const second = await push({ deviceId: 'phone-a', ops: [op('op-1', 'Milk', 1000)] });
  assert.equal(second.accepted, 0);

  const pulled = await fetch(`${server.base}/v1/lists/${created.code}/ops?since=0`, {
    headers: { Authorization: `Bearer ${created.token}` },
  }).then((r) => r.json());

  assert.equal(pulled.ops.length, 1);
});

test('pulling with a sequence returns only newer operations', async (t) => {
  const server = await startServer();
  t.after(() => server.close());

  const created = await fetch(`${server.base}/v1/lists`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ memberName: 'Sam', listName: 'Groceries' }),
  }).then((r) => r.json());

  const push = (ops) =>
    fetch(`${server.base}/v1/lists/${created.code}/ops`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${created.token}`,
      },
      body: JSON.stringify({ deviceId: 'phone-a', ops }),
    }).then((r) => r.json());

  const first = await push([op('op-1', 'Milk', 1000)]);
  await push([op('op-2', 'Bread', 2000)]);

  const pulled = await fetch(`${server.base}/v1/lists/${created.code}/ops?since=${first.seq}`, {
    headers: { Authorization: `Bearer ${created.token}` },
  }).then((r) => r.json());

  assert.equal(pulled.ops.length, 1);
  assert.equal(JSON.parse(pulled.ops[0].payload).name, 'Bread');
});
