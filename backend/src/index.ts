export interface Env {
  SQUADS: KVNamespace;
}

interface AthleteRef {
  name: string;
  slug: string;  // empty string means no OPL record; app handles gracefully
}

interface SquadPayload {
  name: string;
  athletes: AthleteRef[];
}

interface BundlePayload {
  squads: SquadPayload[];
}

const CODE_LENGTH = 6;
const TTL_SECONDS = 30 * 24 * 60 * 60; // 30 days
const MAX_ATHLETES = 30;
const MAX_SQUADS_PER_BUNDLE = 10;
const CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no O/0, I/1 to avoid confusion

function generateCode(): string {
  return Array.from({ length: CODE_LENGTH }, () =>
    CHARS[Math.floor(Math.random() * CHARS.length)]
  ).join('');
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function error(message: string, status: number): Response {
  return json({ error: message }, status);
}

function corsHeaders(origin: string): HeadersInit {
  return {
    'Access-Control-Allow-Origin': origin,
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
  };
}

function validateSquad(squad: SquadPayload): string | null {
  const name = squad.name?.trim();
  if (!name || typeof name !== 'string' || name.length > 100)
    return 'Each squad must have a name under 100 characters';
  if (!Array.isArray(squad.athletes) || squad.athletes.length === 0)
    return `Squad "${name}" must have at least one athlete`;
  if (squad.athletes.length > MAX_ATHLETES)
    return `Squad "${name}" exceeds the maximum of ${MAX_ATHLETES} athletes`;
  if (!squad.athletes.every(a =>
    a && typeof a.name === 'string' && a.name.length > 0 && a.name.length <= 200 &&
    (a.slug === undefined || a.slug === null || (typeof a.slug === 'string' && a.slug.length <= 100))
  )) return `Squad "${name}" contains an invalid athlete`;
  return null;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const origin = request.headers.get('Origin') ?? '*';

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders(origin) });
    }

    const addCors = (res: Response): Response => {
      const headers = new Headers(res.headers);
      Object.entries(corsHeaders(origin)).forEach(([k, v]) => headers.set(k, v));
      return new Response(res.body, { status: res.status, headers });
    };

    // ── Single squad ─────────────────────────────────────────────────

    // POST /squads — create a share code for a single squad
    if (request.method === 'POST' && url.pathname === '/squads') {
      let payload: SquadPayload;
      try { payload = await request.json(); }
      catch { return addCors(error('Invalid JSON', 400)); }

      const validationError = validateSquad(payload);
      if (validationError) return addCors(error(validationError, 400));

      const name = payload.name.trim();
      const athletes = payload.athletes;

      let code = generateCode();
      for (let i = 0; i < 5; i++) {
        if (!(await env.SQUADS.get(code))) break;
        code = generateCode();
      }

      await env.SQUADS.put(code, JSON.stringify({ name, athletes, createdAt: new Date().toISOString() }), { expirationTtl: TTL_SECONDS });
      return addCors(json({ code }, 201));
    }

    // GET /squads/:code — fetch a single squad by code
    if (request.method === 'GET') {
      const match = url.pathname.match(/^\/squads\/([A-Z0-9]{6})$/i);
      if (match) {
        const code = match[1].toUpperCase();
        const raw = await env.SQUADS.get(code);
        if (!raw) return addCors(error('Squad not found or link has expired', 404));
        return addCors(json(JSON.parse(raw)));
      }
    }

    // ── Bundle (multiple squads) ──────────────────────────────────────

    // POST /bundles — create a share code for multiple squads
    if (request.method === 'POST' && url.pathname === '/bundles') {
      let payload: BundlePayload;
      try { payload = await request.json(); }
      catch { return addCors(error('Invalid JSON', 400)); }

      const squads = payload.squads;
      if (!Array.isArray(squads) || squads.length === 0)
        return addCors(error('squads must be a non-empty array', 400));
      if (squads.length > MAX_SQUADS_PER_BUNDLE)
        return addCors(error(`Maximum ${MAX_SQUADS_PER_BUNDLE} squads per bundle`, 400));

      for (const squad of squads) {
        const err = validateSquad(squad);
        if (err) return addCors(error(err, 400));
      }

      const normalised = squads.map(s => ({
        name: s.name.trim(),
        athletes: s.athletes.map(a => ({ name: a.name, slug: a.slug ?? '' })),
      }));

      let code = generateCode();
      for (let i = 0; i < 5; i++) {
        if (!(await env.SQUADS.get('bundle:' + code))) break;
        code = generateCode();
      }

      await env.SQUADS.put('bundle:' + code, JSON.stringify({ squads: normalised, createdAt: new Date().toISOString() }), { expirationTtl: TTL_SECONDS });
      return addCors(json({ code }, 201));
    }

    // GET /bundles/:code — fetch a bundle by code
    if (request.method === 'GET') {
      const match = url.pathname.match(/^\/bundles\/([A-Z0-9]{6})$/i);
      if (match) {
        const code = match[1].toUpperCase();
        const raw = await env.SQUADS.get('bundle:' + code);
        if (!raw) return addCors(error('Bundle not found or link has expired', 404));
        return addCors(json(JSON.parse(raw)));
      }
    }

    return addCors(error('Not found', 404));
  },
};
