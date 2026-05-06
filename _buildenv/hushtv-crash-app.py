#!/usr/bin/env python3
"""
HushTV crash report service.

Receives JSON crash reports POSTed from the HushTV Android app and
writes them to /var/hushtv-crash/<YYYY-MM-DD>/<timestamp>-<device>.json.
Also serves a basic-auth-protected HTML dashboard for browsing them.

Dependencies: Python 3 stdlib only. No pip / venv required — runs as a
systemd service on localhost:5055, fronted by nginx (/crash → proxy).

URL layout:
    POST /submit/<SECRET>   — accept JSON report, write to disk
    GET  /                  — HTML dashboard (basic auth)
    GET  /report/<path>     — raw JSON for a single report (basic auth)
    GET  /summary?since=24h — JSON summary grouped by (device, exception)
                              (basic auth) — added v1.44.20

Security note: the SECRET in the submit URL is a shared token baked
into the APK at build time. It's not strong auth — it just keeps web
crawlers and randos from filling up the disk. Dashboard is gated by
HTTP Basic Auth using credentials in /etc/hushtv-crash/config.env.
"""

from __future__ import annotations

import base64
import html
import http.server
import json
import os
import re
import socketserver
import sys
import threading
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

# ── Config ────────────────────────────────────────────────────────────
PORT = int(os.environ.get("HUSHTV_CRASH_PORT", "5055"))
DATA_DIR = Path(os.environ.get("HUSHTV_CRASH_DATA", "/var/hushtv-crash"))
SUBMIT_SECRET = os.environ.get("HUSHTV_CRASH_SECRET", "")
BASIC_USER = os.environ.get("HUSHTV_CRASH_USER", "admin")
BASIC_PASS = os.environ.get("HUSHTV_CRASH_PASS", "")
MAX_BODY = 512 * 1024  # 512 KB — crash traces should never come close

if not SUBMIT_SECRET:
    sys.stderr.write("HUSHTV_CRASH_SECRET missing; refusing to start.\n")
    sys.exit(1)
if not BASIC_PASS:
    sys.stderr.write("HUSHTV_CRASH_PASS missing; refusing to start.\n")
    sys.exit(1)

DATA_DIR.mkdir(parents=True, exist_ok=True)

# ── HTML dashboard template ───────────────────────────────────────────
DASHBOARD_STYLE = """
body { font-family: -apple-system, Segoe UI, system-ui, sans-serif; background:#05080f; color:#e5e7eb; margin:0; }
.wrap { max-width:1100px; margin:0 auto; padding:24px; }
h1 { margin:0 0 4px; font-size:26px; letter-spacing:-0.5px; }
h2 { margin:32px 0 12px; font-size:18px; font-weight:800; letter-spacing:-0.3px; }
.sub { color:#64748b; font-size:12px; letter-spacing:1px; font-weight:800; margin-bottom:20px; text-transform:uppercase; }
.card { background:#0a1220; border:1px solid rgba(6,182,212,.2); border-radius:10px; padding:14px 16px; margin-bottom:8px; }
.card a { color:#06b6d4; text-decoration:none; font-weight:700; }
.card a:hover { text-decoration:underline; }
.meta { color:#94a3b8; font-size:11px; font-family: monospace; margin-top:4px; }
.trace { color:#f87171; font-family:monospace; font-size:11px; margin-top:6px; white-space:pre-wrap; word-break:break-word; max-height:4.5em; overflow:hidden; }
pre { background:#0a1220; border:1px solid rgba(6,182,212,.2); border-radius:10px; padding:16px; overflow-x:auto; font-size:12px; line-height:1.5; color:#e5e7eb; }
.empty { color:#64748b; padding:40px 0; text-align:center; font-size:14px; }
.back { color:#06b6d4; font-size:12px; font-weight:700; display:inline-block; margin-bottom:12px; text-transform:uppercase; letter-spacing:1.2px; }
.chip { display:inline-block; background:rgba(6,182,212,.15); color:#06b6d4; border:1px solid rgba(6,182,212,.4); border-radius:5px; padding:2px 8px; font-size:10px; font-weight:800; letter-spacing:1px; margin-right:6px; }
.chip.warn { background:rgba(239,68,68,.15); color:#f87171; border-color:rgba(239,68,68,.4); }
.chip.freeze { background:rgba(59,130,246,.15); color:#60a5fa; border-color:rgba(59,130,246,.4); }
table.summary { width:100%; border-collapse:collapse; background:#0a1220; border:1px solid rgba(6,182,212,.2); border-radius:10px; overflow:hidden; }
table.summary th { background:#0e1830; color:#94a3b8; font-size:11px; letter-spacing:1px; text-align:left; padding:10px 14px; text-transform:uppercase; font-weight:800; }
table.summary td { padding:10px 14px; border-top:1px solid rgba(255,255,255,.05); font-size:13px; }
table.summary tr:hover { background:rgba(6,182,212,.05); }
.count-cell { font-family:monospace; font-weight:800; color:#06b6d4; }
.exc-cell { font-family:monospace; color:#f87171; font-size:12px; word-break:break-all; }
.tabs { margin:8px 0 18px; }
.tab { display:inline-block; padding:6px 14px; border:1px solid rgba(6,182,212,.4); border-radius:999px; font-size:11px; font-weight:800; letter-spacing:1.2px; text-transform:uppercase; color:#06b6d4; text-decoration:none; margin-right:6px; }
.tab.active { background:#06b6d4; color:#05080f; border-color:#06b6d4; }
"""

# Map "since" query string → timedelta. "all" returns None (no cap).
_SINCE_RE = re.compile(r"^(\d+)([hd])$")


def _parse_since(s: str) -> timedelta | None:
    """Parse strings like "24h", "7d", "all" into a timedelta or None."""
    if not s or s == "all":
        return None
    m = _SINCE_RE.match(s)
    if not m:
        return timedelta(hours=24)
    n, unit = int(m.group(1)), m.group(2)
    return timedelta(hours=n) if unit == "h" else timedelta(days=n)


def _device_model(raw: str) -> str:
    """Trim the per-install id off the device string.
    Example: 'skyworth-SWTV-24NA-FHD-48ffe8e6' → 'skyworth-SWTV-24NA-FHD'.
    Heuristic: drop the LAST dash-separated chunk if it looks like a
    short hex id (8 chars, all hex). Falls back to the raw string."""
    if not raw:
        return "unknown"
    parts = raw.split("-")
    if len(parts) >= 2:
        last = parts[-1]
        if 6 <= len(last) <= 16 and re.fullmatch(r"[0-9a-fA-F]+", last):
            return "-".join(parts[:-1])
    return raw


_EXCEPTION_RE = re.compile(r"\b([a-zA-Z_][\w.$]*Exception|[a-zA-Z_][\w.$]*Error)\b")


def _exception_class(trace: str) -> str:
    """Pull the FIRST 'java.lang.X' / 'kotlin.X' class out of a trace."""
    if not trace:
        return "Unknown"
    # Each crash file may be a multi-crash log — only inspect first frame.
    head = trace[:4000]
    m = _EXCEPTION_RE.search(head)
    return m.group(1) if m else "Unknown"


def _list_reports() -> list[tuple[Path, dict]]:
    items: list[tuple[Path, dict]] = []
    for day in sorted(DATA_DIR.iterdir(), reverse=True):
        if not day.is_dir():
            continue
        for rep in sorted(day.iterdir(), reverse=True):
            if rep.suffix != ".json":
                continue
            try:
                data = json.loads(rep.read_text(encoding="utf-8"))
            except Exception:
                data = {"_corrupt": True}
            items.append((rep, data))
    return items


def _summary(since_delta: timedelta | None) -> dict:
    """Aggregate crashes within `since_delta` (or all-time if None) into
    a flat list grouped by (device_model, exception_class).

    Output:
      {
        "since": "24h" | "all",
        "since_iso": "2026-05-05T18:00:00+00:00",
        "total": 7,
        "groups": [
          { "device_model": "...", "exception": "java.lang.IllegalArgumentException",
            "count": 3, "version_codes": ["419","420"], "first_seen": "...",
            "last_seen": "...", "kinds": ["crash","freeze"] },
          ...
        ]
      }
    Sorted by count DESC then last_seen DESC. Easy to render as a table."""
    cutoff = (datetime.now(timezone.utc) - since_delta) if since_delta else None
    buckets: dict[tuple[str, str], dict] = defaultdict(lambda: {
        "count": 0,
        "version_codes": set(),
        "first_seen": None,
        "last_seen": None,
        "kinds": set(),
        "sample_path": None,
    })
    total = 0
    for rep_path, data in _list_reports():
        sent_at = data.get("sent_at") or data.get("captured_at")
        try:
            ts = datetime.fromisoformat(str(sent_at).replace("Z", "+00:00"))
        except Exception:
            ts = None
        if cutoff and ts and ts < cutoff:
            continue
        total += 1
        device = _device_model(str(data.get("device", "unknown")))
        exc = _exception_class(str(data.get("trace", "")))
        b = buckets[(device, exc)]
        b["count"] += 1
        b["version_codes"].add(str(data.get("version_code") or data.get("app_version") or "?"))
        b["kinds"].add(str(data.get("kind") or "crash"))
        if ts:
            if b["first_seen"] is None or ts < b["first_seen"]:
                b["first_seen"] = ts
            if b["last_seen"] is None or ts > b["last_seen"]:
                b["last_seen"] = ts
        if b["sample_path"] is None:
            b["sample_path"] = rep_path.relative_to(DATA_DIR).as_posix()

    groups = []
    for (device, exc), b in buckets.items():
        groups.append({
            "device_model": device,
            "exception": exc,
            "count": b["count"],
            "version_codes": sorted(b["version_codes"]),
            "first_seen": b["first_seen"].isoformat() if b["first_seen"] else None,
            "last_seen": b["last_seen"].isoformat() if b["last_seen"] else None,
            "kinds": sorted(b["kinds"]),
            "sample_path": b["sample_path"],
        })
    groups.sort(key=lambda g: (-g["count"], g["last_seen"] or ""), reverse=False)
    # `reverse=False` because we constructed the key as negative count;
    # this gives DESC by count then DESC by last_seen.
    groups.sort(key=lambda g: (g["count"], g["last_seen"] or ""), reverse=True)

    return {
        "since": "all" if since_delta is None else _delta_label(since_delta),
        "since_iso": cutoff.isoformat() if cutoff else None,
        "total": total,
        "groups": groups,
    }


def _delta_label(td: timedelta) -> str:
    h = int(td.total_seconds() // 3600)
    if h % 24 == 0 and h > 0:
        return f"{h // 24}d"
    return f"{h}h"


def _summary_table_html(summary: dict) -> str:
    groups = summary["groups"]
    if not groups:
        return f"<div class='empty'>No crashes in the last {html.escape(summary['since'])}.</div>"
    rows = []
    for g in groups:
        last = (g["last_seen"] or "")[:19].replace("T", " ")
        sample = g.get("sample_path") or ""
        sample_link = (
            f"<a href='/crash/report/{html.escape(sample)}'>view</a>"
            if sample else "—"
        )
        kinds_chip = "".join(
            f"<span class='chip {('freeze' if k=='freeze' else 'warn')}'>{html.escape(k)}</span>"
            for k in g["kinds"]
        )
        ver_chips = " ".join(
            f"<span class='chip'>#{html.escape(v)}</span>" for v in g["version_codes"]
        )
        rows.append(
            f"<tr>"
            f"<td class='count-cell'>{g['count']}</td>"
            f"<td>{html.escape(g['device_model'])}</td>"
            f"<td class='exc-cell'>{html.escape(g['exception'])}</td>"
            f"<td>{ver_chips}</td>"
            f"<td>{kinds_chip}</td>"
            f"<td><span class='meta'>{html.escape(last)}</span></td>"
            f"<td>{sample_link}</td>"
            f"</tr>"
        )
    return (
        "<table class='summary'>"
        "<tr><th>#</th><th>Device</th><th>Exception</th><th>Versions</th>"
        "<th>Kinds</th><th>Last seen (UTC)</th><th>Sample</th></tr>"
        + "".join(rows) +
        "</table>"
    )


def _dashboard_html(summary_since: str = "24h") -> bytes:
    rows = _list_reports()
    summary = _summary(_parse_since(summary_since))
    body_parts: list[str] = []
    body_parts.append(f"<!doctype html><html><head><meta charset='utf-8'>")
    body_parts.append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
    body_parts.append(f"<title>HushTV Crash Reports</title><style>{DASHBOARD_STYLE}</style></head><body><div class='wrap'>")
    body_parts.append(f"<div class='sub'>HushTV · diagnostics</div>")
    body_parts.append(f"<h1>Crash Reports ({len(rows)})</h1>")

    # ── Summary section (v1.44.20) ──
    body_parts.append(f"<h2>Summary — last {html.escape(summary['since'])} · {summary['total']} crashes</h2>")
    body_parts.append("<div class='tabs'>")
    for label in ("24h", "7d", "30d", "all"):
        cls = "tab active" if label == summary_since else "tab"
        body_parts.append(f"<a class='{cls}' href='/crash/?since={label}'>{label}</a>")
    body_parts.append("</div>")
    body_parts.append(_summary_table_html(summary))

    # ── Full per-report list ──
    body_parts.append(f"<h2>All Reports ({len(rows)})</h2>")
    if not rows:
        body_parts.append("<div class='empty'>No crashes have been reported yet. 🎉</div>")
    for rep_path, data in rows:
        rel = rep_path.relative_to(DATA_DIR).as_posix()
        ver = html.escape(str(data.get("app_version", "?")))
        device = html.escape(str(data.get("device", "?")))
        ts = html.escape(str(data.get("captured_at", "?")))
        trace = html.escape(str(data.get("trace", ""))[:400])
        sent = html.escape(str(data.get("sent_at", "?")))
        kind = str(data.get("kind", "crash"))
        kind_chip = f"<span class='chip {('freeze' if kind=='freeze' else 'warn')}'>{html.escape(kind)}</span>"
        body_parts.append(
            f"<div class='card'>"
            f"<a href='/crash/report/{html.escape(rel)}'>{html.escape(rel)}</a>"
            f"<div class='meta'>"
            f"{kind_chip}"
            f"<span class='chip'>v{ver}</span>"
            f"<span class='chip'>{device}</span>"
            f"<span class='chip warn'>captured {ts}</span>"
            f"<span class='chip'>sent {sent}</span>"
            f"</div>"
            f"<div class='trace'>{trace}</div>"
            f"</div>"
        )
    body_parts.append("</div></body></html>")
    return "".join(body_parts).encode("utf-8")


def _report_html(rel: str) -> bytes | None:
    safe = Path(rel).as_posix()
    # Reject anything with ".." or absolute paths.
    if ".." in safe or safe.startswith("/"):
        return None
    target = (DATA_DIR / safe).resolve()
    # Must stay under DATA_DIR.
    try:
        target.relative_to(DATA_DIR.resolve())
    except ValueError:
        return None
    if not target.is_file():
        return None
    text = target.read_text(encoding="utf-8")
    try:
        data = json.loads(text)
        pretty = json.dumps(data, indent=2, ensure_ascii=False)
    except Exception:
        pretty = text
    body = (
        f"<!doctype html><html><head><meta charset='utf-8'>"
        f"<title>Crash {html.escape(rel)}</title>"
        f"<style>{DASHBOARD_STYLE}</style></head><body><div class='wrap'>"
        f"<a class='back' href='/crash/'>← All crashes</a>"
        f"<h1>{html.escape(rel)}</h1>"
        f"<pre>{html.escape(pretty)}</pre>"
        f"</div></body></html>"
    )
    return body.encode("utf-8")


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "HushTVCrashSvc/1.0"

    # Silence default access log — systemd will capture stderr anyway.
    def log_message(self, format, *args):  # noqa: A002 (shadowing)
        sys.stderr.write("%s - %s\n" % (self.address_string(), format % args))

    def _check_basic_auth(self) -> bool:
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Basic "):
            return False
        try:
            raw = base64.b64decode(auth[6:]).decode("utf-8", errors="ignore")
            user, _, pw = raw.partition(":")
        except Exception:
            return False
        return user == BASIC_USER and pw == BASIC_PASS

    def _demand_auth(self):
        self.send_response(401)
        self.send_header("WWW-Authenticate", 'Basic realm="HushTV Crash Reports"')
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(b"auth required")

    def _ok(self, body: bytes, ctype: str = "text/html; charset=utf-8"):
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _fail(self, code: int, msg: str = "error"):
        body = msg.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        # nginx strips "/crash" → we see "/" or "/report/..." here.
        parsed = urlparse(self.path)
        path = parsed.path
        qs = parse_qs(parsed.query)
        if path in ("/", "/dashboard", "/index"):
            if not self._check_basic_auth():
                self._demand_auth(); return
            since = (qs.get("since") or ["24h"])[0]
            self._ok(_dashboard_html(summary_since=since)); return
        if path == "/summary":
            # JSON summary endpoint (v1.44.20). Same basic auth as
            # the dashboard so it's safe to expose externally.
            if not self._check_basic_auth():
                self._demand_auth(); return
            since = (qs.get("since") or ["24h"])[0]
            data = _summary(_parse_since(since))
            body = json.dumps(data, indent=2, ensure_ascii=False).encode("utf-8")
            self._ok(body, ctype="application/json; charset=utf-8"); return
        if path.startswith("/report/"):
            if not self._check_basic_auth():
                self._demand_auth(); return
            rel = unquote(path[len("/report/"):])
            body = _report_html(rel)
            if body is None:
                self._fail(404, "not found"); return
            self._ok(body); return
        if path == "/health":
            self._ok(b"ok", "text/plain"); return
        self._fail(404, "not found")

    def do_POST(self):
        path = self.path.split("?", 1)[0]
        # Submit endpoint: /submit/<SECRET>
        if not path.startswith("/submit/"):
            self._fail(404, "not found"); return
        token = path[len("/submit/"):]
        if token != SUBMIT_SECRET:
            self._fail(403, "forbidden"); return
        length = int(self.headers.get("Content-Length", "0") or "0")
        if length <= 0 or length > MAX_BODY:
            self._fail(413, "bad size"); return
        raw = self.rfile.read(length)
        try:
            payload = json.loads(raw.decode("utf-8", errors="replace"))
        except Exception:
            self._fail(400, "bad json"); return

        # Normalise & enrich.
        now = datetime.now(timezone.utc)
        payload.setdefault("sent_at", now.isoformat())
        # Clamp strings to sane lengths so a malicious client can't
        # blow up the dashboard template.
        for k in ("device", "app_version", "captured_at"):
            if k in payload:
                payload[k] = str(payload[k])[:200]
        if "trace" in payload:
            payload["trace"] = str(payload["trace"])[:128 * 1024]

        day = now.strftime("%Y-%m-%d")
        day_dir = DATA_DIR / day
        day_dir.mkdir(parents=True, exist_ok=True)
        stamp = now.strftime("%H%M%S-%f")
        device = str(payload.get("device", "unknown"))[:40].replace("/", "_").replace(" ", "_")
        target = day_dir / f"{stamp}-{device}.json"
        target.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
        self._ok(json.dumps({"ok": True, "stored": f"{day}/{target.name}"}).encode("utf-8"),
                 "application/json")


class ThreadedServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def main():
    srv = ThreadedServer(("127.0.0.1", PORT), Handler)
    sys.stderr.write(f"HushTV crash svc listening on 127.0.0.1:{PORT}, data={DATA_DIR}\n")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
