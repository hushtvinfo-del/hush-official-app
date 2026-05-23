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
• /app/androidtv/app/src/dev/assets/dev_only/img/<m|t><id>_<p|b>.webp
   (top 100 popular movies + top 100 tv, poster + backdrop each =
    up to 400 images at WebP q=80, ~15-20 MB total)
• /app/androidtv/app/src/dev/assets/dev_only/img_map.json
   ({"/abc123.jpg": "dev_only/img/m54321_p.webp"} — keyed by the
   TMDB path so any size variant Coil requests resolves to the
   same bundled WebP)
• /app/androidtv/app/src/dev/assets/dev_only/manifest.json
   (versioned manifest so the Android loader can refuse stale bundles)
• /app/androidtv/app/src/main/assets/themes_pack.json   (refreshed)

Run manually:
  python3 /app/_buildenv/dev-bundle-fetcher.py
"""

import datetime as dt
import io
import json
import os
import sys
import urllib.error
import urllib.request

try:
    from PIL import Image
except ImportError as _e:
    print(f"FATAL: Pillow required for image bake: {_e}", file=sys.stderr)
    sys.exit(1)

# ── Config ─────────────────────────────────────────────────────────
HUSHTV = "https://hushtv.xyz"
TMDB = "https://api.themoviedb.org/3"
TMDB_KEY = "8b6f95c2b9bb0121d1b43f88dc65f52d"

LEAGUE_SLUGS = ["nhl", "nba", "mlb", "nfl", "ufc", "epl", "mls", "ncaab", "ncaaf"]
LEAGUE_DAYS = 14  # how far ahead to fetch games per league

TMDB_MOVIES_PAGES = 5  # 20 results per page × 5 = top 100 popular movies
TMDB_TV_PAGES = 5

# ── Image bake (Phase 2) ──────────────────────────────────────────
# Source-side TMDB image size we download. w780 is the sweet spot:
# • Poster (sized to display ~300 px wide on TV) — w780 downscales
#   cleanly without aliasing.
# • Backdrop (sized to display ~1920 px wide as full-bleed hero) —
#   w780 is slightly soft but ~10× smaller than 'original' and the
#   user-perceived difference at TV viewing distance is minimal.
# WebP quality 80 is the size/quality knee — saves ~70% vs JPG at
# visually-identical fidelity in our blind test.
TMDB_IMAGE_SRC_SIZE = "w780"
WEBP_QUALITY = 80
# How many of the top-popular movies/tv to bake images for. 100 each
# = 400 images total (poster + backdrop × 100 × 2). At ~40 KB / WebP
# that's ~16 MB of APK weight on the dev flavor.
BAKE_IMG_LIMIT_MOVIES = 100
BAKE_IMG_LIMIT_TV = 100

OUT_DEV = "/app/androidtv/app/src/dev/assets/dev_only"
OUT_DEV_IMG = "/app/androidtv/app/src/dev/assets/dev_only/img"
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


# ── Image bake ─────────────────────────────────────────────────────
def http_bytes(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "hushtv-dev-bundler/1.0"})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
        if r.status != 200:
            raise RuntimeError(f"HTTP {r.status} for {url}")
        return r.read()


def fetch_webp(remote_url: str, out_path: str) -> int:
    """Fetch [remote_url], decode + re-encode as WebP at WEBP_QUALITY,
    write to [out_path]. Returns bytes written. Raises on failure."""
    raw = http_bytes(remote_url)
    im = Image.open(io.BytesIO(raw))
    # TMDB returns RGB JPGs but some posters have RGBA — normalise.
    if im.mode not in ("RGB", "RGBA"):
        im = im.convert("RGB")
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    im.save(out_path, format="WEBP", quality=WEBP_QUALITY, method=6)
    return os.path.getsize(out_path)


def bake_tmdb_images(movies: list, tv: list) -> dict:
    """Download poster + backdrop for the top-N popular movies / TV
    shows. Returns a {tmdb_path -> asset_relative_path} map written
    to img_map.json so the device-side resolver can short-circuit
    any image.tmdb.org/t/p/{any_size}{path} URL to a local asset.

    Path-based keying means ONE bundled WebP serves every size variant
    Coil requests (w185 grid thumbnails, w500 detail, original hero
    backdrops, etc.) — Coil downscales on-device."""
    os.makedirs(OUT_DEV_IMG, exist_ok=True)
    img_map = {}
    total_bytes = 0
    written = 0
    skipped = 0

    queue = []
    for m in movies[:BAKE_IMG_LIMIT_MOVIES]:
        if m.get("poster_path"):
            queue.append((m["id"], "m", "p", m["poster_path"]))
        if m.get("backdrop_path"):
            queue.append((m["id"], "m", "b", m["backdrop_path"]))
    for t in tv[:BAKE_IMG_LIMIT_TV]:
        if t.get("poster_path"):
            queue.append((t["id"], "t", "p", t["poster_path"]))
        if t.get("backdrop_path"):
            queue.append((t["id"], "t", "b", t["backdrop_path"]))

    total = len(queue)
    print(f"[img] baking {total} TMDB images at {TMDB_IMAGE_SRC_SIZE} → WebP q={WEBP_QUALITY}")
    for i, (tmdb_id, kind, what, path) in enumerate(queue):
        # path looks like "/abc123.jpg" — strip the leading slash for
        # filename safety, keep it as-is for the lookup key.
        fname = f"{kind}{tmdb_id}_{what}.webp"
        out_path = os.path.join(OUT_DEV_IMG, fname)
        if os.path.exists(out_path):
            img_map[path] = f"dev_only/img/{fname}"
            skipped += 1
            continue
        url = f"https://image.tmdb.org/t/p/{TMDB_IMAGE_SRC_SIZE}{path}"
        try:
            written_bytes = fetch_webp(url, out_path)
            total_bytes += written_bytes
            img_map[path] = f"dev_only/img/{fname}"
            written += 1
        except (urllib.error.URLError, urllib.error.HTTPError, RuntimeError, OSError) as e:
            print(f"    ! skipped {url}: {e}", file=sys.stderr)
        if (i + 1) % 25 == 0:
            mb = total_bytes / (1024 * 1024)
            print(f"    … {i + 1}/{total}  ({mb:.1f} MB so far)")

    write_json(os.path.join(OUT_DEV, "img_map.json"), img_map)
    mb = total_bytes / (1024 * 1024)
    print(f"[img] done — wrote {written} new, reused {skipped}, total payload {mb:.1f} MB")
    return {
        "entries": len(img_map),
        "written": written,
        "skipped_cached": skipped,
        "total_mb": round(mb, 2),
        "src_size": TMDB_IMAGE_SRC_SIZE,
        "quality": WEBP_QUALITY,
    }


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


def fetch_tmdb() -> tuple:
    """Returns (summary_dict, movies_list, tv_list). The lists are
    used by bake_tmdb_images() to pick which posters to download."""
    print("[tmdb] fetching popular movies …")
    movies = _tmdb_discover_popular("movie", TMDB_MOVIES_PAGES)
    write_json(os.path.join(OUT_DEV, "tmdb_top_movies.json"), {"items": movies})

    print("[tmdb] fetching popular tv …")
    tv = _tmdb_discover_popular("tv", TMDB_TV_PAGES)
    write_json(os.path.join(OUT_DEV, "tmdb_top_tv.json"), {"items": tv})
    return ({"movies": len(movies), "tv": len(tv)}, movies, tv)


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
    tmdb_summary, movies, tv = fetch_tmdb()
    summary["tmdb"] = tmdb_summary
    summary["images"] = bake_tmdb_images(movies, tv)
    summary["themes_pack"] = fetch_themes_pack()
    write_manifest(summary)
    print("\n[done] dev bundle fetch complete.")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
