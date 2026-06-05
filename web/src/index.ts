const COUCHDB_BASE = "https://couchdb.liftingcast.com";
const OPL_BASE = "https://www.openpowerlifting.org/api";
const IPF_BASE = "https://www.openipf.org/api";
const UA = "myliftsquad-web/1.0";

interface Env {
  SHARES: KVNamespace;
}

function generateCode(length: number): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const array = new Uint8Array(length);
  crypto.getRandomValues(array);
  return Array.from(array, (b) => chars[b % chars.length]).join("");
}

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface MeetInfo {
  name: string;
  federation: string;
  date: string;
}

interface Lifter {
  name: string;
  flight: string;
  gender: string;
  weightClass: string;
}

interface OplResult {
  name: string;
  slug: string;
  confidence: "high" | "medium" | "low";
  weightClass: string;
  total: string;
  glPoints: string;
  squat: string;
  bench: string;
  deadlift: string;
  federation: string;
  equipment: string;
}

// ---------------------------------------------------------------------------
// LiftingCast helpers
// ---------------------------------------------------------------------------

function parseLiftingcastUrl(url: string): { meetId: string; platformId?: string } | null {
  const m = url.match(/\/meets\/([A-Za-z0-9]+)(?:\/platforms\/([A-Za-z0-9]+))?/);
  if (!m) return null;
  return { meetId: m[1], platformId: m[2] ?? undefined };
}

function normaliseDate(raw: string): string {
  const trimmed = raw.trim();
  const m = trimmed.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (m) return `${m[3]}-${m[2].padStart(2, "0")}-${m[1].padStart(2, "0")}`;
  if (/^\d{4}-\d{2}-\d{2}/.test(trimmed)) return trimmed.slice(0, 10);
  return trimmed;
}

async function getLiftingcastMeet(meetId: string): Promise<MeetInfo> {
  const res = await fetch(`${COUCHDB_BASE}/${meetId}_readonly/${meetId}`, {
    headers: { Accept: "application/json", "User-Agent": UA },
  });
  if (!res.ok) throw new Error(`LiftingCast returned ${res.status} fetching meet "${meetId}"`);
  const data = (await res.json()) as Record<string, string>;
  return {
    name: data.name ?? "Unknown Meet",
    federation: data.federation ?? "Unknown",
    date: normaliseDate(data.date ?? ""),
  };
}

async function getLiftingcastWeightClassMap(meetId: string): Promise<Record<string, string>> {
  const url =
    `${COUCHDB_BASE}/${meetId}_readonly/_all_docs` +
    `?include_docs=true&startkey=%22d%22&endkey=%22d%EF%BF%B0%22`;
  const res = await fetch(url, { headers: { Accept: "application/json", "User-Agent": UA } });
  if (!res.ok) return {};
  const data = (await res.json()) as { rows: Array<{ doc: Record<string, unknown> }> };
  const map: Record<string, string> = {};
  for (const row of data.rows) {
    const wcs = row.doc.weightClasses as Record<string, { name: string }> | undefined;
    if (wcs) {
      for (const [id, wc] of Object.entries(wcs)) {
        if (!map[id]) map[id] = wc.name;
      }
    }
  }
  return map;
}

async function getLiftingcastLifters(meetId: string, platformId?: string): Promise<Lifter[]> {
  const [lifterRes, wcMap] = await Promise.all([
    fetch(
      `${COUCHDB_BASE}/${meetId}_readonly/_all_docs` +
        `?include_docs=true&startkey=%22l%22&endkey=%22l%EF%BF%B0%22`,
      { headers: { Accept: "application/json", "User-Agent": UA } }
    ),
    getLiftingcastWeightClassMap(meetId),
  ]);
  if (!lifterRes.ok) throw new Error(`LiftingCast returned ${lifterRes.status} fetching lifters`);

  type LifterDoc = {
    name?: string; flight?: string; gender?: string; platformId?: string;
    divisions?: Array<{ divisionId?: string; declaredAwardsWeightClassId?: string }>;
  };
  const data = (await lifterRes.json()) as { rows: Array<{ id: string; doc: LifterDoc }> };

  let lifters = data.rows
    .filter((r) => !r.id.startsWith("_design"))
    .map((r) => {
      const doc = r.doc;
      const wcId = doc.divisions?.[0]?.declaredAwardsWeightClassId ?? "";
      return {
        name: doc.name ?? "",
        flight: doc.flight ?? "?",
        gender: doc.gender ?? "MALE",
        platformId: doc.platformId ?? "",
        weightClass: wcId ? (wcMap[wcId] ?? wcId) : "",
      };
    });

  if (platformId) lifters = lifters.filter((l) => l.platformId === platformId);
  return lifters.map(({ name, flight, gender, weightClass }) => ({ name, flight, gender, weightClass }));
}

// ---------------------------------------------------------------------------
// OPL helpers
// ---------------------------------------------------------------------------

async function searchOplGender(name: string, genderPath: string, base: string): Promise<OplResult[]> {
  const q = encodeURIComponent(name);
  const searchRes = await fetch(
    `${base}/search/rankings/${genderPath}?q=${q}&start=0&lang=en&units=kg`,
    { headers: { "User-Agent": UA } }
  );
  if (!searchRes.ok) return [];

  const searchData = (await searchRes.json()) as Record<string, unknown>;
  const nextIndex = searchData.next_index as number | null | undefined;
  if (nextIndex == null) return [];

  const ranksRes = await fetch(
    `${base}/rankings/${genderPath}?start=${nextIndex}&end=${nextIndex + 24}&lang=en&units=kg`,
    { headers: { "User-Agent": UA } }
  );
  if (!ranksRes.ok) return [];

  const ranksData = (await ranksRes.json()) as { rows?: unknown[][] };
  const nameLower = name.toLowerCase();
  const words = nameLower.split(" ").filter((w) => w.length > 2);
  const results: OplResult[] = [];

  for (const row of ranksData.rows ?? []) {
    if (!Array.isArray(row) || row.length < 4) continue;
    const oplName = String(row[2]);
    const oplSlug = String(row[3]);
    const oplLower = oplName.toLowerCase();

    let confidence: OplResult["confidence"];
    if (oplLower === nameLower) confidence = "high";
    else if (words.length > 0 && words.every((w) => oplLower.includes(w))) confidence = "medium";
    else if (words.some((w) => oplLower.includes(w))) confidence = "low";
    else continue;

    results.push({
      name: oplName,
      slug: oplSlug,
      confidence,
      weightClass: row.length > 18 ? String(row[18] ?? "") : "",
      total: row.length > 22 ? String(row[22] ?? "") : "",
      glPoints: row.length > 23 ? String(row[23] ?? "") : "",
      squat: row.length > 19 ? String(row[19] ?? "") : "",
      bench: row.length > 20 ? String(row[20] ?? "") : "",
      deadlift: row.length > 21 ? String(row[21] ?? "") : "",
      federation: row.length > 6 ? String(row[6] ?? "") : "",
      equipment: row.length > 11 ? String(row[11] ?? "") : "",
    });
  }

  const order: Record<string, number> = { high: 0, medium: 1, low: 2 };
  return results.sort((a, b) => (order[a.confidence] ?? 3) - (order[b.confidence] ?? 3));
}

async function searchOpl(name: string, gender: string, base: string): Promise<OplResult[]> {
  const isFemale =
    gender.toUpperCase() === "FEMALE" ||
    gender.toUpperCase() === "F" ||
    gender.toUpperCase() === "WOMEN";
  const primary = isFemale ? "women" : "men";
  const secondary = primary === "men" ? "women" : "men";
  let results = await searchOplGender(name, primary, base);
  if (!results.length) results = await searchOplGender(name, secondary, base);
  return results.slice(0, 3);
}

// ---------------------------------------------------------------------------
// OPL slug direct lookup (for manual entry)
// ---------------------------------------------------------------------------

interface SlugInfo {
  name: string;
  weightClass: string;
  total: string;
  glPoints: string;
  squat: string;
  bench: string;
  deadlift: string;
  federation: string;
  equipment: string;
}

function parseCsvRow(line: string): string[] {
  const result: string[] = [];
  let field = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') { field += '"'; i++; }
      else inQuotes = !inQuotes;
    } else if (ch === "," && !inQuotes) {
      result.push(field); field = "";
    } else {
      field += ch;
    }
  }
  result.push(field);
  return result;
}

interface MeetEntry {
  date: string; meet: string; federation: string; event: string;
  equipment: string; division: string; bodyweight: string;
  weightClass: string; squat: string; bench: string; deadlift: string;
  total: string; glPoints: string; place: string;
}

async function getLifterHistory(slug: string, base: string): Promise<{ name: string; meets: MeetEntry[] } | null> {
  const res = await fetch(`${base}/liftercsv/${slug}`, { headers: { "User-Agent": UA } });
  if (!res.ok) return null;
  const text = await res.text();
  const lines = text.trim().split("\n");
  if (lines.length < 2) return null;
  const headers = parseCsvRow(lines[0]);
  const col = (name: string) => headers.indexOf(name);
  const nameIdx = col("Name"), dateIdx = col("Date"), meetIdx = col("MeetName");
  const fedIdx = col("Federation"), eventIdx = col("Event"), eqIdx = col("Equipment");
  const divIdx = col("Division"), bwIdx = col("BodyweightKg"), wcIdx = col("WeightClassKg");
  const sqIdx = col("Best3SquatKg"), bchIdx = col("Best3BenchKg"), dlIdx = col("Best3DeadliftKg");
  const totalIdx = col("TotalKg"), glIdx = col("Goodlift"), placeIdx = col("Place");
  const rows = lines.slice(1).map(parseCsvRow);
  const name = nameIdx >= 0 ? (rows[0]?.[nameIdx] ?? slug) : slug;
  const meets: MeetEntry[] = rows
    .filter((r) => r.length > 1)
    .map((r) => ({
      date: dateIdx >= 0 ? r[dateIdx] : "",
      meet: meetIdx >= 0 ? r[meetIdx] : "",
      federation: fedIdx >= 0 ? r[fedIdx] : "",
      event: eventIdx >= 0 ? r[eventIdx] : "",
      equipment: eqIdx >= 0 ? r[eqIdx] : "",
      division: divIdx >= 0 ? r[divIdx] : "",
      bodyweight: bwIdx >= 0 ? r[bwIdx] : "",
      weightClass: wcIdx >= 0 ? r[wcIdx] : "",
      squat: sqIdx >= 0 ? r[sqIdx] : "",
      bench: bchIdx >= 0 ? r[bchIdx] : "",
      deadlift: dlIdx >= 0 ? r[dlIdx] : "",
      total: totalIdx >= 0 ? r[totalIdx] : "",
      glPoints: glIdx >= 0 ? r[glIdx] : "",
      place: placeIdx >= 0 ? r[placeIdx] : "",
    }))
    .sort((a, b) => b.date.localeCompare(a.date));
  return { name, meets };
}

async function lookupOplSlug(slug: string, base: string): Promise<SlugInfo | null> {
  const res = await fetch(`${base}/liftercsv/${slug}`, { headers: { "User-Agent": UA } });
  if (!res.ok) return null;

  const text = await res.text();
  const lines = text.trim().split("\n");
  if (lines.length < 2) return null;

  const headers = lines[0].split(",");
  const col = (name: string) => headers.indexOf(name);
  const nameIdx = col("Name");
  const dateIdx = col("Date");
  const wcIdx = col("WeightClassKg");
  const totalIdx = col("TotalKg");
  const placeIdx = col("Place");
  const sqIdx = col("Best3SquatKg");
  const bchIdx = col("Best3BenchKg");
  const dlIdx = col("Best3DeadliftKg");
  const fedIdx = col("Federation");
  const eqIdx = col("Equipment");
  const glIdx = col("Goodlift");
  if (nameIdx < 0 || totalIdx < 0) return null;

  const rows = lines.slice(1).map((l) => l.split(","));
  const name = rows[0]?.[nameIdx] ?? slug;

  const valid = rows.filter(
    (r) => placeIdx >= 0 && r[placeIdx] !== "DQ" && totalIdx >= 0 && parseFloat(r[totalIdx]) > 0
  );

  if (!valid.length) return { name, weightClass: "", total: "", glPoints: "", squat: "", bench: "", deadlift: "", federation: "", equipment: "" };

  const latest = dateIdx >= 0
    ? [...valid].sort((a, b) => b[dateIdx].localeCompare(a[dateIdx]))[0]
    : valid[0];
  const latestWc = wcIdx >= 0 ? (latest[wcIdx] ?? "") : "";
  const latestFed = fedIdx >= 0 ? (latest[fedIdx] ?? "") : "";
  const latestEq = eqIdx >= 0 ? (latest[eqIdx] ?? "") : "";
  const latestGl = glIdx >= 0 ? (latest[glIdx] ?? "") : "";

  const bestTotal = Math.max(...valid.map((r) => parseFloat(r[totalIdx]) || 0));
  const bestSquat = sqIdx >= 0 ? Math.max(...valid.map((r) => parseFloat(r[sqIdx]) || 0)) : 0;
  const bestBench = bchIdx >= 0 ? Math.max(...valid.map((r) => parseFloat(r[bchIdx]) || 0)) : 0;
  const bestDeadlift = dlIdx >= 0 ? Math.max(...valid.map((r) => parseFloat(r[dlIdx]) || 0)) : 0;

  return {
    name,
    weightClass: latestWc,
    total: bestTotal > 0 ? String(bestTotal) : "",
    glPoints: latestGl,
    squat: bestSquat > 0 ? String(bestSquat) : "",
    bench: bestBench > 0 ? String(bestBench) : "",
    deadlift: bestDeadlift > 0 ? String(bestDeadlift) : "",
    federation: latestFed,
    equipment: latestEq,
  };
}

// ---------------------------------------------------------------------------
// CORS
// ---------------------------------------------------------------------------

function cors(origin: string): HeadersInit {
  return {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
  };
}

function jsonRes(data: unknown, status: number, extraHeaders: HeadersInit = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...extraHeaders },
  });
}

// ---------------------------------------------------------------------------
// Worker
// ---------------------------------------------------------------------------

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const origin = request.headers.get("Origin") ?? "*";
    const c = cors(origin);

    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: c });

    // ── HTML UI ───────────────────────────────────────────────────────────

    if (url.pathname === "/" || url.pathname === "") {
      return new Response(HTML, { headers: { "Content-Type": "text/html; charset=utf-8" } });
    }

    // ── GET /api/meet?url=<liftingcastUrl> ────────────────────────────────

    if (url.pathname === "/api/meet") {
      const lcUrl = url.searchParams.get("url");
      if (!lcUrl) return jsonRes({ error: "Missing url parameter" }, 400, c);

      const parsed = parseLiftingcastUrl(lcUrl);
      if (!parsed) return jsonRes({ error: "Could not extract a meet ID from that URL" }, 400, c);

      try {
        const [meet, lifters] = await Promise.all([
          getLiftingcastMeet(parsed.meetId),
          getLiftingcastLifters(parsed.meetId, parsed.platformId),
        ]);
        if (!lifters.length) return jsonRes({ error: "No lifters found for this meet / platform" }, 404, c);
        return jsonRes({ meet, lifters, meetId: parsed.meetId }, 200, { ...c, "Cache-Control": "no-store" });
      } catch (err) {
        return jsonRes({ error: String(err) }, 502, c);
      }
    }

    // ── GET /api/resolve?name=<name>&gender=<gender> ──────────────────────

    if (url.pathname === "/api/resolve") {
      const name = url.searchParams.get("name") ?? "";
      const gender = url.searchParams.get("gender") ?? "MALE";
      if (!name.trim()) return jsonRes({ results: [] }, 200, c);

      try {
        const source = url.searchParams.get("source") ?? "opl";
        const base = source === "ipf" ? IPF_BASE : OPL_BASE;
        const results = await searchOpl(name.trim(), gender, base);
        return jsonRes({ results }, 200, c);
      } catch {
        return jsonRes({ results: [] }, 200, c);
      }
    }

    // ── GET /api/lookup?slug=<slug> ───────────────────────────────────────

    if (url.pathname === "/api/lookup") {
      const slug = (url.searchParams.get("slug") ?? "").trim().toLowerCase();
      if (!slug) return jsonRes({ error: "Missing slug" }, 400, c);

      try {
        const src = url.searchParams.get("source") ?? "opl";
        const lookupBase = src === "ipf" ? IPF_BASE : OPL_BASE;
        const info = await lookupOplSlug(slug, lookupBase);
        if (!info) return jsonRes({ error: "Lifter not found" }, 404, c);
        return jsonRes({ slug, ...info }, 200, c);
      } catch (err) {
        return jsonRes({ error: String(err) }, 502, c);
      }
    }

    // ── GET /api/lifter?slug=<slug> ───────────────────────────────────────

    if (url.pathname === "/api/lifter") {
      const slug = (url.searchParams.get("slug") ?? "").trim().toLowerCase();
      if (!slug) return jsonRes({ error: "Missing slug" }, 400, c);
      try {
        const src = url.searchParams.get("source") ?? "opl";
        const histBase = src === "ipf" ? IPF_BASE : OPL_BASE;
        const history = await getLifterHistory(slug, histBase);
        if (!history) return jsonRes({ error: "Lifter not found" }, 404, c);
        return jsonRes(history, 200, c);
      } catch (err) {
        return jsonRes({ error: String(err) }, 502, c);
      }
    }

    // ── POST /api/share ───────────────────────────────────────────────────

    if (url.pathname === "/api/share" && request.method === "POST") {
      let body: { state?: unknown };
      try { body = await request.json() as { state?: unknown }; } catch { return jsonRes({ error: "Invalid JSON" }, 400, c); }
      if (!body.state) return jsonRes({ error: "Missing state" }, 400, c);
      const code = generateCode(6);
      await env.SHARES.put(`share:${code}`, JSON.stringify(body.state), { expirationTtl: 60 * 60 * 24 * 30 });
      return jsonRes({ code }, 200, c);
    }

    // ── GET /api/share?code=<code> ────────────────────────────────────────

    if (url.pathname === "/api/share") {
      const code = (url.searchParams.get("code") ?? "").trim().toUpperCase();
      if (!code) return jsonRes({ error: "Missing code" }, 400, c);
      const data = await env.SHARES.get(`share:${code}`);
      if (!data) return jsonRes({ error: "Share link not found or expired" }, 404, c);
      return jsonRes({ state: JSON.parse(data) }, 200, c);
    }

    return jsonRes({ error: "Not found" }, 404);
  },
};

// ---------------------------------------------------------------------------
// HTML — single-page import UI
// ---------------------------------------------------------------------------

const HTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>MyLiftSquad — LiftingCast Import</title>
<style>
:root {
  --bg: #0f1117;
  --surface: #1a1d27;
  --surface2: #20243a;
  --border: #2a2d3e;
  --accent: #6c8fff;
  --accent-dim: rgba(108,143,255,0.12);
  --text: #e8eaf0;
  --text-muted: #8b8fa8;
  --success: #22c55e;
  --warning: #f59e0b;
  --danger: #ef4444;
  --r: 12px;
  --rs: 8px;
}
*{box-sizing:border-box;margin:0;padding:0}
html{font-size:16px}
body{background:var(--bg);color:var(--text);font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;line-height:1.5;min-height:100vh}
.wrap{max-width:920px;margin:0 auto;padding:24px 16px 64px}
header{text-align:center;padding:36px 0 40px}
.logo{font-size:1.625rem;font-weight:700}
.logo span{color:var(--accent)}
header p{color:var(--text-muted);margin-top:6px;font-size:.9375rem}
.card{background:var(--surface);border:1px solid var(--border);border-radius:var(--r);padding:24px;margin-bottom:16px}
.card-title{font-size:.6875rem;font-weight:700;text-transform:uppercase;letter-spacing:.06em;color:var(--text-muted);margin-bottom:16px}
label{display:block;font-size:.8125rem;color:var(--text-muted);margin-bottom:5px}
input[type=url],input[type=text]{width:100%;background:var(--bg);border:1px solid var(--border);border-radius:var(--rs);color:var(--text);padding:10px 14px;font-size:.9375rem;outline:none;transition:border-color .18s}
input:focus{border-color:var(--accent)}
.hint{font-size:.8rem;color:var(--text-muted);margin-top:5px}
.fg{margin-bottom:16px}
.btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;padding:11px 28px;border-radius:var(--rs);font-size:.9375rem;font-weight:600;border:none;cursor:pointer;transition:opacity .15s,transform .1s}
.btn:active{transform:scale(.98)}
.btn-primary{background:var(--accent);color:#fff}
.btn-primary:hover{opacity:.88}
.btn-primary:disabled{opacity:.45;cursor:not-allowed;transform:none}
.btn-secondary{background:var(--surface2);color:var(--text);border:1px solid var(--border)}
.btn-block{width:100%}
.meet-meta{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin-bottom:4px}
.meet-name{font-size:1.0625rem;font-weight:600}
.chip{display:inline-block;background:var(--accent-dim);color:var(--accent);border-radius:20px;padding:2px 11px;font-size:.8rem;font-weight:500}
.prog-wrap{margin-top:16px}
.prog-bg{background:var(--border);border-radius:99px;height:5px;overflow:hidden}
.prog-fill{background:var(--accent);height:100%;border-radius:99px;transition:width .25s}
.prog-label{font-size:.8rem;color:var(--text-muted);margin-top:5px}
.flight-section{margin-bottom:20px}
.flight-header{display:flex;align-items:center;gap:10px;margin-bottom:8px}
.flight-badge{display:inline-flex;align-items:center;justify-content:center;width:30px;height:30px;background:var(--accent-dim);color:var(--accent);border-radius:7px;font-weight:700;font-size:.9rem;flex-shrink:0}
.flight-title{font-weight:600}
.flight-count{color:var(--text-muted);font-size:.85rem}
.tbl-wrap{background:var(--surface);border:1px solid var(--border);border-radius:var(--r);overflow:hidden}
table{width:100%;border-collapse:collapse;font-size:.875rem}
th{text-align:left;padding:9px 14px;color:var(--text-muted);font-size:.6875rem;text-transform:uppercase;letter-spacing:.05em;border-bottom:1px solid var(--border);background:var(--surface2)}
td{padding:10px 14px;border-bottom:1px solid var(--border);vertical-align:middle}
tr:last-child td{border-bottom:none}
.slug{font-family:ui-monospace,monospace;font-size:.8125rem;color:var(--accent)}
.muted{color:var(--text-muted)}
.badge{display:inline-block;border-radius:4px;padding:2px 7px;font-size:.65rem;font-weight:700;text-transform:uppercase;letter-spacing:.04em}
.bh{background:rgba(34,197,94,.14);color:#22c55e}
.bm{background:rgba(245,158,11,.14);color:#f59e0b}
.bl{background:rgba(239,68,68,.14);color:#ef4444}
.bn{background:var(--border);color:var(--text-muted)}
.bp{background:var(--border);color:var(--text-muted);animation:pulse 1.4s infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.35}}
.sum-row{display:flex;flex-wrap:wrap;gap:20px;margin-bottom:20px}
.sum-stat{display:flex;align-items:baseline;gap:6px}
.sum-num{font-size:1.75rem;font-weight:700}
.sum-label{color:var(--text-muted);font-size:.9rem}
.c-green{color:var(--success)}
.c-warn{color:var(--warning)}
.code-box{background:var(--bg);border:2px solid var(--accent);border-radius:var(--r);padding:28px 24px;text-align:center;margin-bottom:12px}
.code-val{font-size:2.75rem;font-weight:700;letter-spacing:.18em;color:var(--accent);font-family:ui-monospace,monospace}
.code-hint{color:var(--text-muted);font-size:.875rem;margin-top:8px}
.multi-codes{display:flex;flex-direction:column;gap:10px}
.mc-item{background:var(--bg);border:1px solid var(--border);border-radius:var(--rs);padding:12px 16px;display:flex;align-items:center;justify-content:space-between}
.mc-label{font-size:.875rem;color:var(--text-muted)}
.mc-val{font-family:ui-monospace,monospace;font-size:1.25rem;font-weight:700;color:var(--accent);letter-spacing:.1em}
.err-box{background:rgba(239,68,68,.1);border:1px solid rgba(239,68,68,.3);border-radius:var(--rs);padding:14px 16px;color:#ef4444;font-size:.9rem;margin-bottom:14px}
.loading{text-align:center;padding:56px 24px;color:var(--text-muted)}
.spinner{display:inline-block;width:28px;height:28px;border:2.5px solid var(--border);border-top-color:var(--accent);border-radius:50%;animation:spin .75s linear infinite;margin-bottom:14px}
@keyframes spin{to{transform:rotate(360deg)}}
.actions{display:flex;gap:10px;align-items:center}
.metric-toggle{display:flex;gap:0;margin-bottom:16px;border:1px solid var(--border);border-radius:var(--rs);overflow:hidden;width:fit-content}
.tog-btn{background:var(--surface);color:var(--text-muted);border:none;padding:7px 18px;font-size:.875rem;font-weight:500;cursor:pointer;transition:background .15s,color .15s}
.tog-btn:hover{background:var(--surface2)}
.tog-active{background:var(--accent-dim);color:var(--accent);font-weight:600}
@media(max-width:580px){
  th:nth-child(3),td:nth-child(3){display:none}
  .sum-num{font-size:1.4rem}
}
</style>
</head>
<body>
<div class="wrap">
  <header>
    <div class="logo">My<span>Lift</span>Squad</div>
    <p>Import flights from LiftingCast</p>
  </header>
  <div id="app"></div>
</div>
<script>
var st = {
  phase: 'input',
  lcUrl: '',
  nameOverride: '',
  meetId: null,
  meet: null,
  lifters: [],
  resolved: [],
  resolvedCount: 0,
  bundleCodes: null,
  genError: null,
  error: null,
  metric: 'total',
  source: 'opl'
};

function esc(s) {
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function groupByFlight(lifters) {
  var g = {};
  for (var i = 0; i < lifters.length; i++) {
    var f = lifters[i].flight || '?';
    if (!g[f]) g[f] = [];
    g[f].push({lifter: lifters[i], idx: i});
  }
  return g;
}

function metricLabel() {
  return st.metric === 'gl' ? 'GL Points' : 'Total (kg)';
}

function metricValue(r) {
  if (!r || !r.oplSlug) return '<span class="muted">—</span>';
  var val = st.metric === 'gl' ? r.glPoints : r.total;
  return val ? esc(val) : '<span class="muted">—</span>';
}

function buildTableRows(entries) {
  var html = '';
  for (var i = 0; i < entries.length; i++) {
    var e = entries[i];
    var lifter = e.lifter;
    var r = st.resolved[e.idx];
    html += '<tr><td>' + esc(lifter.name) + '</td>';
    if (!r) {
      html += '<td><span class="muted">—</span></td><td></td><td><span class="badge bp">Resolving…</span></td><td><span class="muted">—</span></td>';
    } else if (r.oplSlug) {
      html += '<td><span class="slug">' + esc(r.oplSlug) + '</span></td>';
      html += '<td>' + (r.oplName && r.oplName !== lifter.name ? '<span class="muted">' + esc(r.oplName) + '</span>' : '') + '</td>';
      var bclass = r.confidence === 'high' ? 'bh' : r.confidence === 'medium' ? 'bm' : 'bl';
      html += '<td><span class="badge ' + bclass + '">' + r.confidence + '</span></td>';
      html += '<td>' + metricValue(r) + '</td>';
    } else {
      html += '<td><span class="muted">—</span></td><td></td><td><span class="badge bn">None</span></td><td><span class="muted">—</span></td>';
    }
    html += '</tr>';
  }
  return html;
}

function buildMetricToggle() {
  var isTot = st.metric === 'total';
  return '<div class="metric-toggle">' +
    '<button class="tog-btn' + (isTot ? ' tog-active' : '') + '" onclick="setMetric(\'total\')">Total</button>' +
    '<button class="tog-btn' + (!isTot ? ' tog-active' : '') + '" onclick="setMetric(\'gl\')">GL Points</button>' +
    '</div>';
}

function buildSourceToggle() {
  var isOpl = st.source === 'opl';
  return '<div class="metric-toggle">' +
    '<button class="tog-btn' + (isOpl ? ' tog-active' : '') + '" onclick="setSource(\'opl\')">OpenPowerlifting</button>' +
    '<button class="tog-btn' + (!isOpl ? ' tog-active' : '') + '" onclick="setSource(\'ipf\')">OpenIPF</button>' +
    '</div>';
}

function buildFlightsHTML() {
  var groups = groupByFlight(st.lifters);
  var flights = Object.keys(groups).sort();
  var html = '<div style="display:flex;gap:10px;flex-wrap:wrap;margin-bottom:16px">' + buildSourceToggle() + buildMetricToggle() + '</div>';
  for (var fi = 0; fi < flights.length; fi++) {
    var f = flights[fi];
    var entries = groups[f];
    html += '<div class="flight-section">';
    html += '<div class="flight-header">';
    html += '<span class="flight-badge">' + esc(f) + '</span>';
    html += '<span class="flight-title">Flight ' + esc(f) + '</span>';
    html += '<span class="flight-count">' + entries.length + ' athletes</span>';
    html += '</div>';
    html += '<div class="tbl-wrap"><table>';
    html += '<thead><tr><th>Lifter</th><th>OPL Slug</th><th>OPL Name</th><th>Confidence</th><th>' + metricLabel() + '</th></tr></thead>';
    html += '<tbody>' + buildTableRows(entries) + '</tbody>';
    html += '</table></div></div>';
  }
  return html;
}

function meetLabel() {
  if (st.nameOverride) return st.nameOverride;
  if (!st.meet) return '';
  return st.meet.federation + ' - ' + st.meet.date;
}

function render() {
  var app = document.getElementById('app');
  if (!app) return;
  var html = '';

  if (st.phase === 'input') {
    html = '<div class="card">' +
      '<div class="card-title">LiftingCast Meet</div>' +
      '<div class="fg"><label for="lcu">LiftingCast URL</label>' +
      '<input type="url" id="lcu" placeholder="https://liftingcast.com/meets/…" value="' + esc(st.lcUrl) + '"></div>' +
      '<div class="fg"><label for="nom">Competition name <span style="opacity:.5">(optional)</span></label>' +
      '<input type="text" id="nom" placeholder="e.g. IrishPF 2026 June Open" value="' + esc(st.nameOverride) + '">' +
      '<p class="hint">Squad names will be prefixed with this. Defaults to federation + date from LiftingCast.</p></div>' +
      '<button class="btn btn-primary btn-block" onclick="doFetch()">Fetch Meet</button>' +
      '</div>';

  } else if (st.phase === 'fetching') {
    html = '<div class="loading"><div class="spinner"></div><br>Fetching meet data from LiftingCast…</div>';

  } else if (st.phase === 'resolving' || st.phase === 'done') {
    var meet = st.meet;
    html += '<div class="card">';
    html += '<div class="meet-meta">';
    html += '<span class="meet-name">' + esc(meet.name) + '</span>';
    html += '<span class="chip">' + esc(meet.federation) + '</span>';
    if (meet.date) html += '<span class="chip">' + esc(meet.date) + '</span>';
    html += '</div>';
    if (st.phase === 'resolving') {
      var pct = st.lifters.length > 0 ? Math.round(st.resolvedCount / st.lifters.length * 100) : 0;
      html += '<div class="prog-wrap">';
      html += '<div class="prog-bg"><div class="prog-fill" style="width:' + pct + '%"></div></div>';
      var srcLabel = st.source === 'ipf' ? 'OpenIPF' : 'OpenPowerlifting';
      html += '<p class="prog-label">Resolving ' + srcLabel + ' slugs… ' + st.resolvedCount + ' / ' + st.lifters.length + '</p>';
      html += '</div>';
    } else {
      html += '<p class="hint" style="margin-top:8px">Squad prefix: <strong>' + esc(meetLabel()) + '</strong></p>';
    }
    html += '</div>';

    html += buildFlightsHTML();

    if (st.phase === 'done') {
      if (st.bundleCodes) {
        html += '<div class="card">';
        html += '<div class="card-title">Import Code' + (st.bundleCodes.length > 1 ? 's' : '') + '</div>';
        if (st.bundleCodes.length === 1) {
          html += '<div class="code-box"><div class="code-val">' + esc(st.bundleCodes[0].code) + '</div>';
          html += '<p class="code-hint">Open MyLiftSquad &rarr; FAB &rarr; Import Squad &rarr; enter this code</p></div>';
        } else {
          html += '<div class="multi-codes">';
          for (var ci = 0; ci < st.bundleCodes.length; ci++) {
            var item = st.bundleCodes[ci];
            html += '<div class="mc-item">';
            html += '<span class="mc-label">Flights ' + esc(item.flights.join(', ')) + '</span>';
            html += '<span class="mc-val">' + esc(item.code) + '</span>';
            html += '</div>';
          }
          html += '</div>';
        }
        html += '<p class="hint" style="margin-top:12px;text-align:center">Codes expire after 30 days.</p>';
        html += '</div>';
        html += '<div class="actions"><button class="btn btn-secondary" onclick="doReset()">Import another meet</button></div>';
      } else {
        var matched = 0;
        for (var ri = 0; ri < st.resolved.length; ri++) {
          if (st.resolved[ri] && st.resolved[ri].oplSlug) matched++;
        }
        var unmatched = st.lifters.length - matched;
        html += '<div class="card">';
        html += '<div class="sum-row">';
        html += '<div class="sum-stat"><span class="sum-num c-green">' + matched + '</span><span class="sum-label">matched</span></div>';
        if (unmatched > 0) {
          html += '<div class="sum-stat"><span class="sum-num c-warn">' + unmatched + '</span><span class="sum-label">without OPL slug</span></div>';
        }
        html += '</div>';
        if (st.genError) html += '<div class="err-box">' + esc(st.genError) + '</div>';
        html += '<button class="btn btn-primary btn-block" id="genbtn" onclick="doGenerate()">Generate Import Code</button>';
        html += '</div>';
      }
    }

  } else if (st.phase === 'error') {
    html = '<div class="err-box">' + esc(st.error) + '</div>' +
      '<button class="btn btn-secondary" onclick="doReset()">Try again</button>';
  }

  app.innerHTML = html;

  if (st.phase === 'input') {
    var ui = document.getElementById('lcu');
    var ni = document.getElementById('nom');
    if (ui) {
      ui.addEventListener('input', function() { st.lcUrl = this.value; });
      ui.addEventListener('keydown', function(e) { if (e.key === 'Enter') doFetch(); });
    }
    if (ni) ni.addEventListener('input', function() { st.nameOverride = this.value; });
  }
}

function setMetric(m) {
  st.metric = m;
  render();
}

async function setSource(s) {
  if (s === st.source) return;
  st.source = s;
  st.resolved = new Array(st.lifters.length).fill(null);
  st.resolvedCount = 0;
  st.phase = 'resolving';
  render();
  await doResolveAll();
  st.phase = 'done';
  render();
}

function doReset() {
  st.phase = 'input';
  st.error = null;
  st.genError = null;
  st.bundleCodes = null;
  render();
}

async function doFetch() {
  var ui = document.getElementById('lcu');
  var ni = document.getElementById('nom');
  if (ui) st.lcUrl = ui.value.trim();
  if (ni) st.nameOverride = ni.value.trim();
  if (!st.lcUrl) { alert('Please enter a LiftingCast URL'); return; }

  st.phase = 'fetching';
  st.error = null;
  st.genError = null;
  st.bundleCodes = null;
  render();

  try {
    var res = await fetch('/api/meet?url=' + encodeURIComponent(st.lcUrl), { cache: 'no-store' });
    var data = await res.json();
    if (!res.ok) throw new Error(data.error || ('HTTP ' + res.status));

    st.meet = data.meet;
    st.meetId = data.meetId;
    st.lifters = data.lifters;
    st.resolved = new Array(data.lifters.length).fill(null);
    st.resolvedCount = 0;
    st.phase = 'resolving';
    render();

    await doResolveAll();
    st.phase = 'done';
    render();
  } catch (err) {
    st.phase = 'error';
    st.error = err.message || String(err);
    render();
  }
}

async function doResolveAll() {
  var lifters = st.lifters;
  var BATCH = 5;
  for (var i = 0; i < lifters.length; i += BATCH) {
    var batch = lifters.slice(i, Math.min(i + BATCH, lifters.length));
    var baseIdx = i;
    var promises = batch.map(function(lifter, j) {
      var idx = baseIdx + j;
      var url = '/api/resolve?name=' + encodeURIComponent(lifter.name) + '&gender=' + encodeURIComponent(lifter.gender) + '&source=' + st.source;
      return fetch(url)
        .then(function(r) { return r.json(); })
        .then(function(d) {
          var best = d.results && d.results[0];
          return { idx: idx, oplName: best ? best.name : '', oplSlug: best ? best.slug : '', confidence: best ? best.confidence : 'none', total: best ? (best.total || '') : '', glPoints: best ? (best.glPoints || '') : '' };
        })
        .catch(function() {
          return { idx: idx, oplName: '', oplSlug: '', confidence: 'none', total: '', glPoints: '' };
        });
    });
    var results = await Promise.all(promises);
    for (var k = 0; k < results.length; k++) {
      st.resolved[results[k].idx] = results[k];
    }
    st.resolvedCount = Math.min(i + BATCH, lifters.length);
    render();
  }
}

async function doGenerate() {
  var btn = document.getElementById('genbtn');
  if (btn) btn.disabled = true;
  st.genError = null;

  var label = meetLabel();
  var flightMap = {};
  for (var i = 0; i < st.lifters.length; i++) {
    var lifter = st.lifters[i];
    var r = st.resolved[i];
    var f = lifter.flight || '?';
    if (!flightMap[f]) flightMap[f] = [];
    var athlete = { name: lifter.name };
    if (r && r.oplSlug) athlete.slug = r.oplSlug;
    flightMap[f].push(athlete);
  }

  var flights = Object.keys(flightMap).sort();
  var MAX = 10;
  var bundles = [];
  for (var b = 0; b < flights.length; b += MAX) {
    var chunk = flights.slice(b, b + MAX);
    var squads = chunk.map(function(fl) {
      return { name: label + ' - Flight ' + fl, athletes: flightMap[fl] };
    });
    bundles.push({ flights: chunk, squads: squads });
  }

  try {
    var codes = [];
    for (var bi = 0; bi < bundles.length; bi++) {
      var bundle = bundles[bi];
      var res = await fetch('https://myliftsquad-api.gooseyirl.workers.dev/bundles', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ squads: bundle.squads })
      });
      var data = await res.json();
      if (!res.ok) throw new Error(data.error || ('HTTP ' + res.status));
      codes.push({ code: data.code, flights: bundle.flights });
    }
    st.bundleCodes = codes;
    render();
  } catch (err) {
    st.genError = err.message || String(err);
    if (btn) btn.disabled = false;
    render();
  }
}

render();
</script>
</body>
</html>`;
