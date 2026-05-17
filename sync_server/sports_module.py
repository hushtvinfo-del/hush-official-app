"""HushTV sports module — Phase 1 backend.

Lives inside the existing hushsync FastAPI app (port 5056). Exposes
everything under `/api/sports/*`. Ingests TheSportsDB v1/v2 on a
background timer, caches in SQLite, and serves endpoints that the
Android / Mobile clients can hit directly.

Architecture rationale (from the v1.43.99 design review):
  * 3000+ devices can't hit TheSportsDB directly (120 req/min Business
    limit). Instead we do a handful of requests on a timer here, stash
    results in SQLite, and fan out to clients from the cache. Even at
    30,000 users this costs us < 12 req/min against TheSportsDB.

Channel resolution order per game (highest wins):
  1. Per-game admin override  (sports_channel_map row, scope='event')
  2. Team-specific default    (sports_channel_map row, scope='team')
  3. League fallback          (sports_channel_map row, scope='league')
  4. None → game is HIDDEN from `/api/sports/*` responses (per user
     request: "hide entirely" when no channel available).

Schema:
  sports_leagues   id, slug, name, sportsdb_id, accent, display_order, active
  sports_teams     id, league_id, sportsdb_id, name, short_name,
                   logo_url, badge_url, country, home_venue
  sports_games     id, sportsdb_id, league_id, home_team_id, away_team_id,
                   start_utc, status, score_home, score_away,
                   venue, thumb_url, video_url, round, raw_json, updated_ts
  sports_ppv       id, source, source_id, title, subtitle, poster_url,
                   start_utc, status, default_channel, admin_notes, created_ts
  sports_channel_map
                   id, scope (league|team|event|ppv), scope_id,
                   channel_name, priority, active
  sports_active_league
                   slug, active_until_ts (for season-based auto-reorder)

Endpoints:
  GET  /api/sports/health
  GET  /api/sports/home?playlist_id=X&limit=N
      → hero slides + per-league upcoming games, channel-resolved
  GET  /api/sports/league/{slug}?playlist_id=X&days=7
      → games in league grouped by date header (TONIGHT / TOMORROW / ...)
  GET  /api/sports/ppv?playlist_id=X
      → upcoming PPV events
  GET  /api/sports/game/{id}?playlist_id=X
      → one game detail with resolved stream URL

  POST /api/admin/sports/channel_map     body = {scope, scope_id, channel_name}
  DELETE /api/admin/sports/channel_map/{id}
  GET  /api/admin/sports/channel_map?scope=team&league_slug=nhl
  POST /api/admin/sports/ppv             body = {title, subtitle, poster_url, start_iso, default_channel}
  DELETE /api/admin/sports/ppv/{id}
  GET  /api/admin/sports/ppv
  POST /api/admin/sports/league/{slug}/active   body = {active: true/false}
  POST /api/admin/sports/refresh          manual kick of the ingestion loop

Auth (admin endpoints):
  Require header  X-Admin-Token: <value of SPORTS_ADMIN_TOKEN env var>

Note: the admin endpoints are intentionally behind the same internal
nginx route as the public ones (/api/sports/*). We gate with a shared
token instead of moving them to a separate path because:
  • Keeps nginx config trivial (one location block covers everything).
  • Token lives in env so it's rotatable without redeploy.
  • Future: swap for OAuth if an admin panel needs multi-user.
"""
from __future__ import annotations

import json
import logging
import os
import re
import sqlite3
import threading
import time
import urllib.parse
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Iterable, List, Optional, Tuple

import httpx
from fastapi import APIRouter, Header, HTTPException, Query
from pydantic import BaseModel, Field

log = logging.getLogger("hushsports")

# ── Config ─────────────────────────────────────────────────────────
SPORTSDB_KEY = os.environ.get("SPORTSDB_KEY", "3")  # "3" is the free key
SPORTSDB_V1 = f"https://www.thesportsdb.com/api/v1/json/{SPORTSDB_KEY}"
SPORTSDB_V2 = f"https://www.thesportsdb.com/api/v2/json"

# Admin endpoints are gated on this token. Set in the systemd unit.
ADMIN_TOKEN = os.environ.get("SPORTS_ADMIN_TOKEN", "")

# Where the sports SQLite lives. We keep it separate from the sync DB
# so a nuke-and-reseed of sports data doesn't risk sync state.
DB_PATH = os.environ.get("SPORTS_DB", "/var/hushtv-sync/sports.sqlite3")

# Where mirrored team logos live on disk. Served by nginx as
# /sports/teams/{id}.png. We mirror once per team to escape
# TheSportsDB's slow CDN — Android client always loads from us.
LOGO_DIR = os.environ.get("SPORTS_LOGO_DIR", "/var/hushtv-sync/team_logos")
LOGO_PUBLIC_BASE = os.environ.get("SPORTS_LOGO_PUBLIC_BASE",
                                  "https://hushtv.xyz/sports/teams")

# Ingestion cadences.
REFRESH_SCHEDULE_SEC = 15 * 60      # Fetch upcoming schedules
REFRESH_LIVE_SEC = 60               # V2 livescore endpoint — 1 call per league, can poll fast
REFRESH_PPV_SEC = 6 * 60 * 60       # Fetch PPV / UFC / boxing events

# Window we keep games for. Everything else gets pruned to keep the
# home-screen query fast.
GAMES_PAST_DAYS = 2
GAMES_FUTURE_DAYS = 14

# TheSportsDB league IDs we ingest by default. User can disable any
# league via POST /api/admin/sports/league/{slug}/active.
# Source: https://www.thesportsdb.com/api/v1/json/3/all_leagues.php
DEFAULT_LEAGUES: List[Dict[str, Any]] = [
    # slug       name                  sportsdb_id   accent     order
    ("nhl",      "NHL",                 "4380",      "#000000",   2),
    ("mlb",      "MLB",                 "4424",      "#132448",   3),
    ("nba",      "NBA",                 "4387",      "#C8102E",   4),
    ("nfl",      "NFL",                 "4391",      "#013369",   5),
    ("ufc",      "UFC",                 "4443",      "#D20A0A",   1),  # PPV-ish
    ("mls",      "MLS",                 "4346",      "#00205B",   6),
    ("epl",      "Premier League",      "4328",      "#3D195B",   7),
    ("ucl",      "Champions League",    "4480",      "#00387B",   8),
    ("ncaaf",    "NCAA Football",       "4479",      "#BB0000",   9),
    ("ncaab",    "NCAA Basketball",     "4607",      "#BB0000",  10),
    ("cfl",      "CFL",                 "4335",      "#B8860B",  11),
    ("f1",       "Formula 1",           "4370",      "#E10600",  12),
]

# Smart defaults seed — Canadian broadcaster mappings.
# scope=league|team, key=slug or team short_name, channel_name=Xtream-side name
# User can override via the admin panel later, these just bootstrap day-1.
LEAGUE_CHANNEL_SEED: List[Tuple[str, str]] = [
    # league_slug, default Canadian broadcaster
    ("nhl",   "SPORTSNET"),
    ("mlb",   "SPORTSNET"),
    ("nba",   "TSN"),
    ("nfl",   "TSN"),
    ("ufc",   "SPORTSNET PPV"),
    ("mls",   "TSN"),
    ("epl",   "FUBO SPORTS"),
    ("ucl",   "DAZN"),
    ("ncaaf", "TSN"),
    ("ncaab", "TSN"),
    ("cfl",   "TSN"),
    ("f1",    "TSN"),
]

# Team → regional broadcaster. Names match TheSportsDB `strTeam` field
# (we'll also match on short/alternate names). User can edit these.
TEAM_CHANNEL_SEED: List[Tuple[str, str, str]] = [
    # league_slug, team name (TheSportsDB form), channel name
    ("mlb",  "Toronto Blue Jays",      "SPORTSNET ONE"),
    ("nhl",  "Toronto Maple Leafs",    "SPORTSNET ONTARIO"),
    ("nhl",  "Montreal Canadiens",     "TSN 2"),
    ("nhl",  "Ottawa Senators",        "TSN 5"),
    ("nhl",  "Winnipeg Jets",          "TSN 3"),
    ("nhl",  "Calgary Flames",         "SPORTSNET WEST"),
    ("nhl",  "Edmonton Oilers",        "SPORTSNET WEST"),
    ("nhl",  "Vancouver Canucks",      "SPORTSNET PACIFIC"),
    ("nba",  "Toronto Raptors",        "TSN 1"),
    ("mls",  "Toronto FC",             "TSN 4"),
    ("mls",  "CF Montreal",            "TSN 5"),
    ("mls",  "Vancouver Whitecaps FC", "TSN 1"),
]

# ── DB ─────────────────────────────────────────────────────────────
_db_lock = threading.Lock()


_COMPOUND_CITIES = {
    "Los Angeles", "New York", "San Francisco", "San Diego", "San Antonio",
    "San Jose", "Tampa Bay", "Kansas City", "St. Louis", "New Orleans",
    "New England", "Golden State", "Oklahoma City", "Salt Lake",
}


def _derive_short_name(full: Optional[str]) -> str:
    """Cheap heuristic to extract a TV-friendly short team name from
    the full TheSportsDB name. Goal: 'Chicago White Sox' → 'White Sox',
    'Los Angeles Angels' → 'Angels', 'New York Knicks' → 'Knicks'.

    Rules (in order):
      1. ≤1 word → return as-is.
      2. 3+ words with first 2 matching a known compound city
         ('Los Angeles', 'New York', etc.) → drop both. Yields
         'Angels' / 'Yankees' / 'Knicks' / 'Lightning'.
      3. 3+ words otherwise → drop just the first word. Yields
         'White Sox' / 'Maple Leafs' / 'Golden Knights' / 'Pirates'.
      4. 2 words AND the full string is more than 12 chars → drop
         the first word. Yields 'Sabres' / 'Ducks' / 'Pirates'.
      5. 2 words AND ≤12 chars → keep as-is. Preserves soccer-style
         brand names like 'Real Madrid', 'Paris SG', 'Inter Miami',
         'Bayern Munich', 'Toronto FC' which would otherwise become
         meaningless ('Madrid', 'SG', 'Miami', etc.).
    """
    s = (full or "").strip()
    if not s:
        return ""
    words = s.split()
    if len(words) <= 1:
        return s
    head = " ".join(words[:2])
    if len(words) >= 3 and head in _COMPOUND_CITIES:
        return " ".join(words[2:])
    if len(words) >= 3:
        return " ".join(words[1:])
    # 2-word case
    if len(s) > 13:
        return words[1]
    return s


def _ensure_db_dir() -> None:
    d = os.path.dirname(DB_PATH)
    if d and not os.path.isdir(d):
        os.makedirs(d, exist_ok=True)
    if LOGO_DIR and not os.path.isdir(LOGO_DIR):
        os.makedirs(LOGO_DIR, exist_ok=True)


def _mirror_logo(sportsdb_id: str, src_url: Optional[str]) -> Optional[str]:
    """Download a team logo to disk if not already mirrored. Returns
    the public URL (`hushtv.xyz/sports/teams/{id}.png`) or None if
    we have nothing to mirror.

    Idempotent: if the file already exists at the target path we just
    return its public URL without re-downloading.
    """
    if not sportsdb_id or not src_url:
        return None
    safe_id = "".join(ch for ch in str(sportsdb_id) if ch.isalnum())
    if not safe_id:
        return None
    target_path = os.path.join(LOGO_DIR, f"{safe_id}.png")
    public_url = f"{LOGO_PUBLIC_BASE}/{safe_id}.png"
    if os.path.isfile(target_path) and os.path.getsize(target_path) > 0:
        return public_url
    try:
        with httpx.Client(timeout=10.0, follow_redirects=True) as client:
            r = client.get(src_url)
            if r.status_code != 200 or not r.content:
                return None
            tmp = target_path + ".tmp"
            with open(tmp, "wb") as f:
                f.write(r.content)
            os.replace(tmp, target_path)
            return public_url
    except Exception as e:
        log.debug("logo mirror failed for id=%s: %s", sportsdb_id, e)
        return None


def _connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, timeout=20, isolation_level=None)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.row_factory = sqlite3.Row
    return conn


@contextmanager
def _conn():
    with _db_lock:
        c = _connect()
        try:
            yield c
        finally:
            c.close()


def _init_db() -> None:
    _ensure_db_dir()
    with _connect() as c:
        c.executescript(
            """
            CREATE TABLE IF NOT EXISTS sports_leagues (
                id            INTEGER PRIMARY KEY,
                slug          TEXT    NOT NULL UNIQUE,
                name          TEXT    NOT NULL,
                sportsdb_id   TEXT,
                accent        TEXT,
                display_order INTEGER DEFAULT 100,
                active        INTEGER DEFAULT 1,
                logo_url      TEXT
            );
            CREATE TABLE IF NOT EXISTS sports_teams (
                id            INTEGER PRIMARY KEY,
                league_id     INTEGER NOT NULL REFERENCES sports_leagues(id),
                sportsdb_id   TEXT    UNIQUE,
                name          TEXT    NOT NULL,
                short_name    TEXT,
                logo_url      TEXT,
                badge_url     TEXT,
                country       TEXT,
                home_venue    TEXT
            );
            CREATE INDEX IF NOT EXISTS sports_teams_league_idx
                ON sports_teams(league_id);
            CREATE TABLE IF NOT EXISTS sports_games (
                id            INTEGER PRIMARY KEY,
                sportsdb_id   TEXT    UNIQUE,
                league_id     INTEGER NOT NULL REFERENCES sports_leagues(id),
                home_team_id  INTEGER REFERENCES sports_teams(id),
                away_team_id  INTEGER REFERENCES sports_teams(id),
                start_utc     INTEGER NOT NULL,
                status        TEXT    DEFAULT 'scheduled',
                score_home    TEXT,
                score_away    TEXT,
                venue         TEXT,
                thumb_url     TEXT,
                video_url     TEXT,
                round         TEXT,
                raw_json      TEXT,
                updated_ts    INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS sports_games_league_start_idx
                ON sports_games(league_id, start_utc);
            CREATE INDEX IF NOT EXISTS sports_games_start_idx
                ON sports_games(start_utc);
            -- v1.44.26 — TheSportsDB-given broadcaster table.
            -- One row per (event, broadcaster, country) triple
            -- because TheSportsDB returns multi-row TV listings
            -- (game X is on TSN1 in CA, TNT in US, BeIN Max 6 in FR…).
            CREATE TABLE IF NOT EXISTS sports_broadcasts (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                sportsdb_id   TEXT NOT NULL,
                channel_name  TEXT NOT NULL,
                country       TEXT,
                logo_url      TEXT,
                updated_ts    INTEGER NOT NULL,
                UNIQUE(sportsdb_id, channel_name, country)
            );
            CREATE INDEX IF NOT EXISTS sports_broadcasts_event_idx
                ON sports_broadcasts(sportsdb_id);
            CREATE TABLE IF NOT EXISTS sports_ppv (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                source          TEXT NOT NULL,          -- 'sportsdb' | 'manual'
                source_id       TEXT,                   -- sportsdb event id
                title           TEXT NOT NULL,
                subtitle        TEXT,
                poster_url      TEXT,
                start_utc       INTEGER NOT NULL,
                status          TEXT DEFAULT 'scheduled',
                default_channel TEXT,
                admin_notes     TEXT,
                created_ts      INTEGER NOT NULL,
                updated_ts      INTEGER NOT NULL,
                UNIQUE (source, source_id)
            );
            CREATE INDEX IF NOT EXISTS sports_ppv_start_idx
                ON sports_ppv(start_utc);
            CREATE TABLE IF NOT EXISTS sports_channel_map (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                scope        TEXT NOT NULL,      -- 'league'|'team'|'event'|'ppv'
                scope_id     TEXT NOT NULL,      -- league.slug / team.id / game.id / ppv.id
                channel_name TEXT NOT NULL,      -- name as it appears in Xtream
                priority     INTEGER DEFAULT 100,-- lower = higher priority
                active       INTEGER DEFAULT 1,
                created_ts   INTEGER NOT NULL,
                UNIQUE (scope, scope_id, channel_name)
            );
            CREATE INDEX IF NOT EXISTS sports_channel_map_scope_idx
                ON sports_channel_map(scope, scope_id);
            """
        )


# ── TheSportsDB helpers ─────────────────────────────────────────────
def _sportsdb_get(path: str, params: Optional[Dict[str, Any]] = None,
                  v2: bool = False) -> Dict[str, Any]:
    """GET from TheSportsDB with basic error handling.

    path is relative (e.g. 'eventsnextleague.php'). v2 flips to the v2
    host (some endpoints like /livescore/ are v2-only).

    v1.44.8 — V2 endpoints require the API key in an X-API-KEY header
    instead of a URL path segment. V1 stays URL-key (legacy). The
    business-tier key works on both.
    """
    base = SPORTSDB_V2 if v2 else SPORTSDB_V1
    url = f"{base}/{path.lstrip('/')}"
    if params:
        url = f"{url}?{urllib.parse.urlencode(params)}"
    headers: Dict[str, str] = {}
    if v2:
        headers["X-API-KEY"] = SPORTSDB_KEY
    try:
        with httpx.Client(timeout=15.0, follow_redirects=True) as client:
            r = client.get(url, headers=headers)
            r.raise_for_status()
            return r.json() or {}
    except httpx.HTTPError as e:
        log.warning("sportsdb GET %s failed: %s", url, e)
        return {}


def _utc_ts(iso: Optional[str], time_str: Optional[str] = None) -> Optional[int]:
    """Parse TheSportsDB's date+time (both strings) → UTC epoch ms.

    v1.44.59 — when TheSportsDB ships a `dateEvent` with no `strTime`
    (common for games TheSportsDB hasn't yet finalized a tip-time for,
    e.g. playoff TBD slots), the historical fallback was 00:00:00 UTC.
    That timestamp is BEFORE the typical NA evening game time and was
    causing today-or-tomorrow games to get filtered out by the
    `/league/{slug}` endpoint's time-window guard (which only looked
    back 6 h). We now default missing times to 23:00 UTC so a
    "tonight's NHL game" with no published puck-drop is stored at
    23:00 UTC ≈ 7 p.m. EDT / 6 p.m. EST — close to the empirical
    norm — and stays inside any reasonable visibility window.
    """
    if not iso:
        return None
    try:
        # TheSportsDB is inconsistent: sometimes just a date, sometimes
        # date + separate time field. We accept both.
        time_blank = not (time_str and time_str.strip() and time_str.strip() != "00:00:00")
        if time_str and "T" not in iso:
            combined = f"{iso}T{time_str}"
        else:
            combined = iso
        if "T" not in combined:
            # No time component at all → default to 23:00 UTC (see
            # docstring above for rationale).
            combined = f"{combined}T23:00:00"
        elif time_blank and combined.endswith("T00:00:00"):
            # Time was literally "00:00:00" — treat as missing and
            # apply the same evening fallback.
            combined = combined.replace("T00:00:00", "T23:00:00")
        # Strip any trailing Z or +HH:MM — assume TheSportsDB is already UTC.
        combined = combined.rstrip("Z").split("+", 1)[0]
        dt = datetime.fromisoformat(combined)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return int(dt.timestamp() * 1000)
    except Exception:
        return None


# ── Ingestion ───────────────────────────────────────────────────────
def _ingest_leagues(c: sqlite3.Connection) -> None:
    """Ensure DEFAULT_LEAGUES are present. Idempotent."""
    for slug, name, sid, accent, order in DEFAULT_LEAGUES:
        c.execute(
            "INSERT OR IGNORE INTO sports_leagues "
            "(slug, name, sportsdb_id, accent, display_order, active) "
            "VALUES (?, ?, ?, ?, ?, 1)",
            (slug, name, sid, accent, order),
        )


def _ingest_league_logos(c: sqlite3.Connection) -> int:
    """Backfill league badge URLs by hitting TheSportsDB
    `lookupleague.php` once per active league. Idempotent — skips
    leagues that already have a logo_url stored.

    v1.44.11 — added so the new league-chip UI on TV can render real
    league logos instead of plain text pills. Cheap (≤12 GETs total),
    runs once per server boot from the ingestion path.
    """
    rows = c.execute(
        "SELECT id, slug, sportsdb_id FROM sports_leagues "
        "WHERE active=1 AND (logo_url IS NULL OR logo_url='')"
    ).fetchall()
    backfilled = 0
    for r in rows:
        if not r["sportsdb_id"]:
            continue
        data = _sportsdb_get("lookupleague.php", {"id": r["sportsdb_id"]})
        lg = (data.get("leagues") or [{}])[0]
        # Prefer the badge (square, crisper at small sizes); fall back
        # to logo (often wider).
        url = lg.get("strBadge") or lg.get("strLogo")
        if not url:
            continue
        c.execute(
            "UPDATE sports_leagues SET logo_url=? WHERE id=?",
            (url, r["id"]),
        )
        backfilled += 1
        log.info("backfilled league logo: slug=%s url=%s", r["slug"], url)
    return backfilled


def _ingest_seed_channel_map(c: sqlite3.Connection) -> None:
    """Seed league-default and team-specific channel mappings.

    Only runs on first boot — detected by the table being empty. User
    is expected to customize via the admin panel after.
    """
    count = c.execute("SELECT COUNT(*) AS n FROM sports_channel_map").fetchone()["n"]
    if count > 0:
        return
    now = int(time.time() * 1000)
    for slug, channel in LEAGUE_CHANNEL_SEED:
        c.execute(
            "INSERT OR IGNORE INTO sports_channel_map "
            "(scope, scope_id, channel_name, priority, active, created_ts) "
            "VALUES ('league', ?, ?, 100, 1, ?)",
            (slug, channel, now),
        )
    # Team mappings need the team to exist first. We insert into a
    # staging table by short_name and resolve after first schedule
    # ingestion. Store as scope='team_pending' for now.
    for slug, team_name, channel in TEAM_CHANNEL_SEED:
        c.execute(
            "INSERT OR IGNORE INTO sports_channel_map "
            "(scope, scope_id, channel_name, priority, active, created_ts) "
            "VALUES ('team_pending', ?, ?, 50, 1, ?)",
            (f"{slug}|{team_name}", channel, now),
        )


def _resolve_pending_team_maps(c: sqlite3.Connection) -> None:
    """Promote scope='team_pending' rows to scope='team' once the team
    exists in sports_teams. Name-match by TheSportsDB strTeam field.
    """
    pending = c.execute(
        "SELECT id, scope_id, channel_name, created_ts FROM sports_channel_map "
        "WHERE scope='team_pending' AND active=1"
    ).fetchall()
    for row in pending:
        if "|" not in row["scope_id"]:
            continue
        league_slug, team_name = row["scope_id"].split("|", 1)
        lg = c.execute(
            "SELECT id FROM sports_leagues WHERE slug=?",
            (league_slug,),
        ).fetchone()
        if not lg:
            continue
        team = c.execute(
            "SELECT id FROM sports_teams WHERE league_id=? AND LOWER(name)=LOWER(?)",
            (lg["id"], team_name),
        ).fetchone()
        if not team:
            continue
        c.execute(
            "INSERT OR IGNORE INTO sports_channel_map "
            "(scope, scope_id, channel_name, priority, active, created_ts) "
            "VALUES ('team', ?, ?, 50, 1, ?)",
            (str(team["id"]), row["channel_name"], row["created_ts"]),
        )
        c.execute("UPDATE sports_channel_map SET active=0 WHERE id=?", (row["id"],))


def _upsert_team(c: sqlite3.Connection, league_id: int, team_json: Dict[str, Any]) -> Optional[int]:
    sdb_id = team_json.get("idTeam") or team_json.get("idTeamHome") or team_json.get("idTeamAway")
    name = (
        team_json.get("strTeam")
        or team_json.get("strHomeTeam")
        or team_json.get("strAwayTeam")
    )
    if not sdb_id or not name:
        return None
    row = c.execute(
        "SELECT id FROM sports_teams WHERE sportsdb_id=?",
        (str(sdb_id),),
    ).fetchone()
    if row:
        return int(row["id"])
    # Mirror the badge to our own server so the Android client never
    # waits on TheSportsDB's CDN. We prefer strBadge (the square team
    # crest, transparent PNG) over strLogo (often a wordmark).
    src_logo = team_json.get("strBadge") or team_json.get("strLogo")
    mirrored = _mirror_logo(str(sdb_id), src_logo)
    c.execute(
        "INSERT OR IGNORE INTO sports_teams "
        "(league_id, sportsdb_id, name, short_name, logo_url, badge_url, country, home_venue) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (
            league_id,
            str(sdb_id),
            name,
            team_json.get("strTeamShort"),
            mirrored or src_logo,
            mirrored or team_json.get("strBadge") or team_json.get("strLogo"),
            team_json.get("strCountry"),
            team_json.get("strStadium") or team_json.get("strVenue"),
        ),
    )
    return int(c.execute("SELECT id FROM sports_teams WHERE sportsdb_id=?", (str(sdb_id),)).fetchone()["id"])


def _upsert_game(c: sqlite3.Connection, league_id: int, ev: Dict[str, Any]) -> None:
    sdb_id = ev.get("idEvent")
    if not sdb_id:
        return
    start_ms = _utc_ts(ev.get("dateEvent") or ev.get("dateEventLocal"),
                       ev.get("strTime") or ev.get("strTimeLocal"))
    if not start_ms:
        return
    # Shallow team stubs — upsert them.
    home_team_id = None
    away_team_id = None
    if ev.get("idHomeTeam"):
        home_team_id = _upsert_team(c, league_id, {
            "idTeam": ev["idHomeTeam"], "strTeam": ev.get("strHomeTeam"),
            "strBadge": ev.get("strHomeTeamBadge"),
        })
    if ev.get("idAwayTeam"):
        away_team_id = _upsert_team(c, league_id, {
            "idTeam": ev["idAwayTeam"], "strTeam": ev.get("strAwayTeam"),
            "strBadge": ev.get("strAwayTeamBadge"),
        })
    now_ms = int(time.time() * 1000)
    status_raw = (ev.get("strStatus") or ev.get("strProgress") or "scheduled").lower().strip()
    # Comprehensive status normalization for all sports.
    final_keywords = (
        "final", "ft", "match finished", "after extra time", "aet",
        "after pen", "pen.", "match over", "ended", "complete",
    )
    live_keywords = (
        "progress", "live", "q1", "q2", "q3", "q4",
        "1st", "2nd", "3rd", "4th",   # quarters / halves
        "half", "halftime", "ht ",    # soccer / basketball half
        "top ", "bot ", "mid ", "end ",  # baseball innings ("Top 1st")
        "over", "innings",            # cricket
        "set ",                       # tennis
        "lap ",                       # motorsport
        "period",                     # hockey
    )
    if any(k in status_raw for k in final_keywords):
        status = "final"
    elif any(k in status_raw for k in live_keywords):
        status = "live"
    elif status_raw in ("ns", "scheduled", "pre-game", "pregame", "tbd", ""):
        status = "scheduled"
    else:
        # Fallback: trust the time delta.
        delta_h = (start_ms - now_ms) / 3600_000.0
        status = "live" if -5 <= delta_h <= 5 else (
            "final" if delta_h < -5 else "scheduled"
        )
    c.execute(
        "INSERT INTO sports_games "
        "(sportsdb_id, league_id, home_team_id, away_team_id, start_utc, status, "
        " score_home, score_away, venue, thumb_url, video_url, round, raw_json, updated_ts) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
        "ON CONFLICT(sportsdb_id) DO UPDATE SET "
        "  start_utc=excluded.start_utc, status=excluded.status, "
        "  score_home=excluded.score_home, score_away=excluded.score_away, "
        "  venue=excluded.venue, thumb_url=excluded.thumb_url, "
        "  video_url=excluded.video_url, round=excluded.round, "
        "  raw_json=excluded.raw_json, updated_ts=excluded.updated_ts",
        (
            str(sdb_id),
            league_id,
            home_team_id,
            away_team_id,
            start_ms,
            status,
            ev.get("intHomeScore"),
            ev.get("intAwayScore"),
            ev.get("strVenue"),
            ev.get("strThumb"),
            ev.get("strVideo"),
            ev.get("strRound"),
            json.dumps(ev)[:8192],
            now_ms,
        ),
    )


def refresh_league_schedule(slug: str) -> int:
    """Pull the next N games for a league. Returns # games upserted."""
    with _conn() as c:
        lg = c.execute(
            "SELECT id, sportsdb_id FROM sports_leagues WHERE slug=?",
            (slug,),
        ).fetchone()
        if not lg or not lg["sportsdb_id"]:
            return 0
        data = _sportsdb_get("eventsnextleague.php", {"id": lg["sportsdb_id"]})
        events = data.get("events") or []
        for ev in events:
            _upsert_game(c, int(lg["id"]), ev)
        # Also pull past 2 days for status updates.
        past = _sportsdb_get("eventspastleague.php", {"id": lg["sportsdb_id"]})
        for ev in (past.get("events") or [])[:20]:
            _upsert_game(c, int(lg["id"]), ev)
        _resolve_pending_team_maps(c)
        return len(events)


def _apply_v2_livescore(c: sqlite3.Connection, ev: Dict[str, Any]) -> bool:
    """Apply a single live-event update from the V2 livescore endpoint.

    Returns True if a row was actually updated (idEvent matched an
    existing game). Unlike _upsert_game this is a NARROW update —
    only status, scores, and the timestamp move. The full schedule
    upsert happens via _upsert_game on the slower 15-min cycle.

    V2 livescore JSON shape (per business-tier docs):
      {
        "idEvent": "2387404",
        "strProgress": "Top 5th" | "Final" | "FT" | "" | "IN9" | "IN8",
        "strStatus":   "FT" | "NS" | "IN9" | "in progress" | ...,
        "intHomeScore": 4,
        "intAwayScore": 2,
        ...
      }

    NOTE: The V2 livescore endpoint returns the FULL DAY's slate per
    league — not just live games. That includes "NS" (not started)
    games, which we MUST NOT promote to status='live' even though
    they came from the livescore feed. Only progress/status that
    indicates active play counts as live.
    """
    sdb_id = ev.get("idEvent")
    if not sdb_id:
        return False
    progress = (ev.get("strProgress") or "").strip()
    raw_status = (ev.get("strStatus") or "").strip()
    # Use whichever has more info.
    status_raw = (progress or raw_status or "").lower().strip()

    final_keywords = (
        "final", "ft", "match finished", "after extra time", "aet",
        "after pen", "pen.", "match over", "ended", "complete",
        "fulltime", "full time", "ap",      # "after penalties"
    )
    not_started_values = (
        "", "ns", "scheduled", "pre-game", "pregame",
        "tbd", "not started", "postp", "postponed", "cancelled",
        "canceled", "delayed",
    )
    live_keywords = (
        "progress", "live", "halftime",
        "q1", "q2", "q3", "q4",
        "1st", "2nd", "3rd", "4th",
        "half", "ht ",
        "top ", "bot ", "mid ", "end ",
        "over ", "innings", "inning",
        "in1", "in2", "in3", "in4", "in5", "in6", "in7", "in8", "in9",
        "in10", "in11", "in12", "in13", "in14", "in15",
        "set ", "lap ", "period",
        ":",         # "45:30 - 1st Half"
        "+",         # "95+4" extra-time
    )

    if any(k in status_raw for k in final_keywords):
        status = "final"
    elif status_raw in not_started_values:
        # NS / scheduled — don't touch the game's existing status.
        # The schedule pull already set it to 'scheduled' and the
        # client's ±5h time-delta fallback handles the corner case
        # of "started but upstream still says NS".
        return False
    elif any(k in status_raw for k in live_keywords):
        status = "live"
    else:
        # Unknown status string — only call it live if there are
        # actual scores, otherwise leave the row alone.
        if ev.get("intHomeScore") is not None or ev.get("intAwayScore") is not None:
            status = "live"
        else:
            return False

    score_home = ev.get("intHomeScore")
    score_away = ev.get("intAwayScore")
    score_home_str = str(score_home) if score_home is not None else None
    score_away_str = str(score_away) if score_away is not None else None
    now_ms = int(time.time() * 1000)

    # v1.44.60 — Sanity-check upstream livescore data against the
    # game's known start time. TheSportsDB's livescore endpoint can
    # (rarely but observably) ship a game as `Final` with a score
    # BEFORE the puck/ball actually drops — for example when the
    # CDN serves cached "previous game in the series" data under
    # the new event's idEvent. If we trust that, we mark a future
    # game as completed and hide it from the upcoming-games rail.
    #
    # Guard: if the upstream wants to promote a game to `final`
    # but the game's stored start time is more than 1 h in the
    # future, REJECT the update. The game will keep its existing
    # status (scheduled). 1 h gives a buffer for clock skew and
    # for the rare game that runs short / forfeits early.
    if status == "final":
        existing = c.execute(
            "SELECT start_utc FROM sports_games WHERE sportsdb_id=?",
            (str(sdb_id),),
        ).fetchone()
        if existing and existing["start_utc"] is not None:
            if int(existing["start_utc"]) > now_ms + 60 * 60 * 1000:
                log.warning(
                    "rejecting upstream 'final' livescore for future game: "
                    "sdb_id=%s start_utc=%s score=%s-%s now=%s",
                    sdb_id, existing["start_utc"],
                    score_home_str, score_away_str, now_ms,
                )
                return False

    res = c.execute(
        "UPDATE sports_games SET "
        "  status=?, "
        "  score_home=COALESCE(?, score_home), "
        "  score_away=COALESCE(?, score_away), "
        "  updated_ts=? "
        "WHERE sportsdb_id=?",
        (status, score_home_str, score_away_str, now_ms, str(sdb_id)),
    )
    return res.rowcount > 0


def refresh_live_scores() -> int:
    """Pull live scores via the V2 per-league livescore endpoint.

    v1.44.8 — Replaces the previous fan-out of per-game lookupevent.php
    calls with ONE call per active league via
        GET /api/v2/json/livescore/{idLeague}
    The V2 endpoint:
      • Returns ALL currently-live games in the league in a single
        call (not N games × 1 call).
      • Updates every 2 min on the business tier (vs ~10 min lag on
        v1 lookupevent.php).
      • Auto-removes events ~1 hour after FT, so we naturally clean up
        once a game ends.
    Net effect: score-update latency drops from minutes to seconds,
    AND we're no longer at risk of hitting the rate limit when many
    games are live.

    Falls back to per-game lookupevent.php for any local game whose
    sportsdb_id we couldn't find in the V2 response (covers stale
    rows + leagues V2 doesn't currently support).
    """
    updated = 0
    matched_ids: set = set()
    with _conn() as c:
        leagues = c.execute(
            "SELECT id, sportsdb_id FROM sports_leagues "
            "WHERE active=1 AND sportsdb_id IS NOT NULL"
        ).fetchall()
    for lg in leagues:
        idl = lg["sportsdb_id"]
        if not idl:
            continue
        data = _sportsdb_get(f"livescore/{idl}", v2=True)
        events = data.get("livescore") or data.get("events") or []
        if not events:
            continue
        with _conn() as c:
            for ev in events:
                if _apply_v2_livescore(c, ev):
                    updated += 1
                    matched_ids.add(str(ev.get("idEvent")))
        log.info("v2 livescore league=%s applied=%d", idl, len(events))

    # Fallback: any local "scheduled" game that started within the past
    # 6h but didn't show up in any livescore response — poll it the
    # old way so we don't lose visibility of niche games.
    now_ms = int(time.time() * 1000)
    with _conn() as c:
        stragglers = c.execute(
            "SELECT sportsdb_id, league_id FROM sports_games "
            "WHERE status='scheduled' "
            "  AND start_utc <= ? AND start_utc >= ?",
            (now_ms, now_ms - 6 * 3600 * 1000),
        ).fetchall()
    stragglers = [r for r in stragglers if str(r["sportsdb_id"]) not in matched_ids]
    if stragglers:
        log.info("v1 fallback for %d straggler games", len(stragglers))
    for row in stragglers:
        sid = row["sportsdb_id"]
        data = _sportsdb_get("lookupevent.php", {"id": sid})
        events = data.get("events") or []
        if not events:
            continue
        with _conn() as c:
            _upsert_game(c, int(row["league_id"]), events[0])
            updated += 1

    # v1.44.8 — mark stale-live games as final. The V2 livescore feed
    # auto-removes games ~1h after FT, so any local game still
    # marked status='live' with start_utc more than 8h ago has clearly
    # ended; we just lost the FT signal because it dropped out of the
    # feed before our next poll. Sweep them up now.
    stale_threshold = now_ms - 8 * 3600 * 1000
    with _conn() as c:
        res = c.execute(
            "UPDATE sports_games SET status='final', updated_ts=? "
            "WHERE status='live' AND start_utc < ?",
            (now_ms, stale_threshold),
        )
        if res.rowcount > 0:
            log.info("marked %d stale-live games as final", res.rowcount)
            updated += res.rowcount

    return updated


def refresh_ppv() -> int:
    """Pull UFC / boxing / wrestling events for the PPV tab."""
    inserted = 0
    # UFC: league id 4443. Boxing: 4021. WWE: 4366.
    ppv_league_ids = ["4443", "4021", "4366"]
    with _conn() as c:
        for lg_id in ppv_league_ids:
            data = _sportsdb_get("eventsnextleague.php", {"id": lg_id})
            for ev in (data.get("events") or [])[:15]:
                start_ms = _utc_ts(
                    ev.get("dateEvent") or ev.get("dateEventLocal"),
                    ev.get("strTime") or ev.get("strTimeLocal"),
                )
                if not start_ms:
                    continue
                title = (
                    ev.get("strEvent")
                    or f"{ev.get('strHomeTeam', '')} vs {ev.get('strAwayTeam', '')}".strip()
                )
                if not title:
                    continue
                c.execute(
                    "INSERT INTO sports_ppv "
                    "(source, source_id, title, subtitle, poster_url, start_utc, "
                    " status, default_channel, admin_notes, created_ts, updated_ts) "
                    "VALUES ('sportsdb', ?, ?, ?, ?, ?, 'scheduled', NULL, NULL, ?, ?) "
                    "ON CONFLICT(source, source_id) DO UPDATE SET "
                    "  title=excluded.title, subtitle=excluded.subtitle, "
                    "  poster_url=excluded.poster_url, start_utc=excluded.start_utc, "
                    "  updated_ts=excluded.updated_ts",
                    (
                        ev.get("idEvent"),
                        title,
                        (ev.get("strDescriptionEN") or "")[:240] or None,
                        ev.get("strThumb") or ev.get("strPoster"),
                        start_ms,
                        int(time.time() * 1000),
                        int(time.time() * 1000),
                    ),
                )
                inserted += 1
    return inserted


def prune_old() -> int:
    cutoff = int(time.time() * 1000) - GAMES_PAST_DAYS * 86400 * 1000
    with _conn() as c:
        r1 = c.execute("DELETE FROM sports_games WHERE start_utc < ?", (cutoff,))
        r2 = c.execute("DELETE FROM sports_ppv WHERE start_utc < ?", (cutoff,))
        return (r1.rowcount or 0) + (r2.rowcount or 0)


# ── Broadcasts ingestion (v1.44.26) ────────────────────────────────
# TheSportsDB exposes a separate `eventstv.php?d=YYYY-MM-DD&s=<sport>`
# endpoint that returns one row per (event × broadcaster × country).
# The richer per-event `lookuptv.php?id=<event>` works too but is
# 1 HTTP call per game (would burn 200+ req per refresh). The bulk
# date+sport endpoint covers 7 days of slate in 7 × 6 sports = 42
# requests, well within our Business-tier 120 req/min budget.

# TheSportsDB sport names used by /eventstv.php?s=...
_SPORT_BY_LEAGUE_SLUG: Dict[str, str] = {
    "nhl":   "Ice_Hockey",
    "mlb":   "Baseball",
    "nba":   "Basketball",
    "ncaab": "Basketball",
    "nfl":   "American_Football",
    "ncaaf": "American_Football",
    "cfl":   "American_Football",
    "ufc":   "Fighting",
    "mls":   "Soccer",
    "epl":   "Soccer",
    "ucl":   "Soccer",
    "f1":    "Motorsport",
}


def refresh_broadcasts_for_window(days: int = 7) -> int:
    """Pull broadcaster lists for every active league for the next
    [days] days. Returns the # of (event × channel) pairs upserted.

    Strategy: union the unique sport names across all active leagues
    (nhl/ahl/khl all map to 'Ice_Hockey'), then for each (date, sport)
    pair hit `/eventstv.php?d=YYYY-MM-DD&s=<sport>`. Filter results
    to events whose `idEvent` is in our `sports_games` table. Dedupe
    by (sportsdb_id, channel_name, country) so we can keep CA + US
    + UK rows for the same game.

    v1.44.27 — Date window starts at YESTERDAY-UTC, not today, so
    games that started 23:00 UTC last night (still live in North
    America evenings) are still in scope. Without this, a 7 PM EST
    NHL game would lose its broadcaster info as soon as the UTC
    date rolled over (i.e. 7 PM EST = 00:00 UTC), even though it's
    still live for another 2.5 hours.
    """
    today = datetime.now(timezone.utc).date()
    # Include yesterday + today + N future days. Yesterday catches
    # in-progress NA evening games whose dateEvent rolled over.
    dates = [today + timedelta(days=i) for i in range(-1, days)]

    with _conn() as c:
        active_slugs = [r["slug"] for r in c.execute(
            "SELECT slug FROM sports_leagues WHERE active=1").fetchall()]
    sports = sorted({
        _SPORT_BY_LEAGUE_SLUG[s] for s in active_slugs
        if s in _SPORT_BY_LEAGUE_SLUG
    })
    if not sports:
        return 0

    # Cache the set of known event ids so we don't waste rows on
    # broadcasts for events we don't track (e.g. international
    # divisions returned by 'Soccer' that we don't ingest).
    with _conn() as c:
        known_ids = {
            row["sportsdb_id"]
            for row in c.execute(
                "SELECT sportsdb_id FROM sports_games "
                "WHERE start_utc >= ?",
                (int((today - timedelta(days=2)).strftime("%s")) * 1000,),
            ).fetchall()
        }
    if not known_ids:
        return 0

    inserted = 0
    now_ms = int(time.time() * 1000)

    with _conn() as c:
        for d in dates:
            for sport in sports:
                params = {"d": d.isoformat(), "s": sport}
                data = _sportsdb_get("eventstv.php", params)
                rows = data.get("tvevents") or []
                for row in rows:
                    sdb_id = (row.get("idEvent") or "").strip()
                    if not sdb_id or sdb_id not in known_ids:
                        continue
                    ch = (row.get("strChannel") or "").strip()
                    if not ch:
                        continue
                    country = (row.get("strCountry")
                               or row.get("strEventCountry") or "").strip() or None
                    logo = (row.get("strLogo") or "").strip() or None
                    c.execute(
                        "INSERT INTO sports_broadcasts "
                        "(sportsdb_id, channel_name, country, logo_url, updated_ts) "
                        "VALUES (?, ?, ?, ?, ?) "
                        "ON CONFLICT(sportsdb_id, channel_name, country) "
                        "DO UPDATE SET logo_url=excluded.logo_url, "
                        "              updated_ts=excluded.updated_ts",
                        (sdb_id, ch, country, logo, now_ms),
                    )
                    inserted += 1
                # Spread requests at ~2/sec — Business tier is 60/min,
                # but other refreshers (schedules, scores) share the
                # budget. 500 ms gives them headroom without making
                # the broadcasts pull glacial.
                time.sleep(0.5)
    return inserted


# Cache of (game_id) → ordered Canadian-first channel list, scoped
# to a single resolver request. Keyed on the connection object so
# we don't bleed across requests.
def _broadcasts_for_game(c: sqlite3.Connection, game_id: int) -> List[str]:
    """Fetch all known broadcasters for a game from sports_broadcasts,
    Canadian-first sorted. Channels are de-duplicated case-insensitive
    (preserving original casing of the first occurrence)."""
    rows = c.execute(
        "SELECT b.channel_name, b.country FROM sports_broadcasts b "
        "JOIN sports_games g ON g.sportsdb_id = b.sportsdb_id "
        "WHERE g.id = ?",
        (game_id,),
    ).fetchall()
    if not rows:
        return []
    # Canada rows first by `country` field, then by name preference.
    ca = [r["channel_name"] for r in rows if (r["country"] or "").lower() in (
        "canada", "ca",
    )]
    rest = [r["channel_name"] for r in rows if (r["country"] or "").lower() not in (
        "canada", "ca",
    )]
    # Within each bucket, run the broadcaster-name pattern sort so
    # e.g. a Sportsnet US affiliate (rare) gets demoted under TSN1.
    ca_sorted = _canadian_first_sorted(ca)
    rest_sorted = _canadian_first_sorted(rest)
    seen = set()
    out: List[str] = []
    for n in ca_sorted + rest_sorted:
        k = n.lower()
        if k in seen:
            continue
        seen.add(k)
        out.append(n)
    return out


# ── Channel resolution ─────────────────────────────────────────────

# v1.44.26 — TheSportsDB `strChannel` auto-mapping.
#
# TheSportsDB returns a comma/slash/pipe/ampersand separated list of
# broadcasters per event (e.g. "TSN1, ESPN+, NHL Network" for a NHL
# game with Canadian + US + streaming feeds). We auto-extract that
# list, sort it Canadian-first per user direction, and use it as a
# resolver tier between admin event-overrides and admin team-maps.
#
# This means a game gets the SAME broadcaster TheSportsDB knows it's
# on without an admin having to manually map every team / fixture.
# The per-team admin map still applies when TheSportsDB doesn't
# return a broadcaster (rare but happens for friendlies, lower
# divisions, etc.). Per-game admin overrides still win above
# everything (the manual-fix escape hatch).

_CANADIAN_BROADCASTER_PATTERNS = [
    # Highest priority Canadian broadcasters, in rough preference
    # order (regional > national premium > national basic).
    "sportsnet",   # Sportsnet One, East, West, Pacific, 360, etc.
    "tsn",         # TSN 1-5 + TSN+
    "rds",         # French Canadian
    "tva sports",  # French Canadian premium
    "citytv",
    "city tv",
    "ctv",
    "cbc",
    "global",
    "bnn",
    "fubo",        # FuboTV Canada
]

# Tokens that indicate a US-only broadcaster — explicitly DEPRIORITIZED
# so a Canadian match wins even when Canadian and US co-broadcast.
_US_BROADCASTER_PATTERNS = [
    "espn", "fox", "nbc", "cbs", "abc", "tnt", "tbs", "usa network",
    "nfl network", "nba tv", "nhl network", "mlb network",
    "peacock", "paramount", "amazon", "apple", "max ",
]


def _split_tsdb_channels(raw: Optional[str]) -> List[str]:
    """Split TheSportsDB's strChannel string into a clean ordered
    list of broadcaster names. Handles comma / slash / pipe /
    ampersand / semicolon separators and trims whitespace.
    """
    if not raw:
        return []
    # Replace common separator chars with commas, then split.
    cleaned = re.sub(r"[/|&;]", ",", raw)
    parts = [p.strip() for p in cleaned.split(",")]
    # Filter empties + dedupe preserving order.
    seen: set[str] = set()
    out: List[str] = []
    for p in parts:
        if not p:
            continue
        key = p.lower()
        if key in seen:
            continue
        seen.add(key)
        out.append(p)
    return out


def _is_canadian_broadcaster(name: str) -> bool:
    n = name.lower()
    return any(p in n for p in _CANADIAN_BROADCASTER_PATTERNS)


def _is_us_only_broadcaster(name: str) -> bool:
    n = name.lower()
    return (
        any(p in n for p in _US_BROADCASTER_PATTERNS)
        and not any(p in n for p in _CANADIAN_BROADCASTER_PATTERNS)
    )


def _canadian_first_sorted(names: List[str]) -> List[str]:
    """Return [names] reordered as: Canadian → neutral/intl → US-only.
    Preserves original order within each bucket so TheSportsDB's
    natural priority within Canadian channels is kept."""
    canadian: List[str] = []
    neutral: List[str] = []
    us_only: List[str] = []
    for n in names:
        if _is_canadian_broadcaster(n):
            canadian.append(n)
        elif _is_us_only_broadcaster(n):
            us_only.append(n)
        else:
            neutral.append(n)
    return canadian + neutral + us_only


def _resolve_channel(
    c: sqlite3.Connection,
    league_slug: str,
    home_team_id: Optional[int],
    away_team_id: Optional[int],
    game_id: Optional[int] = None,
) -> Optional[str]:
    """Return the best-match channel name for a game, or None if none.

    Lookup order (v1.44.26):
        1. Per-game admin override (manual escape hatch).
        2. TheSportsDB `strChannel`, Canadian-first sorted (NEW).
        3. Team map (admin).
        4. League fallback (admin).

    Returning the FIRST candidate from each tier — the client-side
    [SportsChannelMatcher] then maps that string ("TSN 1") to the
    user's actual Xtream channel ("CA: TSN 1 HD" / "TSN1 FHD" /
    etc) using token + digit-word fuzzy matching. If the matcher
    can't find an Xtream channel, the game is hidden client-side
    (a missing channel doesn't help anyone).
    """
    # 1. Per-game admin override (highest — manual fix wins everything)
    if game_id is not None:
        r = c.execute(
            "SELECT channel_name FROM sports_channel_map "
            "WHERE scope='event' AND scope_id=? AND active=1 "
            "ORDER BY priority LIMIT 1",
            (str(game_id),),
        ).fetchone()
        if r:
            return r["channel_name"]

    # 2. TheSportsDB-given broadcaster (v1.44.26 — read from the
    #    sports_broadcasts table populated by refresh_broadcasts_for_window).
    #    Returns the FIRST Canadian-priority candidate. The client-side
    #    SportsChannelMatcher then maps that string to the user's
    #    Xtream channel ("TSN 1" → "CA: TSN 1 HD") via fuzzy match.
    if game_id is not None:
        cands = _broadcasts_for_game(c, game_id)
        if cands:
            return cands[0]

    # 3. Team map — try home first (canonical "home broadcaster")
    for team_id in (home_team_id, away_team_id):
        if team_id is None:
            continue
        r = c.execute(
            "SELECT channel_name FROM sports_channel_map "
            "WHERE scope='team' AND scope_id=? AND active=1 "
            "ORDER BY priority LIMIT 1",
            (str(team_id),),
        ).fetchone()
        if r:
            return r["channel_name"]

    # 4. League fallback
    r = c.execute(
        "SELECT channel_name FROM sports_channel_map "
        "WHERE scope='league' AND scope_id=? AND active=1 "
        "ORDER BY priority LIMIT 1",
        (league_slug,),
    ).fetchone()
    return r["channel_name"] if r else None


def _resolve_ppv_channel(c: sqlite3.Connection, ppv_id: int,
                          default_channel: Optional[str]) -> Optional[str]:
    r = c.execute(
        "SELECT channel_name FROM sports_channel_map "
        "WHERE scope='ppv' AND scope_id=? AND active=1 "
        "ORDER BY priority LIMIT 1",
        (str(ppv_id),),
    ).fetchone()
    if r:
        return r["channel_name"]
    if default_channel:
        return default_channel
    # Fall back to the UFC league map as a catch-all for PPV.
    r = c.execute(
        "SELECT channel_name FROM sports_channel_map "
        "WHERE scope='league' AND scope_id='ufc' AND active=1 LIMIT 1"
    ).fetchone()
    return r["channel_name"] if r else None


# ── Background worker ──────────────────────────────────────────────
_worker_started = False
_worker_lock = threading.Lock()


def _worker_loop() -> None:
    last_schedule = 0.0
    last_live = 0.0
    last_ppv = 0.0
    last_broadcasts = 0.0
    while True:
        now = time.time()
        try:
            if now - last_schedule > REFRESH_SCHEDULE_SEC:
                with _conn() as c:
                    leagues = [r["slug"] for r in c.execute(
                        "SELECT slug FROM sports_leagues WHERE active=1 "
                        "ORDER BY display_order, slug").fetchall()]
                for slug in leagues:
                    try:
                        refresh_league_schedule(slug)
                    except Exception:
                        log.exception("league schedule refresh failed: %s", slug)
                prune_old()
                last_schedule = now
            if now - last_live > REFRESH_LIVE_SEC:
                try:
                    refresh_live_scores()
                except Exception:
                    log.exception("live score refresh failed")
                last_live = now
            if now - last_ppv > REFRESH_PPV_SEC:
                try:
                    refresh_ppv()
                except Exception:
                    log.exception("ppv refresh failed")
                last_ppv = now
            # v1.44.26 — Refresh TheSportsDB broadcaster lists every
            # 30 minutes. The endpoint is rate-friendly (≈42 req per
            # full window throttled at 2 req/s = ~21 s wall time).
            # 30 min cadence keeps the broadcaster lineup fresh as
            # TheSportsDB publishes / updates feeds throughout the
            # day on game days.
            if now - last_broadcasts > 30 * 60:
                try:
                    n = refresh_broadcasts_for_window(days=7)
                    log.info("sports broadcasts refresh upserted %d rows", n)
                except Exception:
                    log.exception("broadcasts refresh failed")
                last_broadcasts = now
        except Exception:
            log.exception("worker loop iteration failed")
        time.sleep(30)


def start_worker() -> None:
    global _worker_started
    with _worker_lock:
        if _worker_started:
            return
        _worker_started = True
    t = threading.Thread(target=_worker_loop, name="sports-worker", daemon=True)
    t.start()


# ── Serialization helpers ──────────────────────────────────────────
def _game_to_dict(c: sqlite3.Connection, row: sqlite3.Row,
                   resolve_channel: bool = True) -> Dict[str, Any]:
    home = None
    away = None
    if row["home_team_id"]:
        ht = c.execute(
            "SELECT name, short_name, logo_url, badge_url FROM sports_teams WHERE id=?",
            (row["home_team_id"],),
        ).fetchone()
        if ht:
            home = dict(ht)
            # v1.44.10 — auto-fill short_name when TheSportsDB doesn't
            # supply one. Mutating the in-memory dict only; doesn't
            # touch the DB. Keeps the wire shape stable.
            if not (home.get("short_name") or "").strip():
                home["short_name"] = _derive_short_name(home.get("name"))
    if row["away_team_id"]:
        at = c.execute(
            "SELECT name, short_name, logo_url, badge_url FROM sports_teams WHERE id=?",
            (row["away_team_id"],),
        ).fetchone()
        if at:
            away = dict(at)
            if not (away.get("short_name") or "").strip():
                away["short_name"] = _derive_short_name(away.get("name"))
    league = c.execute(
        "SELECT slug, name, accent, logo_url FROM sports_leagues WHERE id=?",
        (row["league_id"],),
    ).fetchone()
    resolved_channel = None
    if resolve_channel and league:
        resolved_channel = _resolve_channel(
            c, league["slug"], row["home_team_id"], row["away_team_id"], row["id"],
        )
    return {
        "id": row["id"],
        "league": dict(league) if league else None,
        "home": home,
        "away": away,
        "start_utc": row["start_utc"],
        "status": row["status"],
        "score_home": row["score_home"],
        "score_away": row["score_away"],
        "venue": row["venue"],
        "round": row["round"],
        "channel": resolved_channel,
    }


def _ppv_to_dict(c: sqlite3.Connection, row: sqlite3.Row) -> Dict[str, Any]:
    return {
        "id": row["id"],
        "source": row["source"],
        "title": row["title"],
        "subtitle": row["subtitle"],
        "poster_url": row["poster_url"],
        "start_utc": row["start_utc"],
        "status": row["status"],
        "channel": _resolve_ppv_channel(c, int(row["id"]), row["default_channel"]),
    }


# ── Router ─────────────────────────────────────────────────────────
router = APIRouter(prefix="/api/sports", tags=["sports"])
admin_router = APIRouter(prefix="/api/admin/sports", tags=["sports-admin"])


@router.get("/health")
def sports_health() -> Dict[str, Any]:
    with _conn() as c:
        games = c.execute("SELECT COUNT(*) AS n FROM sports_games").fetchone()["n"]
        ppv = c.execute("SELECT COUNT(*) AS n FROM sports_ppv").fetchone()["n"]
        leagues = c.execute(
            "SELECT COUNT(*) AS n FROM sports_leagues WHERE active=1"
        ).fetchone()["n"]
        chan = c.execute(
            "SELECT COUNT(*) AS n FROM sports_channel_map WHERE active=1"
        ).fetchone()["n"]
    return {
        "status": "ok",
        "games_cached": games,
        "ppv_cached": ppv,
        "active_leagues": leagues,
        "active_channel_mappings": chan,
    }


@router.get("/leagues")
def sports_leagues() -> Dict[str, Any]:
    with _conn() as c:
        rows = c.execute(
            "SELECT slug, name, accent, display_order FROM sports_leagues "
            "WHERE active=1 ORDER BY display_order, slug"
        ).fetchall()
    return {"leagues": [dict(r) for r in rows]}


@router.get("/home")
def sports_home(
    playlist_id: Optional[str] = None,
    limit: int = Query(8, ge=1, le=24),
) -> Dict[str, Any]:
    """Home-row payload: a handful of upcoming games per active league,
    plus upcoming PPV events. Games without a resolvable channel are
    hidden (per user spec: 'hide entirely')."""
    now = int(time.time() * 1000)
    horizon = now + GAMES_FUTURE_DAYS * 86400 * 1000
    buckets: List[Dict[str, Any]] = []
    with _conn() as c:
        leagues = c.execute(
            "SELECT id, slug, name, accent, logo_url FROM sports_leagues "
            "WHERE active=1 ORDER BY display_order, slug"
        ).fetchall()
        for lg in leagues:
            rows = c.execute(
                "SELECT * FROM sports_games "
                # v1.44.5 — exclude games more than 5h past start that
                # haven't been confirmed live by the server. They're
                # almost certainly over and just haven't been marked
                # final by TheSportsDB yet. Server still keeps live
                # games (status='live') visible no matter how long.
                "WHERE league_id=? AND ("
                "       status='live' "
                "    OR (status='scheduled' AND start_utc > ? AND start_utc < ?) "
                "    OR (status='final' AND start_utc > ?) "
                "    ) "
                # Sort: live first, then upcoming by start time, then
                # final last.
                "ORDER BY CASE status WHEN 'live' THEN 0 "
                "                     WHEN 'scheduled' THEN 1 "
                "                     ELSE 2 END, "
                "         start_utc "
                "LIMIT ?",
                (
                    lg["id"],
                    now - 5 * 3600 * 1000,   # scheduled: keep up to 5h past start
                    horizon,
                    now - 6 * 3600 * 1000,   # final: only show very recently completed
                    limit,
                ),
            ).fetchall()
            games: List[Dict[str, Any]] = []
            for r in rows:
                g = _game_to_dict(c, r)
                # v1.44.60 — DO NOT drop games with no resolved
                # broadcaster (matches the same behaviour applied to
                # /league/{slug} in v1.44.59). The Android client
                # paints them with a "Channel TBD" synthetic card;
                # tapping invokes the per-game EPG sheet which does
                # its own broadcaster lookup.
                games.append(g)
            if games:
                buckets.append({
                    "league": {
                        "slug": lg["slug"],
                        "name": lg["name"],
                        "accent": lg["accent"],
                        "logo_url": lg["logo_url"],
                    },
                    "games": games,
                })
        # PPV bucket
        ppv_rows = c.execute(
            "SELECT * FROM sports_ppv WHERE start_utc > ? ORDER BY start_utc LIMIT ?",
            (now - 2 * 3600 * 1000, limit),
        ).fetchall()
        ppv_list: List[Dict[str, Any]] = []
        for r in ppv_rows:
            p = _ppv_to_dict(c, r)
            if p["channel"] is None:
                continue
            ppv_list.append(p)
    # Hero: pick the 5 soonest across all buckets (PPV + games combined).
    hero: List[Dict[str, Any]] = []
    for p in ppv_list[:3]:
        hero.append({
            "kind": "ppv",
            "id": p["id"],
            "title": p["title"],
            "subtitle": p["subtitle"],
            "image": p["poster_url"],
            "start_utc": p["start_utc"],
            "channel": p["channel"],
        })
    for b in buckets:
        for g in b["games"][:2]:
            # v1.44.5 — Skip final games for the hero. They don't
            # belong in the marquee — they belong in the cards rail
            # below where the user can quickly check the result.
            if (g.get("status") or "").lower() == "final":
                continue
            # v1.44.10 — Use short_name ("White Sox" / "Angels") for the
            # hero title. Falls back to a derived short name (drops the
            # city prefix) when TheSportsDB doesn't provide one — which
            # is most of the time for MLB.
            away = g.get("away") or {}
            home = g.get("home") or {}
            away_name = (away.get("short_name") or "").strip() \
                or _derive_short_name(away.get("name"))
            home_name = (home.get("short_name") or "").strip() \
                or _derive_short_name(home.get("name"))
            hero.append({
                "kind": "game",
                "id": g["id"],
                "title": f"{away_name} @ {home_name}".strip(" @"),
                "subtitle": g["league"]["name"] if g["league"] else "",
                "image": (g["home"] or {}).get("badge_url")
                         or (g["home"] or {}).get("logo_url"),
                "start_utc": g["start_utc"],
                "channel": g["channel"],
                "status": g.get("status") or "scheduled",
                "score_home": g.get("score_home"),
                "score_away": g.get("score_away"),
            })
    # Sort: live first, then upcoming by start time.
    hero.sort(key=lambda h: (
        0 if (h.get("status") or "").lower() == "live" else 1,
        h.get("start_utc") or 0,
    ))
    return {
        "hero": hero[:8],
        "ppv": ppv_list,
        "leagues": buckets,
    }


@router.get("/league/{slug}")
def sports_league(slug: str, days: int = Query(7, ge=1, le=30)) -> Dict[str, Any]:
    now = int(time.time() * 1000)
    horizon = now + days * 86400 * 1000
    with _conn() as c:
        lg = c.execute(
            "SELECT id, slug, name, accent FROM sports_leagues WHERE slug=?",
            (slug,),
        ).fetchone()
        if not lg:
            raise HTTPException(404, f"league {slug} not found")
        # v1.44.59 — widen the past-lookback from 6 h to 30 h. Games
        # that TheSportsDB ships with no `strTime` are stored at
        # 23:00 UTC of dateEvent (see `_utc_ts`), but games TheSportsDB
        # already moved to `dateEvent = TODAY` with a missing strTime
        # were previously slipping outside the 6 h backstop. 30 h
        # covers any same-day game in any NA time zone without
        # bleeding into yesterday's actual finals.
        rows = c.execute(
            "SELECT * FROM sports_games "
            "WHERE league_id=? AND start_utc BETWEEN ? AND ? "
            "ORDER BY start_utc",
            (lg["id"], now - 30 * 3600 * 1000, horizon),
        ).fetchall()
        games: List[Dict[str, Any]] = []
        for r in rows:
            g = _game_to_dict(c, r)
            # v1.44.59 — DO NOT filter out games whose primary
            # broadcaster hasn't been resolved yet. Previously this
            # endpoint silently dropped any game with channel=None;
            # users saw the league page as nearly empty whenever the
            # broadcast feed lagged behind the schedule feed (common
            # for playoff TBDs / cross-conference games). The Android
            # client now handles channel=None gracefully (synthetic
            # MediaCard at click time, GameChannelSheet does its own
            # per-game EPG search) so the empty-channel case is the
            # client's call.
            games.append(g)
    return {"league": dict(lg), "games": games, "count": len(games)}


@router.get("/ppv")
def sports_ppv_list() -> Dict[str, Any]:
    now = int(time.time() * 1000)
    with _conn() as c:
        rows = c.execute(
            "SELECT * FROM sports_ppv WHERE start_utc > ? ORDER BY start_utc",
            (now - 2 * 3600 * 1000,),
        ).fetchall()
        out: List[Dict[str, Any]] = []
        for r in rows:
            p = _ppv_to_dict(c, r)
            if p["channel"] is None:
                continue
            out.append(p)
    return {"events": out}


@router.get("/game/{game_id}")
def sports_game_detail(game_id: int) -> Dict[str, Any]:
    with _conn() as c:
        row = c.execute(
            "SELECT * FROM sports_games WHERE id=?", (game_id,),
        ).fetchone()
        if not row:
            raise HTTPException(404, "game not found")
        g = _game_to_dict(c, row)
        if g["channel"] is None:
            raise HTTPException(404, "game not available on your service")
    return g


# ── EPG-based channel discovery (v1.44.27) ──────────────────────────
# The user's Xtream EPG is the source of truth for "which of MY
# channels is currently airing this game". We call xmltv.php once
# per playlist (cached 4h on disk + in-memory) and search programmes
# whose title/sub/desc contain BOTH team names within a ±30 min
# window of the game's start.
#
# Endpoint: GET /api/sports/game/{id}/channels
#   Query: host, username, password — the user's Xtream creds.
#          (Same trust model as everything else: app -> sync server.)
#
# Returns either a list of matches or the no-match sentinel:
#   { "ok": true,  "matches": [ {channel_id, channel_name,
#                                 programme_title, programme_sub,
#                                 start_utc_ms, stop_utc_ms}, ... ] }
#   { "ok": false, "reason": "no_epg_match" }
@router.get("/game/{game_id}/channels")
def sports_game_channels(
    game_id: int,
    host: str = Query(..., description="Xtream host (https://...)"),
    username: str = Query(...),
    password: str = Query(...),
    window_minutes: int = Query(30, ge=5, le=240),
) -> Dict[str, Any]:
    with _conn() as c:
        row = c.execute(
            "SELECT g.id, g.start_utc, ht.name AS home_name, "
            "       at_.name AS away_name "
            "FROM sports_games g "
            "LEFT JOIN sports_teams ht  ON ht.id  = g.home_team_id "
            "LEFT JOIN sports_teams at_ ON at_.id = g.away_team_id "
            "WHERE g.id=?",
            (game_id,),
        ).fetchone()
    if not row:
        raise HTTPException(404, "game not found")
    home = row["home_name"] or ""
    away = row["away_name"] or ""
    if not home or not away:
        return {"ok": False, "reason": "no_team_names"}

    # Lazy import so the new module isn't a hard dep on cold-start.
    from sports_epg import search_channels_for_game

    matches = search_channels_for_game(
        host=host,
        username=username,
        password=password,
        home_team=home,
        away_team=away,
        game_start_utc_ms=row["start_utc"],
        window_minutes=window_minutes,
    )
    if not matches:
        return {"ok": False, "reason": "no_epg_match"}
    return {"ok": True, "matches": matches}


@router.post("/epg/warm")
def sports_epg_warm(
    host: str = Query(...),
    username: str = Query(...),
    password: str = Query(...),
) -> Dict[str, Any]:
    """Warm-up endpoint — apps can hit this once at app launch to
    pre-fetch the playlist's EPG so the first /game/{id}/channels
    call is instant. Idempotent + cheap (re-uses cache if fresh)."""
    from sports_epg import warm_cache
    ok = warm_cache(host, username, password)
    return {"ok": bool(ok)}


# ── Admin endpoints ─────────────────────────────────────────────────
def _require_admin(token: Optional[str]) -> None:
    if not ADMIN_TOKEN:
        # Admin token not set in env → lock down entirely.
        raise HTTPException(503, "sports admin disabled (no token configured)")
    if token != ADMIN_TOKEN:
        raise HTTPException(401, "bad admin token")


class ChannelMapIn(BaseModel):
    scope: str = Field(..., pattern=r"^(league|team|event|ppv)$")
    scope_id: str
    channel_name: str
    priority: int = 100


@admin_router.get("/channel_map")
def admin_channel_map_list(
    scope: Optional[str] = None,
    league_slug: Optional[str] = None,
    x_admin_token: Optional[str] = Header(None),
) -> Dict[str, Any]:
    _require_admin(x_admin_token)
    with _conn() as c:
        q = "SELECT * FROM sports_channel_map WHERE active=1"
        args: List[Any] = []
        if scope:
            q += " AND scope=?"
            args.append(scope)
        rows = c.execute(q + " ORDER BY scope, scope_id", args).fetchall()
        out = []
        for r in rows:
            d = dict(r)
            # Enrich with team/league name if applicable
            if r["scope"] == "team":
                team = c.execute(
                    "SELECT sports_teams.name AS team_name, sports_leagues.slug AS league_slug "
                    "FROM sports_teams JOIN sports_leagues ON sports_teams.league_id = sports_leagues.id "
                    "WHERE sports_teams.id=?",
                    (r["scope_id"],),
                ).fetchone()
                if team:
                    d["team_name"] = team["team_name"]
                    d["league_slug"] = team["league_slug"]
                    if league_slug and team["league_slug"] != league_slug:
                        continue
            out.append(d)
    return {"mappings": out}


@admin_router.post("/channel_map")
def admin_channel_map_add(
    body: ChannelMapIn,
    x_admin_token: Optional[str] = Header(None),
) -> Dict[str, Any]:
    _require_admin(x_admin_token)
    with _conn() as c:
        c.execute(
            "INSERT OR REPLACE INTO sports_channel_map "
            "(scope, scope_id, channel_name, priority, active, created_ts) "
            "VALUES (?, ?, ?, ?, 1, ?)",
            (body.scope, body.scope_id, body.channel_name, body.priority,
             int(time.time() * 1000)),
        )
    return {"status": "ok"}


@admin_router.delete("/channel_map/{mapping_id}")
def admin_channel_map_delete(
    mapping_id: int,
    x_admin_token: Optional[str] = Header(None),
) -> Dict[str, Any]:
    _require_admin(x_admin_token)
    with _conn() as c:
        c.execute("UPDATE sports_channel_map SET active=0 WHERE id=?", (mapping_id,))
    return {"status": "ok"}


class PpvEventIn(BaseModel):
    title: str
    subtitle: Optional[str] = None
    poster_url: Optional[str] = None
    start_iso: str            # "2026-05-15T23:00:00Z"
    default_channel: Optional[str] = None
    admin_notes: Optional[str] = None


@admin_router.get("/ppv")
def admin_ppv_list(x_admin_token: Optional[str] = Header(None)) -> Dict[str, Any]:
    _require_admin(x_admin_token)
    with _conn() as c:
        rows = c.execute("SELECT * FROM sports_ppv ORDER BY start_utc DESC").fetchall()
    return {"events": [dict(r) for r in rows]}


@admin_router.post("/ppv")
def admin_ppv_add(
    body: PpvEventIn,
    x_admin_token: Optional[str] = Header(None),
) -> Dict[str, Any]:
    _require_admin(x_admin_token)
    start_ms = _utc_ts(body.start_iso)
    if not start_ms:
        raise HTTPException(400, "start_iso must be a valid ISO datetime")
    now = int(time.time() * 1000)
    with _conn() as c:
        c.execute(
            "INSERT INTO sports_ppv "
            "(source, source_id, title, subtitle, poster_url, start_utc, "
            " status, default_channel, admin_notes, created_ts, updated_ts) "
            "VALUES ('manual', NULL, ?, ?, ?, ?, 'scheduled', ?, ?, ?, ?)",
            (body.title, body.subtitle, body.poster_url, start_ms,
             body.default_channel, body.admin_notes, now, now),
        )
        rid = c.execute("SELECT last_insert_rowid() AS id").fetchone()["id"]
    return {"status": "ok", "id": rid}


@admin_router.delete("/ppv/{ppv_id}")
def admin_ppv_delete(ppv_id: int, x_admin_token: Optional[str] = Header(None)) -> Dict[str, Any]:
    _require_admin(x_admin_token)
    with _conn() as c:
        c.execute("DELETE FROM sports_ppv WHERE id=?", (ppv_id,))
    return {"status": "ok"}


@admin_router.post("/league/{slug}/active")
def admin_league_toggle(
    slug: str,
    active: bool = True,
    x_admin_token: Optional[str] = Header(None),
) -> Dict[str, Any]:
    _require_admin(x_admin_token)
    with _conn() as c:
        c.execute(
            "UPDATE sports_leagues SET active=? WHERE slug=?",
            (1 if active else 0, slug),
        )
    return {"status": "ok", "slug": slug, "active": active}


@admin_router.post("/refresh")
def admin_refresh(x_admin_token: Optional[str] = Header(None)) -> Dict[str, Any]:
    _require_admin(x_admin_token)
    counts = {}
    with _conn() as c:
        slugs = [r["slug"] for r in c.execute(
            "SELECT slug FROM sports_leagues WHERE active=1 "
            "ORDER BY display_order, slug").fetchall()]
    for slug in slugs:
        try:
            counts[slug] = refresh_league_schedule(slug)
        except Exception as e:
            counts[slug] = f"error: {e}"
    try:
        counts["ppv"] = refresh_ppv()
    except Exception as e:
        counts["ppv"] = f"error: {e}"
    try:
        counts["live"] = refresh_live_scores()
    except Exception as e:
        counts["live"] = f"error: {e}"
    counts["pruned"] = prune_old()
    return {"status": "ok", "counts": counts}


@admin_router.post("/mirror_logos")
def admin_mirror_logos(x_admin_token: Optional[str] = Header(None)) -> Dict[str, Any]:
    """Backfill: mirror every team logo that's still pointing at
    thesportsdb.com. Safe to run repeatedly — already-mirrored logos
    are skipped instantly."""
    _require_admin(x_admin_token)
    mirrored = 0
    skipped = 0
    failed = 0
    with _conn() as c:
        rows = c.execute(
            "SELECT id, sportsdb_id, badge_url FROM sports_teams"
        ).fetchall()
    for r in rows:
        if not r["sportsdb_id"]:
            continue
        if r["badge_url"] and r["badge_url"].startswith(LOGO_PUBLIC_BASE):
            skipped += 1
            continue
        public = _mirror_logo(r["sportsdb_id"], r["badge_url"])
        if public:
            with _conn() as c:
                c.execute(
                    "UPDATE sports_teams SET badge_url=?, logo_url=? WHERE id=?",
                    (public, public, r["id"]),
                )
            mirrored += 1
        else:
            failed += 1
    return {
        "status": "ok",
        "mirrored": mirrored,
        "already_mirrored": skipped,
        "failed": failed,
        "total": len(rows),
    }


# ── Module init ─────────────────────────────────────────────────────
def init() -> None:
    """Call once at FastAPI startup. Safe to call repeatedly."""
    _init_db()
    with _connect() as c:
        _ingest_leagues(c)
        _ingest_seed_channel_map(c)
    # v1.44.11 — separate connection so the logo backfill HTTP calls
    # don't hold a write transaction open during network I/O.
    try:
        with _conn() as c:
            _ingest_league_logos(c)
    except Exception as e:
        log.warning("league logo backfill failed: %s", e)
    start_worker()
