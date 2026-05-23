#!/usr/bin/env python3
"""
dev-bundle-fetcher.py
─────────────────────
Build-time fetcher that pre-downloads static data from our backends
and bundles it inside the dev-flavor APK so the DEV build doesn't
touch the network for these payloads at runtime. Canada / official
builds are untouched.

What's baked
────────────
• Sports                              hushtv.xyz/api/sports/home,
  /league/{nhl,nba,mlb,nfl,ufc,epl,mls,ncaab,ncaaf}, /ppv
• Themes pack                         hushtv.xyz/themes_pack.json
  (already bundled in app/src/main/assets — this script refreshes
  it in case the deployed pack has drifted)
• TMDB top-popular metadata           top 100 popular movies + top
  100 popular TV shows from TMDB Discover, with full detail
  (poster_path, backdrop_path, overview, runtime, genres). The
  Android client checks the bundle first before hitting TMDB for
  any of these IDs.

Output
──────
• /app/androidtv/app/src/dev/assets/dev_only/sports_home.json
• /app/androidtv/app/src/dev/assets/dev_only/sports_ppv.json
• /app/androidtv/app/src/dev/assets/dev_only/sports_league_<slug>.json
• /app/androidtv/app/src/dev/assets/dev_only/tmdb_top_movies.json
• /app/androidtv/app/src/dev/assets/dev_only/tmdb_top_tv.json
• /app/androidtv/app/src/dev/assets/dev_only/manifest.json
   (versioned manifest so the Android loader can refuse stale bundles)
• /app/androidtv/app/src/main/assets/themes_pack.json   (refreshed)

Run manually:
  python3 /app/_buildenv/dev-bundle-fetcher.py
"""

import datetime as dt
import json
import os
import sys
import urllib.error
import urllib.request

# ── Config ─────────────────────────────────────────────────────────
HUSHTV = "https://hushtv.xyz"
TMDB = "https://api.themoviedb.org/3"
TMDB_KEY = "8b6f95c2b9bb0121d1b43f88dc65f52d"

LEAGUE_SLUGS = ["nhl", "nba", "mlb", "nfl", "ufc", "epl", "mls", "ncaab", "ncaaf"]
LEAGUE_DAYS = 14  # how far ahead to fetch games per league

TMDB_MOVIES_PAGES = 5  # 20 results per page × 5 = top 100 popular movies
TMDB_TV_PAGES = 5

OUT_DEV = "/app/androidtv/app/src/dev/assets/dev_only"
OUT_MAIN_THEMES = "/app/androidtv/app/src/main/assets/themes_pack.json"

TIMEOUT = 15  # seconds per request


# ── HTTP helpers ───────────────────────────────────────────────────
def http_json(url: str) -> dict:
    """Fetch a URL and parse it as JSON. Raises on HTTP/JSON error."""
    req = urllib.request.Request(url, headers={"User-Agent": "hushtv-dev-bundler/1.0"})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
        if r.status != 200:
            raise RuntimeError(f"HTTP {r.status} for {url}")
        return json.loads(r.read().decode("utf-8"))


def http_text(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "hushtv-dev-bundler/1.0"})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
        if r.status != 200:
            raise RuntimeError(f"HTTP {r.status} for {url}")
        return r.read().decode("utf-8")


def write_json(path: str, payload) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, separators=(",", ":"), ensure_ascii=False)
    size_kb = os.path.getsize(path) / 1024.0
    print(f"    ↳ wrote {path}  ({size_kb:.1f} KB)")


# ── Sports ─────────────────────────────────────────────────────────
def fetch_sports() -> dict:
    """Returns a manifest entry summary for the sports section."""
    print("[sports] fetching home + ppv + leagues …")
    home = http_json(f"{HUSHTV}/api/sports/home")
    write_json(os.path.join(OUT_DEV, "sports_home.json"), home)

    ppv = http_json(f"{HUSHTV}/api/sports/ppv")
    write_json(os.path.join(OUT_DEV, "sports_ppv.json"), ppv)

    leagues_meta = {}
    for slug in LEAGUE_SLUGS:
        try:
            data = http_json(f"{HUSHTV}/api/sports/league/{slug}?days={LEAGUE_DAYS}")
            write_json(os.path.join(OUT_DEV, f"sports_league_{slug}.json"), data)
            leagues_meta[slug] = {"games": len(data.get("games") or [])}
        except (urllib.error.URLError, urllib.error.HTTPError, RuntimeError) as e:
            print(f"    ! {slug} skipped: {e}", file=sys.stderr)
            leagues_meta[slug] = {"error": str(e)}

    return {
        "home": {"hero": len(home.get("hero") or []), "leagues": len(home.get("leagues") or [])},
        "ppv": {"events": len(ppv.get("events") or [])},
        "leagues": leagues_meta,
    }


# ── TMDB popular metadata ──────────────────────────────────────────
def _tmdb_discover_popular(kind: str, pages: int) -> list:
    """Walk TMDB discover endpoint to collect popular items, then
    fetch the detail page for each so the bundled payload has full
    poster_path / backdrop_path / overview / runtime / genres."""
    discover_path = "/discover/movie" if kind == "movie" else "/discover/tv"
    detail_kind = "movie" if kind == "movie" else "tv"
    ids = []
    for page in range(1, pages + 1):
        url = (
            f"{TMDB}{discover_path}?api_key={TMDB_KEY}&sort_by=popularity.desc"
            f"&page={page}&include_adult=false"
        )
        resp = http_json(url)
        for hit in resp.get("results") or []:
            tmdb_id = hit.get("id")
            if isinstance(tmdb_id, int):
                ids.append(tmdb_id)
    print(f"[tmdb] {kind} discover: {len(ids)} ids across {pages} page(s)")

    details = []
    for i, tid in enumerate(ids):
        try:
            d = http_json(f"{TMDB}/{detail_kind}/{tid}?api_key={TMDB_KEY}")
            if kind == "movie":
                # Shape matches TmdbMovie Moshi model EXACTLY so the
                # loader can use the same adapter as live network calls.
                # Heavy fields (credits, recommendations, external_ids,
                # videos) are deliberately omitted — those still hit
                # network when the user opens the detail screen.
                details.append({
                    "id": d.get("id") or 0,
                    "title": d.get("title") or "",
                    "original_title": d.get("original_title"),
                    "overview": d.get("overview") or "",
                    "tagline": d.get("tagline"),
                    "poster_path": d.get("poster_path"),
                    "backdrop_path": d.get("backdrop_path"),
                    "release_date": d.get("release_date"),
                    "runtime": d.get("runtime"),
                    "vote_average": d.get("vote_average") or 0.0,
                    "vote_count": d.get("vote_count") or 0,
                    "genres": [
                        {"id": g.get("id") or 0, "name": g.get("name") or ""}
                        for g in (d.get("genres") or [])
                    ],
                })
            else:
                # Shape matches TmdbTv Moshi model.
                details.append({
                    "id": d.get("id") or 0,
                    "name": d.get("name") or "",
                    "original_name": d.get("original_name"),
                    "overview": d.get("overview") or "",
                    "tagline": d.get("tagline"),
                    "poster_path": d.get("poster_path"),
                    "backdrop_path": d.get("backdrop_path"),
                    "first_air_date": d.get("first_air_date"),
                    "last_air_date": d.get("last_air_date"),
                    "episode_run_time": d.get("episode_run_time") or [],
                    "number_of_seasons": d.get("number_of_seasons") or 0,
                    "number_of_episodes": d.get("number_of_episodes") or 0,
                    "vote_average": d.get("vote_average") or 0.0,
                    "vote_count": d.get("vote_count") or 0,
                    "genres": [
                        {"id": g.get("id") or 0, "name": g.get("name") or ""}
                        for g in (d.get("genres") or [])
                    ],
                    "seasons": [
                        {
                            "id": s.get("id") or 0,
                            "name": s.get("name") or "",
                            "season_number": s.get("season_number") or 0,
                            "episode_count": s.get("episode_count") or 0,
                            "poster_path": s.get("poster_path"),
                            "air_date": s.get("air_date"),
                            "overview": s.get("overview"),
                        }
                        for s in (d.get("seasons") or [])
                    ],
                })
        except (urllib.error.URLError, urllib.error.HTTPError, RuntimeError) as e:
            print(f"    ! tmdb {detail_kind} {tid} skipped: {e}", file=sys.stderr)
        if (i + 1) % 25 == 0:
            print(f"    … {i + 1}/{len(ids)}")
    return details


def fetch_tmdb() -> dict:
    print("[tmdb] fetching popular movies …")
    movies = _tmdb_discover_popular("movie", TMDB_MOVIES_PAGES)
    write_json(os.path.join(OUT_DEV, "tmdb_top_movies.json"), {"items": movies})

    print("[tmdb] fetching popular tv …")
    tv = _tmdb_discover_popular("tv", TMDB_TV_PAGES)
    write_json(os.path.join(OUT_DEV, "tmdb_top_tv.json"), {"items": tv})
    return {"movies": len(movies), "tv": len(tv)}


# ── Themes pack refresh ────────────────────────────────────────────
def fetch_themes_pack() -> dict:
    print("[themes_pack] refreshing bundled copy …")
    text = http_text(f"{HUSHTV}/themes_pack.json")
    os.makedirs(os.path.dirname(OUT_MAIN_THEMES), exist_ok=True)
    with open(OUT_MAIN_THEMES, "w", encoding="utf-8") as f:
        f.write(text)
    size_kb = os.path.getsize(OUT_MAIN_THEMES) / 1024.0
    print(f"    ↳ wrote {OUT_MAIN_THEMES}  ({size_kb:.1f} KB)")
    parsed = json.loads(text)
    return {"version": parsed.get("version"), "themes": len(parsed.get("themes") or [])}


# ── Manifest ───────────────────────────────────────────────────────
def write_manifest(summary: dict) -> None:
    summary["generated_at_utc"] = dt.datetime.now(dt.timezone.utc).isoformat()
    summary["schema"] = 1
    write_json(os.path.join(OUT_DEV, "manifest.json"), summary)


# ── Entry point ────────────────────────────────────────────────────
def main():
    print(f"OUT_DEV = {OUT_DEV}")
    os.makedirs(OUT_DEV, exist_ok=True)

    summary = {}
    summary["sports"] = fetch_sports()
    summary["tmdb"] = fetch_tmdb()
    summary["themes_pack"] = fetch_themes_pack()
    write_manifest(summary)
    print("\n[done] dev bundle fetch complete.")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
