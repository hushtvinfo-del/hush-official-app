"""
HushTV Crash & ANR Monitor — admin-side aggregation over /var/hushtv-crash.

The Android app's `CrashReporter` posts each crash/anr/freeze/playback log
to the crash-intake service (/opt/hushtv-crash/app.py), which drops one
JSON file per report into:

    /var/hushtv-crash/{YYYY-MM-DD}/{HHMMSS-rnd-device}.json

Each file has fields:
    device, android_sdk, app_version, version_code, captured_at,
    installed_version, kind, trace, sent_at

This module:
    • lazily scans the last N days of those JSONs on each admin call
    • caches the parsed result in-memory for `_CACHE_TTL_SEC` so the
      page is snappy and we don't re-read 5,000 files per request
    • exposes /api/admin/canada/crashes/* endpoints for the admin UI

Mount sequence (in hushsync_app.py):
    app.include_router(crash_monitor.admin_router)

v1.44.113 — initial release.
"""

from __future__ import annotations

import glob
import json
import logging
import os
import re
import threading
import time
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from typing import Any, Optional

from fastapi import APIRouter, Header, HTTPException, Query

log = logging.getLogger(__name__)
admin_router = APIRouter(prefix="/api/admin/canada/crashes", tags=["crash-monitor"])

# Re-use the canada admin token (same SPORTS_ADMIN_TOKEN env var).
ADMIN_TOKEN = os.environ.get("SPORTS_ADMIN_TOKEN", "")
CRASH_DIR = "/var/hushtv-crash"
_CACHE_TTL_SEC = 30          # admin UI refresh cadence
_DEFAULT_DAYS  = 7           # default window to scan


def _check_admin(token: Optional[str]) -> None:
    if not ADMIN_TOKEN:
        # In dev / unconfigured environments, allow through but warn.
        return
    if token != ADMIN_TOKEN:
        raise HTTPException(403, "invalid admin token")


# ── cache ────────────────────────────────────────────────────────────────
_cache: dict[str, Any] = {}
_cache_lock = threading.Lock()


def _list_days(num_days: int) -> list[str]:
    """Return YYYY-MM-DD strings for the last `num_days` days."""
    today = datetime.now(timezone.utc).date()
    return [(today - timedelta(days=i)).strftime("%Y-%m-%d") for i in range(num_days)]


def _classify_kind(d: dict) -> str:
    """Crash reports older than v1.44.113 didn't tag `kind` for crash.log /
    anr.log uploads. We infer from the trace header `thread=...` so the
    dashboard groups them correctly with newer reports."""
    k = (d.get("kind") or "").strip().lower()
    if k in ("crash", "anr", "freeze", "diagnostic", "playback_telemetry"):
        return k
    trace = d.get("trace", "") or ""
    if "thread=anr-watchdog" in trace:
        return "anr"
    if "thread=freeze-monitor" in trace:
        return "freeze"
    if "thread=playback-telemetry" in trace:
        return "playback_telemetry"
    if "thread=diagnostic" in trace:
        return "diagnostic"
    # Plain crash.log has lines like "Exception in thread main" or stack.
    if re.search(r"Caused by:|Exception in thread|FATAL EXCEPTION", trace):
        return "crash"
    return "other"


def _scan(days: int) -> list[dict]:
    """Scan + parse all reports from the last `days` days. Cached.

    Returns a list of compact dicts (NOT full traces — those are huge).
    The full trace is fetched on demand by `/report/{file}` only.
    """
    cache_key = f"scan:{days}"
    with _cache_lock:
        entry = _cache.get(cache_key)
        if entry and time.time() - entry["t"] < _CACHE_TTL_SEC:
            return entry["data"]

    rows: list[dict] = []
    for day in _list_days(days):
        for path in sorted(glob.glob(f"{CRASH_DIR}/{day}/*.json")):
            try:
                d = json.load(open(path))
            except Exception:
                continue
            trace = d.get("trace", "") or ""
            rows.append({
                "file": f"{day}/{os.path.basename(path)}",
                "captured_at": d.get("captured_at") or d.get("sent_at"),
                "kind": _classify_kind(d),
                "device": d.get("device", "?"),
                "android_sdk": d.get("android_sdk", "?"),
                "app_version": d.get("app_version", "?"),
                "version_code": d.get("version_code", "?"),
                "installed_version": d.get("installed_version", "?"),
                "signature": _signature(trace),
                "trace_len": len(trace),
            })

    with _cache_lock:
        _cache[cache_key] = {"t": time.time(), "data": rows}
    return rows


def _signature(trace: str) -> str:
    """Cheap fingerprint for grouping equivalent crashes/ANRs.

    For an ANR it's "thread=anr-watchdog::top-main-stack-frame".
    For a crash it's "ExceptionClass::file:line of top app frame".
    For a freeze report it's the FREEZE-REPORT reason if present.
    """
    if not trace:
        return "(empty)"
    # ANR
    if "thread=anr-watchdog" in trace:
        m = re.search(
            r"Main thread stack at detection ───\n\s*at (\S+)\(",
            trace,
        )
        if m:
            return f"ANR::{m.group(1).split('$')[0]}"
        return "ANR::unknown"
    # FREEZE
    if "thread=freeze-monitor" in trace:
        m = re.search(r"reason=(\S+)", trace)
        if m:
            return f"FREEZE::{m.group(1)}"
        return "FREEZE::unknown"
    # JVM crash — pull the FIRST com.hushtv frame after a "Caused by"
    m = re.search(
        r"(?:Caused by:|FATAL EXCEPTION[\s\S]*?)\s*([\w\.\$]+(?:Exception|Error))[\s\S]*?at (com\.hushtv\.[^\(]+)\(",
        trace,
    )
    if m:
        return f"{m.group(1).rsplit('.', 1)[-1]}::{m.group(2)}"
    m = re.search(r"([\w\.\$]+(?:Exception|Error))", trace)
    if m:
        return m.group(1).rsplit('.', 1)[-1]
    return "(unknown)"


@admin_router.get("/stats")
def crash_stats(
    days: int = Query(7, ge=1, le=30),
    x_admin_token: Optional[str] = Header(None),
) -> dict:
    """Top-level stats for the dashboard: report counts, kind breakdown,
    hourly chart for the last 24h, per-version breakdown."""
    _check_admin(x_admin_token)
    rows = _scan(days)
    now = datetime.now(timezone.utc)

    # Kind totals over the window
    kind_total = Counter(r["kind"] for r in rows)

    # Hourly buckets for the last 24h
    hour_buckets: dict[str, Counter] = defaultdict(Counter)
    for r in rows:
        ts = _parse_ts(r["captured_at"])
        if ts is None:
            continue
        if now - ts > timedelta(hours=24):
            continue
        hkey = ts.strftime("%H:00")
        hour_buckets[hkey][r["kind"]] += 1
    hours = []
    # Always emit 24 buckets (last 24 hours) so the chart is contiguous
    for i in range(23, -1, -1):
        h = (now - timedelta(hours=i)).strftime("%H:00")
        b = hour_buckets.get(h, Counter())
        hours.append({
            "hour": h,
            "anr": b.get("anr", 0),
            "crash": b.get("crash", 0),
            "freeze": b.get("freeze", 0),
            "other": (sum(b.values()) - b.get("anr", 0)
                      - b.get("crash", 0) - b.get("freeze", 0)),
        })

    # Per-version counts (last 24h)
    ver_24h: Counter = Counter()
    for r in rows:
        ts = _parse_ts(r["captured_at"])
        if ts is None or now - ts > timedelta(hours=24):
            continue
        if r["kind"] in ("anr", "crash"):
            ver_24h[r["app_version"]] += 1
    version_breakdown = [
        {"version": v, "count": n}
        for v, n in ver_24h.most_common(10)
    ]

    # Per-device family counts (last 24h, ANR + crash only)
    dev_24h: Counter = Counter()
    for r in rows:
        ts = _parse_ts(r["captured_at"])
        if ts is None or now - ts > timedelta(hours=24):
            continue
        if r["kind"] not in ("anr", "crash"):
            continue
        d = r["device"]
        # Strip the trailing -<id> hash so different devices of the same
        # model aggregate together.
        family = "-".join(d.split("-")[:-1]) if "-" in d else d
        dev_24h[family or d] += 1
    device_breakdown = [
        {"device": k, "count": n}
        for k, n in dev_24h.most_common(10)
    ]

    # 7-day totals so we can compare yesterday vs today
    today_str = now.strftime("%Y-%m-%d")
    yesterday_str = (now - timedelta(days=1)).strftime("%Y-%m-%d")
    today_anr = sum(1 for r in rows if r["kind"] == "anr" and (r["captured_at"] or "").startswith(today_str))
    yest_anr  = sum(1 for r in rows if r["kind"] == "anr" and (r["captured_at"] or "").startswith(yesterday_str))
    today_crash = sum(1 for r in rows if r["kind"] == "crash" and (r["captured_at"] or "").startswith(today_str))
    yest_crash  = sum(1 for r in rows if r["kind"] == "crash" and (r["captured_at"] or "").startswith(yesterday_str))

    return {
        "window_days": days,
        "now": now.isoformat(),
        "totals": {
            "all":       sum(kind_total.values()),
            "anr":       kind_total.get("anr", 0),
            "crash":     kind_total.get("crash", 0),
            "freeze":    kind_total.get("freeze", 0),
            "playback":  kind_total.get("playback_telemetry", 0),
            "diagnostic": kind_total.get("diagnostic", 0),
            "other":     kind_total.get("other", 0),
        },
        "today_vs_yesterday": {
            "anr":   {"today": today_anr,   "yesterday": yest_anr,
                      "delta": today_anr - yest_anr},
            "crash": {"today": today_crash, "yesterday": yest_crash,
                      "delta": today_crash - yest_crash},
        },
        "hourly_24h": hours,
        "version_breakdown_24h": version_breakdown,
        "device_breakdown_24h": device_breakdown,
    }


@admin_router.get("/top")
def crash_top(
    days: int = Query(1, ge=1, le=14),
    kind: str = Query("anr", regex=r"^(anr|crash|freeze|all)$"),
    limit: int = Query(20, ge=1, le=100),
    x_admin_token: Optional[str] = Header(None),
) -> dict:
    """Top crash signatures grouped, with a representative-report file
    pointer so the admin can drill in to one example."""
    _check_admin(x_admin_token)
    rows = _scan(days)
    cutoff = datetime.now(timezone.utc) - timedelta(days=days)

    grouped: dict[str, dict] = {}
    for r in rows:
        if kind != "all" and r["kind"] != kind:
            continue
        ts = _parse_ts(r["captured_at"])
        if ts is None or ts < cutoff:
            continue
        sig = r["signature"]
        g = grouped.setdefault(sig, {
            "signature": sig, "count": 0,
            "versions": Counter(), "devices": Counter(),
            "first_seen": r["captured_at"], "last_seen": r["captured_at"],
            "sample_file": r["file"],
        })
        g["count"] += 1
        g["versions"][r["app_version"]] += 1
        g["devices"][r["device"]] += 1
        if (r["captured_at"] or "") > (g["last_seen"] or ""):
            g["last_seen"] = r["captured_at"]
            g["sample_file"] = r["file"]
        if (r["captured_at"] or "") < (g["first_seen"] or ""):
            g["first_seen"] = r["captured_at"]

    out = []
    for sig, g in sorted(grouped.items(), key=lambda kv: -kv[1]["count"])[:limit]:
        out.append({
            "signature": g["signature"],
            "count": g["count"],
            "first_seen": g["first_seen"],
            "last_seen": g["last_seen"],
            "top_versions": [{"v": v, "n": n} for v, n in g["versions"].most_common(3)],
            "top_devices":  [{"d": d, "n": n} for d, n in g["devices"].most_common(3)],
            "sample_file":  g["sample_file"],
        })
    return {"window_days": days, "kind": kind, "groups": out}


@admin_router.get("/recent")
def crash_recent(
    days: int = Query(1, ge=1, le=14),
    kind: str = Query("all", regex=r"^(anr|crash|freeze|playback_telemetry|diagnostic|other|all)$"),
    limit: int = Query(50, ge=1, le=500),
    x_admin_token: Optional[str] = Header(None),
) -> dict:
    """Most recent crash/anr reports, newest first."""
    _check_admin(x_admin_token)
    rows = _scan(days)
    cutoff = datetime.now(timezone.utc) - timedelta(days=days)
    out = []
    for r in rows:
        if kind != "all" and r["kind"] != kind:
            continue
        ts = _parse_ts(r["captured_at"])
        if ts is None or ts < cutoff:
            continue
        out.append(r)
    out.sort(key=lambda r: r.get("captured_at") or "", reverse=True)
    return {"window_days": days, "kind": kind, "rows": out[:limit]}


@admin_router.get("/report")
def crash_report(
    file: str = Query(..., min_length=10, max_length=200),
    x_admin_token: Optional[str] = Header(None),
) -> dict:
    """Fetch the full trace for one report. `file` is the value returned
    in `/recent` rows or `/top` group.sample_file ("YYYY-MM-DD/...json").
    """
    _check_admin(x_admin_token)
    # Strict path safety: only allow "YYYY-MM-DD/filename.json"
    if not re.match(r"^\d{4}-\d{2}-\d{2}/[\w.\-]+\.json$", file):
        raise HTTPException(400, "bad file id")
    path = os.path.join(CRASH_DIR, file)
    if not os.path.isfile(path):
        raise HTTPException(404, "not found")
    try:
        d = json.load(open(path))
    except Exception as e:
        raise HTTPException(500, f"parse failed: {e}")
    # Don't include sent_at/headers we don't display — keep payload lean.
    return {
        "file": file,
        "kind": _classify_kind(d),
        "device": d.get("device", "?"),
        "android_sdk": d.get("android_sdk", "?"),
        "app_version": d.get("app_version", "?"),
        "version_code": d.get("version_code", "?"),
        "installed_version": d.get("installed_version", "?"),
        "captured_at": d.get("captured_at"),
        "sent_at": d.get("sent_at"),
        "trace": d.get("trace", ""),
    }


def _parse_ts(s: Optional[str]) -> Optional[datetime]:
    if not s:
        return None
    try:
        # captured_at is ISO 8601 UTC (e.g. "2026-05-20T20:13:01Z" or with offset)
        if s.endswith("Z"):
            s = s[:-1] + "+00:00"
        return datetime.fromisoformat(s)
    except Exception:
        return None
