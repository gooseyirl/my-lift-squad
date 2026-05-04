import { describe, it, expect, vi, beforeEach } from 'vitest';
import worker from './index';

// Minimal KV mock: in-memory map, supports get/put
function makeKV(initial: Record<string, string> = {}): KVNamespace {
  const store = new Map<string, string>(Object.entries(initial));
  return {
    get: vi.fn(async (key: string) => store.get(key) ?? null),
    put: vi.fn(async (key: string, value: string) => { store.set(key, value); }),
    delete: vi.fn(async (key: string) => { store.delete(key); }),
    list: vi.fn(async () => ({ keys: [], list_complete: true, cursor: '' })),
    getWithMetadata: vi.fn(async (key: string) => ({
      value: store.get(key) ?? null,
      metadata: null,
    })),
  } as unknown as KVNamespace;
}

function makeEnv(kv?: KVNamespace) {
  return { SQUADS: kv ?? makeKV() };
}

function makeRequest(method: string, path: string, body?: unknown): Request {
  const url = `http://localhost${path}`;
  const init: RequestInit = { method, headers: {} };
  if (body !== undefined) {
    (init.headers as Record<string, string>)['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  return new Request(url, init);
}

const ALICE: { name: string; slug: string } = { name: 'Alice Smith', slug: 'alice-smith' };
const BOB: { name: string; slug: string } = { name: 'Bob Jones', slug: 'bob-jones' };
const VALID_SQUAD = { name: 'My Squad', athletes: [ALICE] };

// ── POST /squads ───────────────────────────────────────────────────────────────

describe('POST /squads', () => {
  it('returns 201 with a 6-character code', async () => {
    const res = await worker.fetch(makeRequest('POST', '/squads', VALID_SQUAD), makeEnv());
    expect(res.status).toBe(201);
    const body = await res.json() as { code: string };
    expect(body.code).toMatch(/^[A-Z0-9]{6}$/);
  });

  it('stores the squad in KV under the returned code', async () => {
    const kv = makeKV();
    const res = await worker.fetch(makeRequest('POST', '/squads', VALID_SQUAD), makeEnv(kv));
    const { code } = await res.json() as { code: string };
    expect(kv.put).toHaveBeenCalledWith(
      code,
      expect.stringContaining('"My Squad"'),
      expect.objectContaining({ expirationTtl: expect.any(Number) }),
    );
  });

  it('returns 400 when body is not JSON', async () => {
    const req = new Request('http://localhost/squads', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: 'not json',
    });
    const res = await worker.fetch(req, makeEnv());
    expect(res.status).toBe(400);
  });

  it('returns 400 when squad name is missing', async () => {
    const res = await worker.fetch(
      makeRequest('POST', '/squads', { athletes: [ALICE] }),
      makeEnv(),
    );
    expect(res.status).toBe(400);
  });

  it('returns 400 when squad name exceeds 100 characters', async () => {
    const res = await worker.fetch(
      makeRequest('POST', '/squads', { name: 'x'.repeat(101), athletes: [ALICE] }),
      makeEnv(),
    );
    expect(res.status).toBe(400);
  });

  it('returns 400 when athletes array is empty', async () => {
    const res = await worker.fetch(
      makeRequest('POST', '/squads', { name: 'Squad', athletes: [] }),
      makeEnv(),
    );
    expect(res.status).toBe(400);
  });

  it('returns 400 when an athlete has no slug', async () => {
    const res = await worker.fetch(
      makeRequest('POST', '/squads', { name: 'Squad', athletes: [{ name: 'Alice', slug: '' }] }),
      makeEnv(),
    );
    expect(res.status).toBe(400);
  });

  it('trims whitespace from squad name before storing', async () => {
    const kv = makeKV();
    const res = await worker.fetch(
      makeRequest('POST', '/squads', { name: '  Trimmed  ', athletes: [ALICE] }),
      makeEnv(kv),
    );
    const { code } = await res.json() as { code: string };
    const stored = JSON.parse((await kv.get(code)) as string);
    expect(stored.name).toBe('Trimmed');
  });
});

// ── GET /squads/:code ──────────────────────────────────────────────────────────

describe('GET /squads/:code', () => {
  it('returns 200 with the squad data for a known code', async () => {
    const stored = JSON.stringify({ name: 'My Squad', athletes: [ALICE], createdAt: '2024-01-01' });
    const kv = makeKV({ ABC123: stored });
    const res = await worker.fetch(makeRequest('GET', '/squads/ABC123'), makeEnv(kv));
    expect(res.status).toBe(200);
    const body = await res.json() as { name: string; athletes: typeof ALICE[] };
    expect(body.name).toBe('My Squad');
    expect(body.athletes[0].slug).toBe('alice-smith');
  });

  it('returns 404 for an unknown code', async () => {
    const res = await worker.fetch(makeRequest('GET', '/squads/XXXXXX'), makeEnv());
    expect(res.status).toBe(404);
  });

  it('looks up the code uppercased', async () => {
    const stored = JSON.stringify({ name: 'Squad', athletes: [ALICE], createdAt: '' });
    const kv = makeKV({ ABC123: stored });
    const res = await worker.fetch(makeRequest('GET', '/squads/abc123'), makeEnv(kv));
    expect(res.status).toBe(200);
  });
});

// ── POST /bundles ──────────────────────────────────────────────────────────────

describe('POST /bundles', () => {
  const SQUAD_A = { name: 'Squad A', athletes: [ALICE] };
  const SQUAD_B = { name: 'Squad B', athletes: [BOB] };

  it('returns 201 with a 6-character code', async () => {
    const res = await worker.fetch(
      makeRequest('POST', '/bundles', { squads: [SQUAD_A, SQUAD_B] }),
      makeEnv(),
    );
    expect(res.status).toBe(201);
    const body = await res.json() as { code: string };
    expect(body.code).toMatch(/^[A-Z0-9]{6}$/);
  });

  it('stores the bundle under bundle:<code> key', async () => {
    const kv = makeKV();
    const res = await worker.fetch(
      makeRequest('POST', '/bundles', { squads: [SQUAD_A] }),
      makeEnv(kv),
    );
    const { code } = await res.json() as { code: string };
    expect(kv.put).toHaveBeenCalledWith(
      `bundle:${code}`,
      expect.stringContaining('"Squad A"'),
      expect.objectContaining({ expirationTtl: expect.any(Number) }),
    );
  });

  it('returns 400 when squads array is empty', async () => {
    const res = await worker.fetch(
      makeRequest('POST', '/bundles', { squads: [] }),
      makeEnv(),
    );
    expect(res.status).toBe(400);
  });

  it('returns 400 when squads array is missing', async () => {
    const res = await worker.fetch(makeRequest('POST', '/bundles', {}), makeEnv());
    expect(res.status).toBe(400);
  });

  it('returns 400 when a squad in the bundle fails validation', async () => {
    const res = await worker.fetch(
      makeRequest('POST', '/bundles', { squads: [{ name: '', athletes: [ALICE] }] }),
      makeEnv(),
    );
    expect(res.status).toBe(400);
  });

  it('returns 400 when squads exceed the max per bundle', async () => {
    const squads = Array.from({ length: 11 }, (_, i) => ({
      name: `Squad ${i}`,
      athletes: [ALICE],
    }));
    const res = await worker.fetch(
      makeRequest('POST', '/bundles', { squads }),
      makeEnv(),
    );
    expect(res.status).toBe(400);
  });
});

// ── GET /bundles/:code ─────────────────────────────────────────────────────────

describe('GET /bundles/:code', () => {
  it('returns 200 with all squads for a known bundle code', async () => {
    const payload = {
      squads: [
        { name: 'Squad A', athletes: [ALICE] },
        { name: 'Squad B', athletes: [BOB] },
      ],
      createdAt: '2024-01-01',
    };
    const kv = makeKV({ 'bundle:ABC123': JSON.stringify(payload) });
    const res = await worker.fetch(makeRequest('GET', '/bundles/ABC123'), makeEnv(kv));
    expect(res.status).toBe(200);
    const body = await res.json() as typeof payload;
    expect(body.squads).toHaveLength(2);
    expect(body.squads[0].name).toBe('Squad A');
    expect(body.squads[1].name).toBe('Squad B');
  });

  it('returns 404 for an unknown bundle code', async () => {
    const res = await worker.fetch(makeRequest('GET', '/bundles/XXXXXX'), makeEnv());
    expect(res.status).toBe(404);
  });

  it('looks up the code uppercased', async () => {
    const payload = { squads: [{ name: 'S', athletes: [ALICE] }], createdAt: '' };
    const kv = makeKV({ 'bundle:ABC123': JSON.stringify(payload) });
    const res = await worker.fetch(makeRequest('GET', '/bundles/abc123'), makeEnv(kv));
    expect(res.status).toBe(200);
  });
});

// ── CORS ───────────────────────────────────────────────────────────────────────

describe('CORS', () => {
  it('responds to OPTIONS preflight with 204', async () => {
    const req = new Request('http://localhost/squads', {
      method: 'OPTIONS',
      headers: { Origin: 'https://example.com' },
    });
    const res = await worker.fetch(req, makeEnv());
    expect(res.status).toBe(204);
  });

  it('includes CORS headers on responses', async () => {
    const res = await worker.fetch(makeRequest('GET', '/squads/XXXXXX'), makeEnv());
    expect(res.headers.get('Access-Control-Allow-Origin')).toBeTruthy();
  });
});

// ── Unknown routes ─────────────────────────────────────────────────────────────

describe('Unknown routes', () => {
  it('returns 404 for an unrecognised path', async () => {
    const res = await worker.fetch(makeRequest('GET', '/unknown'), makeEnv());
    expect(res.status).toBe(404);
  });
});
