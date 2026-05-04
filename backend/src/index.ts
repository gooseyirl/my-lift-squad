export interface Env {
  SQUADS: KVNamespace;
}

interface AthleteRef {
  name: string;
  slug: string;
}

interface SquadPayload {
  name: string;
  athletes: AthleteRef[];
}

const CODE_LENGTH = 6;
const TTL_SECONDS = 30 * 24 * 60 * 60; // 30 days
const MAX_ATHLETES = 30;
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

    // POST /squads — create a share code
    if (request.method === 'POST' && url.pathname === '/squads') {
      let payload: SquadPayload;
      try {
        payload = await request.json();
      } catch {
        return addCors(error('Invalid JSON', 400));
      }

      const name = payload.name?.trim();
      const athletes = payload.athletes;

      if (!name || typeof name !== 'string' || name.length > 100) {
        return addCors(error('name is required and must be under 100 characters', 400));
      }
      if (!Array.isArray(athletes) || athletes.length === 0) {
        return addCors(error('athletes must be a non-empty array', 400));
      }
      if (athletes.length > MAX_ATHLETES) {
        return addCors(error(`Maximum ${MAX_ATHLETES} athletes per squad`, 400));
      }
      if (!athletes.every(a =>
        a && typeof a.name === 'string' && a.name.length > 0 && a.name.length <= 200 &&
        typeof a.slug === 'string' && a.slug.length > 0 && a.slug.length <= 100
      )) {
        return addCors(error('Each athlete must have a non-empty name and slug', 400));
      }

      // Generate a unique code
      let code = generateCode();
      for (let i = 0; i < 5; i++) {
        const existing = await env.SQUADS.get(code);
        if (!existing) break;
        code = generateCode();
      }

      const value = JSON.stringify({ name, athletes, createdAt: new Date().toISOString() });
      await env.SQUADS.put(code, value, { expirationTtl: TTL_SECONDS });

      return addCors(json({ code }, 201));
    }

    // GET /squads/:code — fetch a squad by code
    if (request.method === 'GET') {
      const match = url.pathname.match(/^\/squads\/([A-Z0-9]{6})$/);
      if (match) {
        const code = match[1];
        const raw = await env.SQUADS.get(code);
        if (!raw) return addCors(error('Squad not found or link has expired', 404));
        return addCors(json(JSON.parse(raw)));
      }
    }

    return addCors(error('Not found', 404));
  },
};
