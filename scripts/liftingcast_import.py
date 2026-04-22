#!/usr/bin/env python3
"""
liftingcast_import.py — Import a liftingcast competition into MyLiftSquad.

Two-step workflow:

  Step 1: Fetch meet data and search OpenPowerlifting for athlete slugs
    python liftingcast_import.py <url>
    → Writes liftingcast_<meetId>.csv for review

  Step 2: Generate MyLiftSquad share codes from the reviewed CSV
    python liftingcast_import.py <url> --generate
    → Prints one 6-character import code per flight

Example:
  python liftingcast_import.py \\
    https://liftingcast.com/meets/mmx3chstivvq/platforms/pfbm42g4bl5p/board

The URL can include a /platforms/<id> segment to filter to one platform.
If omitted, all lifters in the meet are included.

Squad names are formatted as: "<federation> - <YYYY-MM-DD> - Flight <X>"

CSV columns:
  flight            Flight letter (A, B, C, …)
  liftingcast_name  Lifter name as entered in liftingcast
  opl_slug          OpenPowerlifting URL slug — VERIFY THIS IS CORRECT
  opl_name          Name as it appears on OpenPowerlifting
  confidence        high / medium / low / none
  include           yes = include in squad, no = skip

Requires Python 3.10+ (stdlib only — no third-party packages needed).
"""

from __future__ import annotations

import sys
import csv
import json
import re
import time
import argparse
import urllib.request
import urllib.parse
from pathlib import Path
from collections import defaultdict

COUCHDB_BASE = "https://couchdb.liftingcast.com"
OPL_BASE     = "https://www.openpowerlifting.org/api"
SHARE_API    = "https://myliftsquad-api.gooseyirl.workers.dev"


# ---------------------------------------------------------------------------
# URL parsing
# ---------------------------------------------------------------------------

def parse_url(url: str) -> tuple[str, str | None]:
    """Return (meetId, platformId | None) from any liftingcast meet URL."""
    m = re.search(r'/meets/([A-Za-z0-9]+)(?:/platforms/([A-Za-z0-9]+))?', url)
    if not m:
        sys.exit(f"ERROR: Could not extract a meet ID from: {url}")
    return m.group(1), m.group(2)


# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

def _get(url: str, *, retries: int = 3) -> dict | list:
    headers = {
        "Accept": "application/json",
        "User-Agent": "myliftsquad-liftingcast-import/1.0",
    }
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=20) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as exc:
            if attempt == retries - 1:
                raise RuntimeError(f"GET {url} — {exc}") from exc
            time.sleep(1.5 ** attempt)
    raise RuntimeError("unreachable")


def _post(url: str, payload: dict) -> dict:
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url, data=body, method="POST",
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


# ---------------------------------------------------------------------------
# Liftingcast data
# ---------------------------------------------------------------------------

def fetch_meet(meet_id: str) -> dict:
    """Fetch the meet document (federation, date, name)."""
    return _get(f"{COUCHDB_BASE}/{meet_id}_readonly/{meet_id}")


def fetch_lifters(meet_id: str) -> list[dict]:
    """
    Fetch all lifter documents from the meet.
    Lifter doc IDs start with 'l', so a CouchDB range query avoids returning
    the 1,200+ attempt documents (IDs start with 'a').
    """
    url = (
        f"{COUCHDB_BASE}/{meet_id}_readonly/_all_docs"
        f"?include_docs=true"
        f"&startkey=%22l%22"           # startkey="l"
        f"&endkey=%22l%EF%BF%B0%22"   # endkey="l\ufff0"  (CouchDB range sentinel)
    )
    data = _get(url)
    return [
        row["doc"]
        for row in data.get("rows", [])
        if not row["id"].startswith("_design")
    ]


def normalise_date(raw: str) -> str:
    """Convert DD/MM/YYYY → YYYY-MM-DD. Returns the value unchanged if already ISO."""
    raw = raw.strip()
    m = re.match(r'^(\d{1,2})/(\d{1,2})/(\d{4})$', raw)
    if m:
        return f"{m.group(3)}-{m.group(2).zfill(2)}-{m.group(1).zfill(2)}"
    if re.match(r'^\d{4}-\d{2}-\d{2}', raw):
        return raw[:10]
    return raw


# ---------------------------------------------------------------------------
# OpenPowerlifting search
# ---------------------------------------------------------------------------

def _search_opl_gender(name: str, gender_path: str) -> list[dict]:
    """
    Search OPL for a lifter in one gender path using the same two-step API
    the MyLiftSquad app uses: search → next_index → rankings rows.
    """
    encoded = urllib.parse.quote(name)
    try:
        search = _get(
            f"{OPL_BASE}/search/rankings/{gender_path}"
            f"?q={encoded}&start=0&lang=en&units=kg"
        )
    except Exception:
        return []

    next_index = search.get("next_index")
    if next_index is None:
        return []

    try:
        ranks = _get(
            f"{OPL_BASE}/rankings/{gender_path}"
            f"?start={next_index}&end={next_index + 24}&lang=en&units=kg"
        )
    except Exception:
        return []

    name_lower = name.lower()
    # Split into significant words (>2 chars) for partial matching
    words = [w for w in name_lower.split() if len(w) > 2]

    matches = []
    for row in ranks.get("rows", []):
        if len(row) < 4:
            continue
        opl_name: str = str(row[2])
        opl_slug: str = str(row[3])
        opl_lower = opl_name.lower()

        if opl_lower == name_lower:
            confidence = "high"
        elif all(w in opl_lower for w in words):
            confidence = "medium"
        elif any(w in opl_lower for w in words):
            confidence = "low"
        else:
            continue

        matches.append({
            "opl_name": opl_name,
            "opl_slug": opl_slug,
            "confidence": confidence,
        })

    order = {"high": 0, "medium": 1, "low": 2}
    matches.sort(key=lambda c: order.get(c["confidence"], 3))
    return matches


def search_opl(name: str, gender: str) -> list[dict]:
    """
    Search OPL for a lifter. Tries the correct gender first; falls back to
    the other gender if no results are found.
    Returns up to 3 best candidates.
    """
    primary = "men" if gender.upper() in ("MALE", "M") else "women"
    secondary = "women" if primary == "men" else "men"

    results = _search_opl_gender(name, primary)
    time.sleep(0.25)

    if not results:
        results = _search_opl_gender(name, secondary)
        time.sleep(0.25)

    return results[:3]


# ---------------------------------------------------------------------------
# Step 1 — fetch and write CSV
# ---------------------------------------------------------------------------

CSV_FIELDS = ["flight", "liftingcast_name", "opl_slug", "opl_name", "confidence", "include"]


def cmd_fetch(meet_id: str, platform_id: str | None, csv_path: Path, *, federation_override: str | None = None) -> None:
    print(f"\nFetching meet {meet_id} …")
    meet = fetch_meet(meet_id)
    federation = federation_override or meet.get("federation", "Unknown")
    iso_date   = normalise_date(meet.get("date", ""))
    print(f"  Meet:        {meet.get('name', '(unnamed)')}")
    print(f"  Federation:  {federation}")
    print(f"  Date:        {iso_date}")

    print("\nFetching lifters …")
    all_lifters = fetch_lifters(meet_id)
    print(f"  Lifters in meet: {len(all_lifters)}")

    if platform_id:
        lifters = [l for l in all_lifters if l.get("platformId") == platform_id]
        print(f"  On platform {platform_id}: {len(lifters)}")
    else:
        lifters = all_lifters
        print("  (no platform filter — including all platforms)")

    if not lifters:
        sys.exit("\nERROR: No lifters found. Check the URL / platform ID.")

    flights = sorted({l.get("flight", "?") for l in lifters})
    print(f"  Flights: {', '.join(flights)}")

    # Sort lifters: flight A → Z, then name
    lifters.sort(key=lambda l: (l.get("flight", "?"), l.get("name", "")))

    print(f"\nSearching OpenPowerlifting for {len(lifters)} lifters …\n")
    rows = []
    n = len(lifters)

    for i, lifter in enumerate(lifters, 1):
        name   = lifter.get("name", "")
        flight = lifter.get("flight", "?")
        gender = lifter.get("gender", "MALE")

        print(f"  [{i:>3}/{n}] {name:<35}", end="", flush=True)
        candidates = search_opl(name, gender)

        if candidates:
            best = candidates[0]
            flag = "✓" if best["confidence"] == "high" else "~" if best["confidence"] == "medium" else "?"
            print(f"{flag} {best['opl_name']}  [{best['confidence']}]  {best['opl_slug']}")
            rows.append({
                "flight":           flight,
                "liftingcast_name": name,
                "opl_slug":         best["opl_slug"],
                "opl_name":         best["opl_name"],
                "confidence":       best["confidence"],
                "include":          "yes",
            })
        else:
            print("✗ NOT FOUND")
            rows.append({
                "flight":           flight,
                "liftingcast_name": name,
                "opl_slug":         "",
                "opl_name":         "",
                "confidence":       "none",
                "include":          "no",
            })

    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_FIELDS)
        writer.writeheader()
        writer.writerows(rows)

    matched = sum(1 for r in rows if r["include"] == "yes")
    print(f"\n✓ {csv_path}  ({matched}/{n} matched)\n")

    print("Squad names that will be created:")
    for flight in flights:
        print(f"  \"{federation} - {iso_date} - Flight {flight}\"")

    print("""
Review the CSV:
  • Verify each opl_slug is the correct lifter on openpowerlifting.org
  • Correct wrong slugs: find the right one at openpowerlifting.org/<slug>
  • Change include → 'no' for lifters with no OPL record or a wrong match
  • confidence=low or none rows need the most attention

Then generate share codes:
  python liftingcast_import.py <url> --generate
""")


# ---------------------------------------------------------------------------
# Step 2 — generate share codes
# ---------------------------------------------------------------------------

def cmd_generate(meet_id: str, platform_id: str | None, csv_path: Path, *, federation_override: str | None = None) -> None:
    if not csv_path.exists():
        sys.exit(
            f"ERROR: {csv_path} not found.\n"
            f"Run without --generate first to create the CSV."
        )

    print(f"\nFetching meet info …")
    meet = fetch_meet(meet_id)
    federation = federation_override or meet.get("federation", "Unknown")
    iso_date   = normalise_date(meet.get("date", ""))
    print(f"  {meet.get('name', '')}  |  {federation}  |  {iso_date}\n")

    flights: dict[str, list[dict]] = defaultdict(list)
    skipped = 0

    with open(csv_path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row.get("include", "").strip().lower() != "yes":
                skipped += 1
                continue
            slug = row.get("opl_slug", "").strip()
            if not slug:
                print(f"  SKIP (no slug): {row.get('liftingcast_name', '?')}")
                skipped += 1
                continue
            flights[row["flight"].strip()].append({
                "name": row["liftingcast_name"].strip(),
                "slug": slug,
            })

    if not flights:
        sys.exit("ERROR: No athletes with include=yes and a valid opl_slug.")

    total_athletes = sum(len(a) for a in flights.values())
    print(f"Creating share codes for {len(flights)} flight(s), {total_athletes} athlete(s)  "
          f"({skipped} skipped)\n")

    for flight in sorted(flights.keys()):
        athletes  = flights[flight]
        squad_name = f"{federation} - {iso_date} - Flight {flight}"
        print(f"  Flight {flight}  —  {len(athletes)} athletes")
        print(f"  Squad: \"{squad_name}\"")
        try:
            result = _post(f"{SHARE_API}/squads", {"name": squad_name, "athletes": athletes})
            print(f"  Code:  {result['code']}\n")
        except Exception as exc:
            print(f"  ERROR: {exc}\n")

    print("Import these codes in MyLiftSquad via  FAB → Import Squad.")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("url", help="Liftingcast meet URL (board, results, or any page)")
    parser.add_argument(
        "--generate", action="store_true",
        help="Generate share codes from the reviewed CSV (step 2)",
    )
    parser.add_argument(
        "--federation",
        help=(
            "Override the federation name used in squad names. "
            "The meet document stores the parent federation (e.g. 'IPF') but you may "
            "want the local federation (e.g. 'IrishPF'). "
            "Example: --federation IrishPF"
        ),
    )
    args = parser.parse_args()

    meet_id, platform_id = parse_url(args.url)
    csv_path = Path(f"liftingcast_{meet_id}.csv")

    if args.generate:
        cmd_generate(meet_id, platform_id, csv_path, federation_override=args.federation)
    else:
        cmd_fetch(meet_id, platform_id, csv_path, federation_override=args.federation)


if __name__ == "__main__":
    main()
