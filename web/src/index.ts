const COUCHDB_BASE = "https://couchdb.liftingcast.com";
const OPL_BASE = "https://www.openpowerlifting.org/api";
const IPF_BASE = "https://www.openipf.org/api";
const IRISHPF_HOST = "irishpowerliftingfederation.com";
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
  lot?: number | string;
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
  // Set once the numbers above are career bests rather than a single meet's.
  // Lets the page spot results from an older deployment and top them up.
  bests?: boolean;
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
    lot?: number | string;
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
        lot: doc.lot ?? "",
        weightClass: wcId ? (wcMap[wcId] ?? wcId) : "",
      };
    });

  if (platformId) lifters = lifters.filter((l) => l.platformId === platformId);
  return lifters.map(({ name, flight, gender, weightClass, lot }) => ({ name, flight, gender, weightClass, lot }));
}

// ---------------------------------------------------------------------------
// IrishPF helpers
//
// Competition pages on irishpowerliftingfederation.com are WordPress pages with
// a TablePress entry list: Flight | Lot | Name | Club | Class | Session.
// Column order is not consistent between pages (Club/Class are sometimes
// swapped relative to the header row), so the weight class is located by
// matching cell contents rather than trusting the header.
// ---------------------------------------------------------------------------

function parseIrishPfUrl(url: string): string | null {
  let parsed: URL;
  try { parsed = new URL(url); } catch { return null; }
  const host = parsed.hostname.toLowerCase().replace(/^www\./, "");
  if (host !== IRISHPF_HOST) return null;
  const path = parsed.pathname.replace(/\/+$/, "");
  if (!path || path === "/") return null;
  return path;
}

function decodeEntities(s: string): string {
  return s
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(Number(d)))
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&nbsp;/g, " ")
    .replace(/&(?:lsquo|rsquo|apos);/g, "'")
    .replace(/&(?:ldquo|rdquo|quot);/g, '"')
    .replace(/&(?:ndash|mdash);/g, "–")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}

function cellText(html: string): string {
  return decodeEntities(html.replace(/<[^>]+>/g, " ")).replace(/\s+/g, " ").trim();
}

interface HtmlTable {
  headers: string[];
  rows: string[][];
}

function parseHtmlTables(html: string): HtmlTable[] {
  const tables: HtmlTable[] = [];
  for (const table of html.matchAll(/<table[^>]*>([\s\S]*?)<\/table>/gi)) {
    const headers: string[] = [];
    const rows: string[][] = [];
    for (const row of table[1].matchAll(/<tr[^>]*>([\s\S]*?)<\/tr>/gi)) {
      const ths = [...row[1].matchAll(/<th[^>]*>([\s\S]*?)<\/th>/gi)].map((c) => cellText(c[1]));
      const tds = [...row[1].matchAll(/<td[^>]*>([\s\S]*?)<\/td>/gi)].map((c) => cellText(c[1]));
      if (ths.length && !headers.length) headers.push(...ths);
      else if (tds.length) rows.push(tds);
    }
    if (rows.length) tables.push({ headers, rows });
  }
  return tables;
}

// "F - 57", "M – 120kg+", "F - 84+", "SUPER HEAVYWEIGHT M - 120" → gender + class
function parseIrishPfClass(cell: string): { gender: string; weightClass: string } | null {
  const m = cell.match(/(?<![A-Za-z])([MF])\s*[-–—]\s*(\d{2,3})\s*(?:kg)?\s*(\+?)/i);
  if (!m) return null;
  return {
    gender: m[1].toUpperCase() === "F" ? "FEMALE" : "MALE",
    weightClass: m[2] + m[3],
  };
}

const MONTHS = [
  "january", "february", "march", "april", "may", "june",
  "july", "august", "september", "october", "november", "december",
];

// A trailing "– 16th" style second day, which we ignore in favour of day one.
const SECOND_DAY = String.raw`(?:\s*(?:[–—-]|and|&|to)\s*\d{1,2}(?:st|nd|rd|th)?)?`;
// "15th – 16th of August 2026", "5th of July 2026", "16th and 17th of May 2026"
const DAY_FIRST = new RegExp(
  String.raw`(\d{1,2})(?:st|nd|rd|th)?${SECOND_DAY}\s*(?:of\s+)?(${MONTHS.join("|")})\s+(\d{4})`,
  "i"
);
// "February 7th – 8th 2026"
const MONTH_FIRST = new RegExp(
  String.raw`(${MONTHS.join("|")})\s+(\d{1,2})(?:st|nd|rd|th)?${SECOND_DAY},?\s+(\d{4})`,
  "i"
);

function matchDate(scope: string): string {
  const dayFirst = scope.match(DAY_FIRST);
  const monthFirst = scope.match(MONTH_FIRST);
  let day: string, monthName: string, year: string;
  if (dayFirst && (!monthFirst || dayFirst.index! <= monthFirst.index!)) {
    [, day, monthName, year] = dayFirst;
  } else if (monthFirst) {
    [, monthName, day, year] = monthFirst;
  } else {
    return "";
  }
  const month = MONTHS.indexOf(monthName.toLowerCase()) + 1;
  return `${year}-${String(month).padStart(2, "0")}-${day.padStart(2, "0")}`;
}

function parseIrishPfDate(text: string): string {
  // Prefer a date near the "will take place on …" sentence over any other date
  // that happens to appear elsewhere on the page.
  const intro = text.match(/(?:takes?\s+place|be\s+held|will\s+run)[\s\S]{0,200}/i);
  return (intro && matchDate(intro[0])) || matchDate(text);
}

function parseIrishPfName(html: string): string {
  const h1 = html.match(/<h1[^>]*class="[^"]*fl-post-title[^"]*"[^>]*>([\s\S]*?)<\/h1>/i);
  const title = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
  const raw = h1 ? cellText(h1[1]) : title ? cellText(title[1]) : "";
  return raw
    .replace(/\s*[–—-]\s*Irish Powerlifting Federation\s*$/i, "")
    .replace(/\s*[–—-]\s*(Athlete Information|Entry List|Flight (Information|List)).*$/i, "")
    .trim();
}

async function getIrishPfMeet(path: string): Promise<{ meet: MeetInfo; lifters: Lifter[] }> {
  const res = await fetch(`https://${IRISHPF_HOST}${path}/`, {
    headers: { Accept: "text/html", "User-Agent": UA },
  });
  if (!res.ok) throw new Error(`IrishPF returned ${res.status} fetching that page`);
  const html = await res.text();

  const entryTables = parseHtmlTables(html).filter(
    (t) => t.headers.some((h) => /^name$/i.test(h)) && t.headers.some((h) => /^(flight|lot)$/i.test(h))
  );

  const lifters: Lifter[] = [];
  for (const table of entryTables) {
    const idx = (re: RegExp) => table.headers.findIndex((h) => re.test(h));
    const nameIdx = idx(/^name$/i);
    const flightIdx = idx(/^flight$/i);
    const lotIdx = idx(/^lot$/i);
    const sessionIdx = idx(/^session$/i);

    for (const row of table.rows) {
      const name = nameIdx >= 0 ? row[nameIdx] : "";
      if (!name) continue;
      const cls = row.map(parseIrishPfClass).find((c) => c !== null) ?? null;
      lifters.push({
        name,
        // Fall back to the session so a flightless entry list still splits into
        // sensibly sized squads.
        flight: (flightIdx >= 0 ? row[flightIdx] : "") || (sessionIdx >= 0 ? row[sessionIdx] : "") || "?",
        gender: cls?.gender ?? "MALE",
        weightClass: cls?.weightClass ?? "",
        lot: lotIdx >= 0 ? row[lotIdx] : "",
      });
    }
  }

  const text = decodeEntities(
    html.replace(/<(script|style)[^>]*>[\s\S]*?<\/\1>/gi, "").replace(/<[^>]+>/g, " ")
  ).replace(/\s+/g, " ");

  return {
    meet: {
      name: parseIrishPfName(html) || "IrishPF Competition",
      federation: "IrishPF",
      date: parseIrishPfDate(text),
    },
    lifters,
  };
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
  const results: OplResult[] = [];
  for (const row of ranksData.rows ?? []) {
    const result = rowToResult(row, name);
    if (result) results.push(result);
  }
  return sortByConfidence(results);
}

const CONFIDENCE_ORDER: Record<string, number> = { high: 0, medium: 1, low: 2 };

function sortByConfidence(results: OplResult[]): OplResult[] {
  return results.sort(
    (a, b) => (CONFIDENCE_ORDER[a.confidence] ?? 3) - (CONFIDENCE_ORDER[b.confidence] ?? 3)
  );
}

// A rankings row → a candidate, scored against the name we searched for.
// Returns null when the row has nothing in common with the name.
function rowToResult(row: unknown[], name: string): OplResult | null {
  if (!Array.isArray(row) || row.length < 4) return null;
  const oplName = String(row[2]);
  const oplSlug = String(row[3]);
  const oplLower = oplName.toLowerCase();
  const nameLower = name.toLowerCase();
  const words = nameLower.split(" ").filter((w) => w.length > 2);

  // OpenPowerlifting disambiguates people sharing a name as "David Walsh #2";
  // that is still an exact name match as far as the user is concerned.
  const bare = oplLower.replace(/\s*#\d+\s*$/, "");

  let confidence: OplResult["confidence"];
  if (bare === nameLower) confidence = "high";
  else if (words.length > 0 && words.every((w) => oplLower.includes(w))) confidence = "medium";
  else if (words.some((w) => oplLower.includes(w))) confidence = "low";
  else return null;

  return {
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
  };
}

// Walk every search hit for a name rather than skimming the 25 rankings rows
// after the first one. That is what surfaces the other people sharing a name
// — "David Walsh #1/#2/#3" are far apart in the rankings, so the window used
// by searchOplGender only ever sees one of them.
async function walkOplMatches(
  name: string,
  genderPath: string,
  base: string,
  limit: number
): Promise<OplResult[]> {
  const q = encodeURIComponent(name);
  const results: OplResult[] = [];
  let start = 0;

  for (let i = 0; i < limit; i++) {
    const searchRes = await fetch(
      `${base}/search/rankings/${genderPath}?q=${q}&start=${start}&lang=en&units=kg`,
      { headers: { "User-Agent": UA } }
    );
    if (!searchRes.ok) break;
    const searchData = (await searchRes.json()) as { next_index?: number | null };
    const idx = searchData.next_index;
    if (idx == null) break;

    const rowRes = await fetch(
      `${base}/rankings/${genderPath}?start=${idx}&end=${idx}&lang=en&units=kg`,
      { headers: { "User-Agent": UA } }
    );
    if (!rowRes.ok) break;
    const rowData = (await rowRes.json()) as { rows?: unknown[][] };
    const result = rowData.rows?.[0] ? rowToResult(rowData.rows[0], name) : null;
    if (result) results.push(result);

    start = idx + 1;
  }
  return results;
}

// Candidates for the manual picker: the same person's namesakes, best first.
async function findCandidates(
  name: string,
  gender: string,
  base: string,
  limit: number
): Promise<OplResult[]> {
  const [primary, secondary] = genderPaths(gender);
  let results = await walkOplMatches(name, primary, base, limit);
  if (results.length < limit) {
    const other = await walkOplMatches(name, secondary, base, limit - results.length);
    results = results.concat(other);
  }

  const seen = new Set<string>();
  const unique = results.filter((r) => {
    if (!r.slug || seen.has(r.slug)) return false;
    seen.add(r.slug);
    return true;
  });
  return sortByConfidence(unique).slice(0, limit);
}

// Which rankings to search first, given a gender from the entry list.
function genderPaths(gender: string): [string, string] {
  const g = gender.toUpperCase();
  const isFemale = g === "FEMALE" || g === "F" || g === "WOMEN";
  return isFemale ? ["women", "men"] : ["men", "women"];
}

async function searchOpl(name: string, gender: string, base: string): Promise<OplResult[]> {
  const [primary, secondary] = genderPaths(gender);
  let results = await searchOplGender(name, primary, base);
  if (!results.length) results = await searchOplGender(name, secondary, base);
  return results.slice(0, 3);
}

// ---------------------------------------------------------------------------
// OPL slug direct lookup (for manual entry)
// ---------------------------------------------------------------------------

// The subset of an OplResult that describes a lifter's record rather than the
// match itself, so it can be swapped wholesale onto a result or a candidate.
interface LifterBests {
  weightClass: string;
  total: string;
  glPoints: string;
  squat: string;
  bench: string;
  deadlift: string;
  federation: string;
  equipment: string;
}

type SlugInfo = LifterBests & { name: string };

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

// Places that don't count towards a personal best. Same set as the apps use in
// PrCalculator, so every client agrees on what a "best" is.
const DQ_PLACES = new Set(["DQ", "DD", "DNS", "NS", "G"]);

const NO_BESTS: LifterBests = {
  weightClass: "", total: "", glPoints: "", squat: "",
  bench: "", deadlift: "", federation: "", equipment: "",
};

function maxOf(meets: MeetEntry[], pick: (m: MeetEntry) => string): string {
  let best = 0;
  for (const m of meets) {
    const n = parseFloat(pick(m));
    if (n > best) best = n;
  }
  return best > 0 ? String(best) : "";
}

// Career bests, each lift taken independently — a squat from one meet and a
// bench from another is the right answer for "best ever". Weight class,
// federation and equipment describe where the lifter is now, so those come from
// the most recent meet rather than being maximised.
function bestsFromMeets(meets: MeetEntry[]): LifterBests {
  const valid = meets.filter((m) => !DQ_PLACES.has(m.place));
  if (!valid.length) return NO_BESTS;
  // getLifterHistory returns meets newest first.
  const latest = valid[0];
  return {
    weightClass: latest.weightClass,
    federation: latest.federation,
    equipment: latest.equipment,
    total: maxOf(valid, (m) => m.total),
    glPoints: maxOf(valid, (m) => m.glPoints),
    squat: maxOf(valid, (m) => m.squat),
    bench: maxOf(valid, (m) => m.bench),
    deadlift: maxOf(valid, (m) => m.deadlift),
  };
}

// A rankings row is a single meet — the lifter's best by Goodlift — so every
// number on it comes from that one day. Swap in the career bests instead, and
// keep the row as-is if the CSV can't be read.
async function withBests(result: OplResult, base: string): Promise<OplResult> {
  if (!result.slug) return result;
  try {
    const history = await getLifterHistory(result.slug, base);
    if (history) return { ...result, ...bestsFromMeets(history.meets), bests: true };
  } catch { /* fall back to the rankings row */ }
  return result;
}

async function lookupOplSlug(slug: string, base: string): Promise<SlugInfo | null> {
  const history = await getLifterHistory(slug, base);
  if (!history) return null;
  return { name: history.name, ...bestsFromMeets(history.meets) };
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

    // The UI is a static asset (public/index.html) served ahead of this
    // handler, so everything reaching here is an API call.

    // ── GET /api/meet?url=<liftingcastUrl> ────────────────────────────────

    if (url.pathname === "/api/meet") {
      const meetUrl = url.searchParams.get("url");
      if (!meetUrl) return jsonRes({ error: "Missing url parameter" }, 400, c);

      const irishPfPath = parseIrishPfUrl(meetUrl);
      if (irishPfPath) {
        try {
          const { meet, lifters } = await getIrishPfMeet(irishPfPath);
          if (!lifters.length) {
            return jsonRes({ error: "No entry list found on that IrishPF page" }, 404, c);
          }
          return jsonRes(
            { meet, lifters, meetId: irishPfPath, provider: "irishpf" },
            200,
            { ...c, "Cache-Control": "no-store" }
          );
        } catch (err) {
          return jsonRes({ error: String(err) }, 502, c);
        }
      }

      if (meetUrl.toLowerCase().includes(IRISHPF_HOST)) {
        return jsonRes(
          { error: "Paste the URL of a specific IrishPF competition page, e.g. .../august-open-2026/" },
          400,
          c
        );
      }

      const parsed = parseLiftingcastUrl(meetUrl);
      if (!parsed) return jsonRes({ error: "Could not extract a meet ID from that URL" }, 400, c);

      try {
        const [meet, lifters] = await Promise.all([
          getLiftingcastMeet(parsed.meetId),
          getLiftingcastLifters(parsed.meetId, parsed.platformId),
        ]);
        if (!lifters.length) return jsonRes({ error: "No lifters found for this meet / platform" }, 404, c);
        return jsonRes({ meet, lifters, meetId: parsed.meetId, provider: "liftingcast" }, 200, { ...c, "Cache-Control": "no-store" });
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
        // Only the top hit reaches a card, and one CSV fetch per lifter across a
        // whole entry list is already a lot of traffic for OPL — leave the rest
        // of the shortlist on its rankings-row numbers.
        if (results.length) results[0] = await withBests(results[0], base);
        return jsonRes({ results }, 200, c);
      } catch {
        return jsonRes({ results: [] }, 200, c);
      }
    }

    // ── GET /api/candidates?name=<name>&gender=<gender>&limit=<n> ─────────
    //
    // Like /api/resolve, but walks every search hit instead of scoring one
    // window of rankings rows, so people sharing a name all show up. Costs a
    // couple of round trips per candidate, so it backs the manual picker
    // rather than the bulk resolve.

    if (url.pathname === "/api/candidates") {
      const name = url.searchParams.get("name") ?? "";
      const gender = url.searchParams.get("gender") ?? "MALE";
      if (!name.trim()) return jsonRes({ results: [] }, 200, c);

      const requested = Number(url.searchParams.get("limit") ?? 5);
      const limit = Math.min(Math.max(Number.isFinite(requested) ? requested : 5, 1), 8);

      try {
        const source = url.searchParams.get("source") ?? "opl";
        const base = source === "ipf" ? IPF_BASE : OPL_BASE;
        const results = await findCandidates(name.trim(), gender, base, limit);
        // The picker compares candidates side by side, so they all need bests —
        // this is one manual action, not a whole entry list.
        const enriched = await Promise.all(results.map((r) => withBests(r, base)));
        return jsonRes({ results: enriched }, 200, c);
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
        // Tells the page these numbers are career bests, so it can leave an
        // older deployment's results marked for a retry instead of trusting
        // them. Keeps a page rollout independent of an API rollout.
        return jsonRes({ slug, ...info, bests: true }, 200, c);
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
