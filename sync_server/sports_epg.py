"""
Sports EPG matcher — finds Xtream channels currently airing a given
sports event by searching the user's Xtream EPG (xmltv.php) for
programmes whose title or description contains BOTH team names within
a ±30 min window of the event's start time.

This module is purely additive — it doesn't replace the existing
TheSportsDB-broadcast resolver in sports_module.py. The TV/mobile
client calls /api/sports/game/{id}/channels to get a ranked list of
"channels currently airing this match" and shows it to the user as
a "watch on…" picker. Replaces the old behaviour of tuning to a
single guessed channel.

Cache strategy (per user direction, v1.44.27):
  • Backend (this module) caches the full xmltv.php once per
    (host, username) for 4 hours. ~5–10 MB compressed.
  • All concurrent users on the same playlist share the same cache
    entry — so the FIRST page load triggers a 2–3 s xmltv pull,
    subsequent loads are <100 ms in-memory searches.
  • Cache survives process restarts via /var/hushtv-sync/epg_cache/
    so a redeploy doesn't blank everyone's EPG.
"""

from __future__ import annotations

import gzip
import hashlib
import io
import logging
import os
import re
import threading
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import httpx

log = logging.getLogger(__name__)

CACHE_DIR = Path(os.environ.get(
    "HUSHTV_EPG_CACHE_DIR", "/var/hushtv-sync/epg_cache"
))
CACHE_DIR.mkdir(parents=True, exist_ok=True)
EPG_TTL_SEC = 4 * 3600

# In-memory parsed-EPG cache. Keyed by playlist_key (sha1 of host+user).
# Each value is a dict:
#   {
#     "expires": float (epoch sec),
#     "channels": { tvg_id: { "name": str, "display_names": [str] } },
#     "programmes": [
#         { "channel": tvg_id, "start": dt, "stop": dt,
#           "title": str, "title_lc": str, "desc_lc": str },
#         ...
#     ]
#   }
_EPG_CACHE: Dict[str, Dict[str, Any]] = {}
_EPG_LOCK = threading.RLock()

# Canadian-priority broadcaster patterns (same as sports_module.py).
_CANADIAN_PATTERNS = [
    "sportsnet", "tsn", "rds", "tva sports", "citytv", "city tv",
    "ctv", "cbc", "global", "bnn", "fubo",
]


# ── Helpers ────────────────────────────────────────────────────────


def _playlist_key(host: str, username: str) -> str:
    """Stable cache key — sha1 of host+username. We don't include
    the password so multiple admins fronting the same Xtream box
    share the cache. Salt keeps the key from being obviously
    reversible if someone glances at /var/hushtv-sync/epg_cache/."""
    return hashlib.sha1(
        f"hushtv-epg|{host.lower().strip()}|{username.strip()}".encode()
    ).hexdigest()


def _xmltv_url(host: str, username: str, password: str) -> str:
    """Xtream's xmltv.php endpoint. Always served by the same Xtream
    panel that serves player_api.php."""
    base = host.rstrip("/")
    return f"{base}/xmltv.php?username={username}&password={password}"


def _parse_xmltv_time(raw: str) -> Optional[datetime]:
    """XMLTV timestamps look like '20260507T010000 +0000' or
    '20260507010000 +0000'. We only need the date+time; timezones
    in xmltv are in ±HHMM."""
    if not raw:
        return None
    s = raw.strip().replace("T", "")
    # Split off timezone if present
    parts = s.split(" ")
    ts = parts[0]
    tz_str = parts[1] if len(parts) > 1 else "+0000"
    try:
        dt = datetime.strptime(ts[:14], "%Y%m%d%H%M%S")
        # Parse tz like '+0000', '-0500'
        sign = 1 if tz_str[0] == "+" else -1
        hours = int(tz_str[1:3])
        mins = int(tz_str[3:5])
        from datetime import timedelta, timezone as _tz
        offset = _tz(sign * timedelta(hours=hours, minutes=mins))
        return dt.replace(tzinfo=offset).astimezone(timezone.utc)
    except Exception:
        return None


def _fetch_xmltv(host: str, username: str, password: str) -> bytes:
    """Download xmltv.php with a generous timeout. Some Xtream
    panels serve the whole EPG as gzip even without compression
    request headers; we handle that."""
    url = _xmltv_url(host, username, password)
    log.info("EPG: fetching xmltv.php from %s (user=%s)",
             host, username)
    with httpx.Client(timeout=60.0, follow_redirects=True) as client:
        r = client.get(url)
        r.raise_for_status()
    body = r.content
    # Auto-detect gzip header (sometimes content-encoding is missing).
    if body[:2] == b"\x1f\x8b":
        body = gzip.decompress(body)
    return body


def _build_index(xmltv_bytes: bytes) -> Dict[str, Any]:
    """Parse XMLTV into channel + programme indexes. Only keeps the
    fields we actually use to keep RSS reasonable. Lower-cases
    title+desc once at parse time so per-game lookups are pure
    string ops."""
    channels: Dict[str, Dict[str, Any]] = {}
    programmes: List[Dict[str, Any]] = []
    # Use iterparse for memory efficiency on big EPGs.
    src = io.BytesIO(xmltv_bytes)
    for _, elem in ET.iterparse(src, events=("end",)):
        if elem.tag == "channel":
            cid = elem.attrib.get("id") or ""
            if not cid:
                elem.clear()
                continue
            display_names = [
                (dn.text or "").strip()
                for dn in elem.findall("display-name")
                if dn.text
            ]
            primary = display_names[0] if display_names else cid
            channels[cid] = {
                "name": primary,
                "display_names": display_names,
            }
            elem.clear()
        elif elem.tag == "programme":
            cid = elem.attrib.get("channel") or ""
            start = _parse_xmltv_time(elem.attrib.get("start", ""))
            stop = _parse_xmltv_time(elem.attrib.get("stop", ""))
            title_el = elem.find("title")
            desc_el = elem.find("desc")
            sub_el = elem.find("sub-title")
            title = (title_el.text or "").strip() if title_el is not None else ""
            desc = (desc_el.text or "").strip() if desc_el is not None else ""
            sub = (sub_el.text or "").strip() if sub_el is not None else ""
            if cid and start and stop and (title or desc or sub):
                programmes.append({
                    "channel": cid,
                    "start": start,
                    "stop": stop,
                    "title": title,
                    "sub": sub,
                    # Combined searchable text — title + sub + desc,
                    # all lowercased once for cheap substring search.
                    "haystack": (
                        f"{title} {sub} {desc}"
                    ).lower(),
                })
            elem.clear()
    return {"channels": channels, "programmes": programmes}


def _load_or_fetch(
    host: str,
    username: str,
    password: str,
    force: bool = False,
) -> Optional[Dict[str, Any]]:
    """Return the parsed EPG index for a playlist, fetching+parsing
    if cache is missing or expired. Returns None if the fetch fails
    (callers fall back to the auto-mapper)."""
    key = _playlist_key(host, username)
    now = time.time()

    with _EPG_LOCK:
        cached = _EPG_CACHE.get(key)
        if cached and not force and cached["expires"] > now:
            return cached

    # Try disk cache for cold-start.
    disk_path = CACHE_DIR / f"{key}.xml.gz"
    use_disk = (
        not force
        and disk_path.exists()
        and (now - disk_path.stat().st_mtime) < EPG_TTL_SEC
    )
    raw: Optional[bytes] = None
    if use_disk:
        try:
            raw = gzip.decompress(disk_path.read_bytes())
            log.info("EPG: loaded from disk cache %s", disk_path.name)
        except Exception:
            log.exception("EPG: disk cache read failed, refetching")
            raw = None

    if raw is None:
        try:
            raw = _fetch_xmltv(host, username, password)
        except Exception:
            log.exception("EPG: fetch failed for host=%s user=%s",
                          host, username)
            # If we have stale in-memory cache, return that rather
            # than nothing — better outdated than none.
            with _EPG_LOCK:
                stale = _EPG_CACHE.get(key)
            return stale
        # Persist to disk gzipped.
        try:
            disk_path.write_bytes(gzip.compress(raw))
        except Exception:
            log.exception("EPG: disk cache write failed (non-fatal)")

    # Parse + cache.
    try:
        parsed = _build_index(raw)
    except Exception:
        log.exception("EPG: parse failed for host=%s", host)
        return None
    entry = {
        "expires": now + EPG_TTL_SEC,
        "channels": parsed["channels"],
        "programmes": parsed["programmes"],
    }
    with _EPG_LOCK:
        _EPG_CACHE[key] = entry
    log.info(
        "EPG: cached %d channels, %d programmes for host=%s",
        len(parsed["channels"]),
        len(parsed["programmes"]),
        host,
    )
    return entry


# ── Search ─────────────────────────────────────────────────────────


def _terms_for_team(team_name: str) -> List[str]:
    """Generate search terms for a team. We try the FULL name first
    ('Montreal Canadiens'), then the SHORT name ('Canadiens') if
    the EPG doesn't include the city. Stripping '*FC' and team
    suffixes catches soccer titles that just say 'Manchester United'
    when our team is 'Manchester United FC'."""
    if not team_name:
        return []
    raw = team_name.strip()
    out: List[str] = [raw.lower()]
    # Strip common trailing words ('FC', 'CF', 'United') for soccer
    # but only if there's still 1+ token left.
    tokens = raw.split()
    if len(tokens) >= 2:
        # Last token (e.g. "Canadiens" out of "Montreal Canadiens")
        last = tokens[-1].lower()
        if last not in ("fc", "cf", "united", "city"):
            out.append(last)
        # First token if it's a non-trivial city (e.g. "Montreal")
        first = tokens[0].lower()
        if len(first) >= 4 and first not in (
            "los", "new", "san", "the", "fc"
        ):
            out.append(first)
    # Dedup preserving order.
    seen: set = set()
    result: List[str] = []
    for t in out:
        if t not in seen:
            seen.add(t)
            result.append(t)
    return result


def _is_canadian_channel(name: str) -> bool:
    n = name.lower()
    return any(p in n for p in _CANADIAN_PATTERNS)


def _channel_sort_key(ch_name: str) -> Tuple[int, str]:
    """Canadian first (rank 0), then alpha. Used as the final sort
    key after EPG-match-quality has been applied."""
    return (0 if _is_canadian_channel(ch_name) else 1, ch_name.lower())


def search_channels_for_game(
    host: str,
    username: str,
    password: str,
    home_team: str,
    away_team: str,
    game_start_utc_ms: int,
    window_minutes: int = 30,
) -> List[Dict[str, Any]]:
    """Return a list of EPG matches for the given game, Canadian-first
    sorted. Empty list if EPG isn't available or no matches.

    Each result is:
      {
        "channel_id":     str,   # XMLTV channel id (matches Xtream tvg_id)
        "channel_name":   str,   # primary display-name from EPG
        "programme_title":str,
        "programme_sub":  str,
        "start_utc_ms":   int,
        "stop_utc_ms":    int,
      }
    """
    epg = _load_or_fetch(host, username, password)
    if not epg:
        return []

    home_terms = _terms_for_team(home_team)
    away_terms = _terms_for_team(away_team)
    if not home_terms or not away_terms:
        return []

    game_start = datetime.fromtimestamp(
        game_start_utc_ms / 1000, tz=timezone.utc,
    )
    from datetime import timedelta
    win_lo = game_start - timedelta(minutes=window_minutes)
    win_hi = game_start + timedelta(minutes=window_minutes)

    matches: List[Tuple[int, Dict[str, Any]]] = []
    seen_channels: set = set()
    for prog in epg["programmes"]:
        # Skip programmes far outside the window. Programs that
        # OVERLAP the window count.
        if prog["stop"] < win_lo:
            continue
        if prog["start"] > win_hi:
            continue
        haystack = prog["haystack"]
        # Title or description must contain at least one home-term
        # AND at least one away-term. Try each pair to handle the
        # full-name / short-name combos.
        home_hit = next(
            (t for t in home_terms if t in haystack),
            None,
        )
        away_hit = next(
            (t for t in away_terms if t in haystack),
            None,
        )
        if not (home_hit and away_hit):
            continue
        cid = prog["channel"]
        if cid in seen_channels:
            continue
        seen_channels.add(cid)
        ch = epg["channels"].get(cid) or {}
        ch_name = ch.get("name") or cid

        # Match-quality score:
        #   2: title contains both team names
        #   1: title or sub contains a name, desc the other
        #   0: only desc contains both
        title_lc = (prog["title"] or "").lower()
        sub_lc = (prog.get("sub") or "").lower()
        title_has_home = home_hit in title_lc or home_hit in sub_lc
        title_has_away = away_hit in title_lc or away_hit in sub_lc
        if title_has_home and title_has_away:
            score = 2
        elif title_has_home or title_has_away:
            score = 1
        else:
            score = 0

        matches.append((score, {
            "channel_id": cid,
            "channel_name": ch_name,
            "programme_title": prog["title"],
            "programme_sub": prog.get("sub") or "",
            "start_utc_ms": int(prog["start"].timestamp() * 1000),
            "stop_utc_ms": int(prog["stop"].timestamp() * 1000),
            "_score": score,
        }))

    if not matches:
        return []

    # Sort: Canadian first, then by match-quality desc, then alpha.
    # (User direction: Canadian first then alpha — score is a
    # tiebreaker among Canadian channels with equal score.)
    matches.sort(key=lambda sm: (
        _channel_sort_key(sm[1]["channel_name"]),
        -sm[0],
    ))
    out = []
    for _, m in matches:
        m.pop("_score", None)
        out.append(m)
    return out


def warm_cache(host: str, username: str, password: str) -> bool:
    """Force a fetch+parse to warm the cache. Useful from a
    backend warm-up cron or as a /api/sports/epg/refresh admin
    endpoint."""
    return _load_or_fetch(host, username, password, force=True) is not None
