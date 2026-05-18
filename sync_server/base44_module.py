"""Base44 CMS integration — pushes Canada CDN-fee payment confirmations
to the Base44-side `recordCdnFeePayment` gateway action.

Architecture:
  • `record_payment_sync()` is called inline from `_apply_payment()` in
    canada_payment_module.py immediately after a successful IMAP-match.
    Best-effort & idempotent: a Base44 outage NEVER blocks the in-app
    license grant. On failure the call is queued into the
    `canada_base44_retry` table and re-driven by a background worker.

  • `retry_worker()` is a daemon thread that wakes every 5 minutes,
    re-tries up to MAX_ATTEMPTS, then marks the row "failed" so the
    admin can spot it and force-push manually.

  • Base44 itself dedupes by order_id, so retrying a successful call is
    free.

Config — all read from env at import time (set in systemd unit):
  BASE44_GATEWAY_URL    — full URL of the hushtvapiGateway function
  BASE44_API_KEY        — X-API-Key for that gateway
  BASE44_DRY_RUN        — "1" to log requests instead of sending
"""
from __future__ import annotations

import json
import logging
import os
import sqlite3
import threading
import time
import urllib.error
import urllib.request
from contextlib import contextmanager
from typing import Optional

log = logging.getLogger("base44_sync")

GATEWAY_URL = os.environ.get(
    "BASE44_GATEWAY_URL",
    "https://hushtv.com/api/functions/hushtvapiGateway",
)
API_KEY = os.environ.get(
    "BASE44_API_KEY",
    "htv_FIe0oUPLXQ8PorAoxgWgewYjxcsLal78ls4DR1jx7omxBGSi",
)
DRY_RUN = os.environ.get("BASE44_DRY_RUN", "").strip() in ("1", "true", "yes")

DB_PATH = os.environ.get("HUSHSYNC_DB", "/var/hushtv-sync/sync.sqlite3")

REQUEST_TIMEOUT_S = 15
MAX_ATTEMPTS = 24                  # ~24h at 1-per-hour after the first burst
RETRY_INTERVAL_S = 5 * 60          # 5 min between scheduler wake-ups


@contextmanager
def _conn():
    c = sqlite3.connect(DB_PATH, timeout=20)
    c.row_factory = sqlite3.Row
    try:
        yield c
        c.commit()
    finally:
        c.close()


def _now_ms() -> int:
    return int(time.time() * 1000)


def init_schema() -> None:
    """Idempotent schema bootstrap. Called from canada_payment_module
    at module import so the retry table exists before the IMAP poller
    runs."""
    with _conn() as c:
        c.executescript(
            """
            CREATE TABLE IF NOT EXISTS canada_base44_retry (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id        TEXT NOT NULL,
                xtream_username TEXT NOT NULL,
                payload_json    TEXT NOT NULL,
                first_attempt_at INTEGER NOT NULL,
                last_attempt_at  INTEGER NOT NULL,
                next_attempt_at  INTEGER NOT NULL,
                attempts        INTEGER NOT NULL DEFAULT 0,
                status          TEXT NOT NULL DEFAULT 'pending',
                last_error      TEXT
            );
            CREATE INDEX IF NOT EXISTS canada_base44_retry_next_idx
                ON canada_base44_retry(next_attempt_at)
                WHERE status='pending';
            CREATE UNIQUE INDEX IF NOT EXISTS canada_base44_retry_order_idx
                ON canada_base44_retry(order_id);
            """
        )


# ── HTTP call ───────────────────────────────────────────────────────
def _post_gateway(payload: dict, timeout: int = REQUEST_TIMEOUT_S) -> tuple[int, dict | None, str]:
    """POST to Base44 gateway. Returns (http_code, parsed_json|None, raw_text).
    Never raises — network errors come back as (0, None, message)."""
    if DRY_RUN:
        log.info("BASE44 DRY_RUN payload=%s", json.dumps(payload)[:500])
        return 200, {"success": True, "dry_run": True}, ""
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        GATEWAY_URL, data=body, method="POST",
        headers={
            "Content-Type": "application/json",
            "X-API-Key": API_KEY,
            "Accept": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            text = resp.read().decode("utf-8", errors="ignore")
            try:
                return resp.status, json.loads(text), text
            except json.JSONDecodeError:
                return resp.status, None, text
    except urllib.error.HTTPError as e:
        text = ""
        try:
            text = e.read().decode("utf-8", errors="ignore")
        except Exception:
            pass
        return e.code, _safe_json(text), text or str(e)
    except urllib.error.URLError as e:
        return 0, None, f"URLError: {e.reason}"
    except Exception as e:
        return 0, None, f"{type(e).__name__}: {e}"


def _safe_json(s: str) -> Optional[dict]:
    try:
        return json.loads(s)
    except Exception:
        return None


# ── Audit log helper ────────────────────────────────────────────────
def _log_event(
    c: sqlite3.Connection,
    action: str,
    xtream_username: str,
    detail: str,
) -> None:
    """Write into canada_admin_events so the activity is visible in admin."""
    c.execute(
        "INSERT INTO canada_admin_events (at, action, xtream_username, actor, detail) "
        "VALUES (?, ?, ?, ?, ?)",
        (_now_ms(), action, xtream_username, "system", detail[:500]),
    )


# ── Public entry points ─────────────────────────────────────────────
def record_payment_sync(
    c: sqlite3.Connection,
    *,
    xtream_username: str,
    order_id: str,
    amount_cad: float,
    paid_at_ms: int,
    expires_at_ms: int,
    interac_sender: Optional[str],
) -> dict:
    """Inline call from `_apply_payment()`. Best-effort: on any failure
    (network, 5xx, malformed response) we queue a retry row and let
    the background worker pick it up. Returns a small status dict for
    immediate logging.

    Receives the OPEN SQLite connection from the caller so all writes
    happen in the same transaction as the license grant — no risk of
    partial state."""
    payload = _build_payload(
        xtream_username=xtream_username,
        order_id=order_id,
        amount_cad=amount_cad,
        paid_at_ms=paid_at_ms,
        expires_at_ms=expires_at_ms,
        interac_sender=interac_sender,
    )
    code, parsed, raw = _post_gateway(payload)
    success = code == 200 and isinstance(parsed, dict) and parsed.get("success") is True
    if success:
        dup = parsed.get("duplicate") is True
        user_id = parsed.get("user_id") or "?"
        msg = ("base44 sync OK (DUPLICATE)" if dup else "base44 sync OK") + f" user_id={user_id}"
        _log_event(c, "base44_sync", xtream_username, f"order={order_id} {msg}")
        # If a previous retry row existed, mark it done.
        c.execute(
            "UPDATE canada_base44_retry SET status='succeeded', last_attempt_at=?, "
            "last_error=NULL WHERE order_id=?",
            (_now_ms(), order_id),
        )
        return {"sync": "ok", "user_id": user_id, "duplicate": dup}
    # Failure path. Distinguish transient errors (worth retrying) from
    # permanent ones (Base44 said "user not found" / "bad payload" —
    # retrying won't fix those; the admin needs to act).
    err = (parsed or {}).get("error") if isinstance(parsed, dict) else None
    err = err or f"http={code} {raw[:200]}"
    transient = (code == 0) or (code >= 500) or (code == 429)
    if transient:
        _enqueue_retry(c, order_id, xtream_username, payload, err)
        _log_event(c, "base44_sync", xtream_username,
                   f"order={order_id} TRANSIENT FAIL → queued. err={err[:200]}")
        return {"sync": "queued_for_retry", "error": err, "http": code}
    # Permanent failure — surface in admin but don't retry.
    _log_event(c, "base44_sync", xtream_username,
               f"order={order_id} PERMANENT FAIL (http={code}). err={err[:200]}")
    return {"sync": "permanent_failure", "error": err, "http": code}


def _build_payload(
    *,
    xtream_username: str,
    order_id: str,
    amount_cad: float,
    paid_at_ms: int,
    expires_at_ms: int,
    interac_sender: Optional[str],
) -> dict:
    return {
        "action": "recordCdnFeePayment",
        "xtream_username": xtream_username,
        "order_id": order_id,
        "amount_cad": round(float(amount_cad), 2),
        "paid_at": _iso(paid_at_ms),
        "expires_at": _iso(expires_at_ms),
        **({"interac_sender_name": interac_sender} if interac_sender else {}),
    }


def _iso(ms: int) -> str:
    import datetime as _dt
    return _dt.datetime.utcfromtimestamp(int(ms) / 1000).strftime(
        "%Y-%m-%dT%H:%M:%SZ"
    )


def _enqueue_retry(
    c: sqlite3.Connection,
    order_id: str,
    xtream_username: str,
    payload: dict,
    err: str,
) -> None:
    now = _now_ms()
    c.execute(
        """INSERT INTO canada_base44_retry
            (order_id, xtream_username, payload_json,
             first_attempt_at, last_attempt_at, next_attempt_at,
             attempts, status, last_error)
           VALUES (?, ?, ?, ?, ?, ?, 1, 'pending', ?)
           ON CONFLICT(order_id) DO UPDATE SET
             last_attempt_at = excluded.last_attempt_at,
             next_attempt_at = excluded.next_attempt_at,
             attempts        = canada_base44_retry.attempts + 1,
             status          = 'pending',
             last_error      = excluded.last_error""",
        (order_id, xtream_username, json.dumps(payload),
         now, now, now + RETRY_INTERVAL_S * 1000, err[:500]),
    )


# ── Admin-triggered manual resync ──────────────────────────────────
def resync_one(
    *, xtream_username: str, order_id: str,
    amount_cad: float, paid_at_ms: int, expires_at_ms: int,
    interac_sender: Optional[str], actor: str = "admin",
) -> dict:
    """Force-push a single order to Base44. Used by the admin "Resync"
    button. Opens its own DB connection because the admin route
    handler doesn't run inside an `_apply_payment` transaction."""
    payload = _build_payload(
        xtream_username=xtream_username, order_id=order_id,
        amount_cad=amount_cad, paid_at_ms=paid_at_ms,
        expires_at_ms=expires_at_ms, interac_sender=interac_sender,
    )
    code, parsed, raw = _post_gateway(payload)
    success = code == 200 and isinstance(parsed, dict) and parsed.get("success") is True
    with _conn() as c:
        if success:
            dup = parsed.get("duplicate") is True
            user_id = parsed.get("user_id") or "?"
            c.execute(
                "INSERT INTO canada_admin_events (at, action, xtream_username, actor, detail) "
                "VALUES (?, 'base44_resync', ?, ?, ?)",
                (_now_ms(), xtream_username, actor,
                 f"order={order_id} OK user_id={user_id}{' DUP' if dup else ''}"),
            )
            c.execute(
                "UPDATE canada_base44_retry SET status='succeeded', last_attempt_at=?, "
                "last_error=NULL WHERE order_id=?",
                (_now_ms(), order_id),
            )
            return {"ok": True, "user_id": user_id, "duplicate": dup}
        err = (parsed or {}).get("error") if isinstance(parsed, dict) else None
        err = err or f"http={code} {raw[:200]}"
        c.execute(
            "INSERT INTO canada_admin_events (at, action, xtream_username, actor, detail) "
            "VALUES (?, 'base44_resync', ?, ?, ?)",
            (_now_ms(), xtream_username, actor,
             f"order={order_id} FAILED err={err[:200]}"),
        )
        return {"ok": False, "error": err, "http": code}


# ── Background retry worker ────────────────────────────────────────
_worker_started = False
_worker_lock = threading.Lock()


def start_retry_worker() -> None:
    """Idempotent — safe to call from canada_payment_module's startup."""
    global _worker_started
    with _worker_lock:
        if _worker_started:
            return
        _worker_started = True
    t = threading.Thread(target=_worker_loop, name="base44-retry", daemon=True)
    t.start()
    log.info("base44 retry worker started")


def _worker_loop() -> None:
    while True:
        try:
            _drive_one_pass()
        except Exception as e:
            log.exception("base44 retry pass failed: %s", e)
        time.sleep(RETRY_INTERVAL_S)


def _drive_one_pass() -> None:
    """Find every pending row with next_attempt_at <= now and retry."""
    now = _now_ms()
    with _conn() as c:
        rows = c.execute(
            "SELECT * FROM canada_base44_retry "
            "WHERE status='pending' AND next_attempt_at <= ? "
            "ORDER BY next_attempt_at ASC LIMIT 50",
            (now,),
        ).fetchall()
    for r in rows:
        order_id = r["order_id"]
        attempts = int(r["attempts"]) + 1
        try:
            payload = json.loads(r["payload_json"])
        except Exception:
            payload = None
        if payload is None:
            with _conn() as c:
                c.execute(
                    "UPDATE canada_base44_retry SET status='failed', last_error='unparseable payload' "
                    "WHERE order_id=?",
                    (order_id,),
                )
            continue
        code, parsed, raw = _post_gateway(payload)
        success = code == 200 and isinstance(parsed, dict) and parsed.get("success") is True
        with _conn() as c:
            if success:
                user_id = (parsed or {}).get("user_id") or "?"
                dup = (parsed or {}).get("duplicate") is True
                c.execute(
                    "UPDATE canada_base44_retry "
                    "SET status='succeeded', last_attempt_at=?, attempts=?, last_error=NULL "
                    "WHERE order_id=?",
                    (_now_ms(), attempts, order_id),
                )
                c.execute(
                    "INSERT INTO canada_admin_events (at, action, xtream_username, actor, detail) "
                    "VALUES (?, 'base44_sync', ?, 'retry-worker', ?)",
                    (_now_ms(), r["xtream_username"],
                     f"order={order_id} retry#{attempts} OK user_id={user_id}{' DUP' if dup else ''}"),
                )
            else:
                err = (parsed or {}).get("error") if isinstance(parsed, dict) else None
                err = err or f"http={code} {raw[:200]}"
                # Same transient/permanent split as the inline path:
                # a 4xx response means "Base44 will keep saying no" —
                # don't waste 24h of retries on it.
                transient = (code == 0) or (code >= 500) or (code == 429)
                give_up_now = (not transient) or attempts >= MAX_ATTEMPTS
                if give_up_now:
                    c.execute(
                        "UPDATE canada_base44_retry "
                        "SET status='failed', last_attempt_at=?, attempts=?, last_error=? "
                        "WHERE order_id=?",
                        (_now_ms(), attempts, err[:500], order_id),
                    )
                    reason = (
                        f"PERMANENT err (http={code})" if not transient
                        else f"GAVE UP after {attempts} attempts"
                    )
                    c.execute(
                        "INSERT INTO canada_admin_events (at, action, xtream_username, actor, detail) "
                        "VALUES (?, 'base44_sync', ?, 'retry-worker', ?)",
                        (_now_ms(), r["xtream_username"],
                         f"order={order_id} {reason}. err={err[:200]}"),
                    )
                else:
                    # Exponential-ish backoff: 5m, 10m, 20m, 40m, then capped at 1h.
                    backoff_s = min(RETRY_INTERVAL_S * (2 ** (attempts - 1)), 3600)
                    c.execute(
                        "UPDATE canada_base44_retry "
                        "SET status='pending', last_attempt_at=?, next_attempt_at=?, "
                        "    attempts=?, last_error=? "
                        "WHERE order_id=?",
                        (_now_ms(), _now_ms() + backoff_s * 1000,
                         attempts, err[:500], order_id),
                    )
