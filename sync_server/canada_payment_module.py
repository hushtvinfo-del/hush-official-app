"""HushTV Canada — $40 CAD/year CDN Proxy Fee payment gateway.

Generates 8-digit Order IDs tied to an Xtream username. The user pays
$40 (CAD) via Interac e-Transfer to Hushtv.info@gmail.com with the
Order ID typed into the e-Transfer "Message" field.

A background IMAP poller logs into Hushtv.info@gmail.com every 30 s,
parses Interac auto-deposit emails from notify@payments.interac.ca,
extracts the amount + Message (Order ID) and reconciles against
pending orders. On a match the order flips to `paid` and a 1-year
license is granted to the associated xtream_username.

Storage: re-uses the existing sync SQLite DB at /var/hushtv-sync/sync.sqlite3
in six tables:
  - canada_orders                — order_id PK, status, etc.
  - canada_licenses              — xtream_username PK, expires_at
  - canada_processed_emails      — IMAP idempotency
  - canada_devices               — per-device heartbeats (admin)
  - canada_admin_events          — audit log for admin actions
  - canada_base44_events_inbox   — incoming Base44 webhook idempotency

Routes mounted under /api/canada/* by hushsync_app.py.
"""
from __future__ import annotations

import csv
import email
import hashlib
import hmac
import imaplib
import io
import json
import logging
import os
import random
import re
import smtplib
import sqlite3
import threading
import time
from contextlib import contextmanager
from email.message import EmailMessage, Message
from typing import Optional

from bs4 import BeautifulSoup
from fastapi import APIRouter, Header, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

import base44_module

log = logging.getLogger("canada_payment")

DB_PATH = os.environ.get("HUSHSYNC_DB", "/var/hushtv-sync/sync.sqlite3")
GMAIL_USER = os.environ.get("INTERAC_GMAIL_USER", "Hushtv.info@gmail.com")
GMAIL_PASS = os.environ.get("INTERAC_GMAIL_APP_PASSWORD", "")
ADMIN_TOKEN = os.environ.get("SPORTS_ADMIN_TOKEN", "")

# Business constants
EXPECTED_AMOUNT_CAD = 10.00          # TESTING price — bump back to 40.00 when going live
ORDER_TTL_MS = 60 * 60 * 1000        # 60 minutes for user to pay
LICENSE_YEAR_MS = 365 * 24 * 60 * 60 * 1000
INTERAC_SENDER = "notify@payments.interac.ca"
IMAP_HOST = "imap.gmail.com"
IMAP_PORT = 993
IMAP_POLL_INTERVAL_SEC = 30
IMAP_LOOKBACK_DAYS = 7

# ── DB ──────────────────────────────────────────────────────────────
_db_lock = threading.RLock()


def _connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, timeout=20, isolation_level=None)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.row_factory = sqlite3.Row
    return conn


def _init_schema() -> None:
    with _connect() as c:
        c.executescript(
            """
            CREATE TABLE IF NOT EXISTS canada_orders (
                order_id        TEXT PRIMARY KEY,
                xtream_username TEXT NOT NULL,
                created_at      INTEGER NOT NULL,
                expires_at      INTEGER NOT NULL,
                status          TEXT NOT NULL,
                paid_at         INTEGER,
                interac_amount  TEXT,
                interac_sender  TEXT,
                interac_email_uid TEXT
            );
            CREATE INDEX IF NOT EXISTS canada_orders_user_idx
                ON canada_orders(xtream_username);
            CREATE INDEX IF NOT EXISTS canada_orders_status_idx
                ON canada_orders(status);

            CREATE TABLE IF NOT EXISTS canada_licenses (
                xtream_username TEXT PRIMARY KEY,
                paid_at         INTEGER NOT NULL,
                expires_at      INTEGER NOT NULL,
                last_order_id   TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS canada_processed_emails (
                uid          TEXT PRIMARY KEY,
                processed_at INTEGER NOT NULL,
                order_id     TEXT,
                amount       TEXT,
                sender       TEXT,
                outcome      TEXT
            );

            -- v1.44.82 — per-device heartbeats so admin can see who is
            -- actively using the app and from how many devices.
            CREATE TABLE IF NOT EXISTS canada_devices (
                device_id        TEXT PRIMARY KEY,
                xtream_username  TEXT NOT NULL,
                first_seen_at    INTEGER NOT NULL,
                last_seen_at     INTEGER NOT NULL,
                app_version      TEXT,
                platform         TEXT,
                model            TEXT
            );
            CREATE INDEX IF NOT EXISTS canada_devices_user_idx
                ON canada_devices(xtream_username);
            CREATE INDEX IF NOT EXISTS canada_devices_last_seen_idx
                ON canada_devices(last_seen_at DESC);

            -- v1.44.82 — audit log for admin-initiated actions
            -- (extend, revoke, send reminder). Lets accounting see who
            -- did what without trawling through orders.
            CREATE TABLE IF NOT EXISTS canada_admin_events (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                at               INTEGER NOT NULL,
                action           TEXT NOT NULL,
                xtream_username  TEXT,
                actor            TEXT,
                detail           TEXT
            );
            CREATE INDEX IF NOT EXISTS canada_admin_events_user_idx
                ON canada_admin_events(xtream_username);
            CREATE INDEX IF NOT EXISTS canada_admin_events_at_idx
                ON canada_admin_events(at DESC);

            -- v1.44.85 — Idempotency + audit for inbound Base44 webhooks
            -- (CDN-fee paid/revoked events fired by Base44 when an
            -- admin manually marks payment status on a user record).
            CREATE TABLE IF NOT EXISTS canada_base44_events_inbox (
                event_id        TEXT PRIMARY KEY,
                event_type      TEXT NOT NULL,
                received_at     INTEGER NOT NULL,
                processed_at    INTEGER,
                xtream_username TEXT,
                raw_payload     TEXT NOT NULL,
                status          TEXT NOT NULL DEFAULT 'received',
                applied         TEXT,
                error           TEXT
            );
            CREATE INDEX IF NOT EXISTS canada_base44_inbox_recv_idx
                ON canada_base44_events_inbox(received_at DESC);
            """
        )
        # Add message_id column for stable cross-session idempotency.
        # The original uid-based key was unreliable because Gmail re-uses
        # IMAP UIDs after label/folder operations — we'd skip a brand-new
        # email because its UID happened to match a previously-processed
        # email. Message-ID is RFC-2822-globally-unique and stable forever.
        try:
            c.execute("ALTER TABLE canada_processed_emails ADD COLUMN message_id TEXT")
        except sqlite3.OperationalError:
            pass  # already added
        c.execute(
            "CREATE INDEX IF NOT EXISTS canada_processed_emails_msgid_idx "
            "ON canada_processed_emails(message_id)"
        )


@contextmanager
def _conn():
    with _db_lock:
        c = _connect()
        try:
            yield c
        finally:
            c.close()


def _now_ms() -> int:
    return int(time.time() * 1000)


# ── Order ID generation ─────────────────────────────────────────────
def _new_order_id() -> str:
    """Generate an unused 8-digit numeric Order ID.

    Range 10000000..99999999 — leading zero would confuse users typing
    it into the Interac "Message" field on a mobile keyboard.
    """
    with _conn() as c:
        for _ in range(40):
            oid = str(random.randint(10_000_000, 99_999_999))
            row = c.execute(
                "SELECT 1 FROM canada_orders WHERE order_id=?", (oid,)
            ).fetchone()
            if row is None:
                return oid
    raise RuntimeError("could not allocate a unique 8-digit order id after 40 tries")


# ── Interac HTML parsing ────────────────────────────────────────────
_AMOUNT_RE = re.compile(r"\$\s*([0-9]+(?:\.[0-9]{1,2})?)\s*\(CAD\)", re.I)
_AMOUNT_FALLBACK_RE = re.compile(r"\$\s*([0-9]+(?:\.[0-9]{1,2})?)\s*CAD", re.I)
_ORDER_ID_RE = re.compile(r"\b(\d{8})\b")
_MESSAGE_LABEL_RE = re.compile(r"Message\s*:?\s*(.{0,200})", re.I | re.S)
_SENT_FROM_RE = re.compile(
    r"Sent\s+From\s*:?\s*(.+?)(?=\s+(?:Sent\s+On|Amount|Message|Reference|Notice)\b|$)",
    re.I,
)


def _html_to_text(html: str) -> str:
    try:
        soup = BeautifulSoup(html, "html.parser")
        for br in soup.find_all("br"):
            br.replace_with("\n")
        return soup.get_text(separator="\n")
    except Exception:  # pragma: no cover
        return html


def parse_interac_email(raw_html_or_text: str) -> dict:
    """Extract amount, order id (from Message), and sender from an Interac e-mail.

    Tolerant — works against both HTML and plaintext payloads. Returns a
    dict with keys: amount_cad (float|None), order_id (str|None),
    sender_name (str|None), raw_text (str).
    """
    text = _html_to_text(raw_html_or_text) if "<" in raw_html_or_text else raw_html_or_text
    # collapse whitespace
    flat = re.sub(r"\s+", " ", text).strip()

    amount = None
    m = _AMOUNT_RE.search(flat) or _AMOUNT_FALLBACK_RE.search(flat)
    if m:
        try:
            amount = float(m.group(1))
        except ValueError:
            amount = None

    # Find the Message: field, then extract the first 8-digit token after it.
    order_id = None
    msg_m = _MESSAGE_LABEL_RE.search(flat)
    if msg_m:
        msg_tail = msg_m.group(1)
        oid_m = _ORDER_ID_RE.search(msg_tail)
        if oid_m:
            order_id = oid_m.group(1)
    # NO fallback: if there is no explicit "Message:" label with an 8-digit
    # value, we treat the email as having NO order id. The previous
    # any-8-digit-substring fallback caused legitimate Interac transfers
    # for unrelated business activity (banking reference numbers, dates,
    # bank-account hashes, etc.) to wrongly attach to a real order — which
    # could grant licenses to the wrong xtream_username.

    sender = None
    sm = _SENT_FROM_RE.search(flat)
    if sm:
        sender = sm.group(1).strip().rstrip(",").rstrip(".")

    return {
        "amount_cad": amount,
        "order_id": order_id,
        "sender_name": sender,
        "raw_text": text[:4000],
    }


def _extract_email_body(msg: Message) -> str:
    """Return the best body text for parsing — HTML preferred, fallback plain."""
    html_part = None
    text_part = None
    if msg.is_multipart():
        for part in msg.walk():
            ctype = (part.get_content_type() or "").lower()
            if part.get_content_disposition() == "attachment":
                continue
            try:
                payload = part.get_payload(decode=True)
            except Exception:
                payload = None
            if not payload:
                continue
            charset = part.get_content_charset() or "utf-8"
            try:
                decoded = payload.decode(charset, errors="replace")
            except Exception:
                decoded = payload.decode("utf-8", errors="replace")
            if ctype == "text/html" and html_part is None:
                html_part = decoded
            elif ctype == "text/plain" and text_part is None:
                text_part = decoded
    else:
        try:
            payload = msg.get_payload(decode=True)
            charset = msg.get_content_charset() or "utf-8"
            text_part = payload.decode(charset, errors="replace") if payload else ""
        except Exception:
            text_part = msg.get_payload() or ""
    return html_part or text_part or ""


# ── Order / License DB ops ──────────────────────────────────────────
class CreateOrderReq(BaseModel):
    xtream_username: str = Field(..., min_length=1, max_length=128)
    force_new: bool = False  # when true, allow a renewal even if currently licensed


def _serialize_order(row: sqlite3.Row) -> dict:
    return {
        "order_id": row["order_id"],
        "xtream_username": row["xtream_username"],
        "created_at": row["created_at"],
        "expires_at": row["expires_at"],
        "status": row["status"],
        "paid_at": row["paid_at"],
    }


def _serialize_license(row: Optional[sqlite3.Row]) -> dict:
    if row is None:
        return {"paid": False}
    now = _now_ms()
    expires_at = int(row["expires_at"])
    if expires_at <= now:
        return {
            "paid": False,
            "expired": True,
            "expired_at": expires_at,
            "last_order_id": row["last_order_id"],
        }
    return {
        "paid": True,
        "expires_at": expires_at,
        "paid_at": int(row["paid_at"]),
        "days_remaining": max(0, (expires_at - now) // (24 * 60 * 60 * 1000)),
        "last_order_id": row["last_order_id"],
    }


def _get_license_row(c, xtream_username: str):
    return c.execute(
        "SELECT * FROM canada_licenses WHERE xtream_username=?",
        (xtream_username,),
    ).fetchone()


def _grant_license(c, xtream_username: str, paid_at: int, order_id: str) -> None:
    """Insert or extend a license. If an active license exists, ADD a year
    to its current expiry; otherwise expires = paid_at + 1y."""
    row = _get_license_row(c, xtream_username)
    now = _now_ms()
    if row and int(row["expires_at"]) > now:
        new_expires = int(row["expires_at"]) + LICENSE_YEAR_MS
    else:
        new_expires = paid_at + LICENSE_YEAR_MS
    c.execute(
        """INSERT INTO canada_licenses (xtream_username, paid_at, expires_at, last_order_id)
           VALUES (?, ?, ?, ?)
           ON CONFLICT(xtream_username) DO UPDATE SET
              paid_at=excluded.paid_at,
              expires_at=excluded.expires_at,
              last_order_id=excluded.last_order_id""",
        (xtream_username, paid_at, new_expires, order_id),
    )


# ── IMAP poller ─────────────────────────────────────────────────────
_poller_started = False
_poller_lock = threading.Lock()


def _imap_connect() -> Optional[imaplib.IMAP4_SSL]:
    if not GMAIL_PASS:
        log.warning("INTERAC_GMAIL_APP_PASSWORD not set; IMAP poller idle")
        return None
    try:
        m = imaplib.IMAP4_SSL(IMAP_HOST, IMAP_PORT)
        m.login(GMAIL_USER, GMAIL_PASS)
        return m
    except Exception as e:
        log.exception("IMAP login failed: %s", e)
        return None


def _already_processed(c, uid: str, message_id: Optional[str]) -> bool:
    """Has this email already been processed?

    Primary idempotency key is the RFC-2822 Message-ID header — globally
    unique, stable forever, immune to Gmail's IMAP-UID re-assignment quirks.
    Falls back to the UID for very old rows that pre-date the message_id
    column.
    """
    if message_id:
        row = c.execute(
            "SELECT 1 FROM canada_processed_emails WHERE message_id=?",
            (message_id,),
        ).fetchone()
        if row is not None:
            return True
    # IMPORTANT: do NOT fall back to uid-only matching. Gmail re-uses UIDs
    # after label/folder operations, which means a brand-new email can land
    # at the same UID as one we processed days ago and get incorrectly
    # skipped. Treating "unknown message_id" as "never processed" is the
    # safe default — the downstream order-status check (`already_paid`)
    # prevents double-granting if we do re-process the same payment.
    return False


def _record_processed(c, uid: str, message_id: Optional[str], order_id: Optional[str],
                      amount: Optional[float], sender: Optional[str], outcome: str) -> None:
    c.execute(
        """INSERT OR REPLACE INTO canada_processed_emails
           (uid, message_id, processed_at, order_id, amount, sender, outcome)
           VALUES (?, ?, ?, ?, ?, ?, ?)""",
        (uid, message_id, _now_ms(), order_id,
         f"{amount:.2f}" if amount else None, sender, outcome),
    )


def _process_single_email(c, uid: str, raw_msg_bytes: bytes) -> str:
    """Parse one email, reconcile against orders. Returns an outcome tag."""
    msg = email.message_from_bytes(raw_msg_bytes)
    from_addr = (msg.get("From") or "").lower()
    message_id = (msg.get("Message-ID") or msg.get("Message-Id") or "").strip().strip("<>")
    if INTERAC_SENDER not in from_addr:
        return "skipped_not_interac"
    body = _extract_email_body(msg)
    parsed = parse_interac_email(body)
    amount = parsed["amount_cad"]
    order_id = parsed["order_id"]
    sender_name = parsed["sender_name"]

    if amount is None or amount < EXPECTED_AMOUNT_CAD - 0.01:
        log.info("Interac email uid=%s amount=%s < expected — skipping", uid, amount)
        _record_processed(c, uid, message_id, order_id, amount, sender_name, "amount_too_low")
        return "amount_too_low"

    if not order_id:
        log.info("Interac email uid=%s has no parseable order id", uid)
        _record_processed(c, uid, message_id, order_id, amount, sender_name, "no_order_id")
        return "no_order_id"

    row = c.execute(
        "SELECT * FROM canada_orders WHERE order_id=?", (order_id,)
    ).fetchone()
    if row is None:
        log.info("Interac email uid=%s references unknown order_id=%s", uid, order_id)
        _record_processed(c, uid, message_id, order_id, amount, sender_name, "unknown_order")
        return "unknown_order"

    if row["status"] == "paid":
        _record_processed(c, uid, message_id, order_id, amount, sender_name, "already_paid")
        return "already_paid"

    # Reconcile.
    now = _now_ms()
    c.execute(
        """UPDATE canada_orders SET status='paid', paid_at=?, interac_amount=?,
           interac_sender=?, interac_email_uid=? WHERE order_id=?""",
        (now, f"{amount:.2f}", sender_name, uid, order_id),
    )
    _grant_license(c, row["xtream_username"], now, order_id)
    _record_processed(c, uid, message_id, order_id, amount, sender_name, "paid")
    log.info("Interac payment matched: order_id=%s user=%s amount=%.2f",
             order_id, row["xtream_username"], amount)

    # v1.44.84 — Push the payment confirmation to Base44 so the user's
    # CDN-fee fields update and the branded confirmation email goes out.
    # Best-effort: failures are queued in canada_base44_retry by the
    # module — the in-app license grant above already succeeded
    # regardless. Re-fetch the license row to get the freshly-computed
    # expires_at (may be the original paid_at+1y, or +2y if this was
    # an early renewal).
    try:
        lic = _get_license_row(c, row["xtream_username"])
        expires_at_ms = int(lic["expires_at"]) if lic else now + LICENSE_YEAR_MS
        base44_module.record_payment_sync(
            c,
            xtream_username=row["xtream_username"],
            order_id=order_id,
            amount_cad=amount,
            paid_at_ms=now,
            expires_at_ms=expires_at_ms,
            interac_sender=_clean_sender(sender_name),
        )
    except Exception as e:
        log.exception("base44 push failed for order=%s: %s", order_id, e)
    return "paid"


def _imap_scan_once() -> dict:
    """Single IMAP scan pass. Returns a small summary dict for diagnostics."""
    summary = {"checked": 0, "matched": 0, "errors": 0}
    m = _imap_connect()
    if m is None:
        summary["errors"] = 1
        return summary
    try:
        m.select("INBOX")
        # Search last N days from the Interac sender.
        since = time.strftime("%d-%b-%Y", time.gmtime(time.time() - IMAP_LOOKBACK_DAYS * 86400))
        typ, data = m.search(None, '(SINCE "%s" FROM "%s")' % (since, INTERAC_SENDER))
        if typ != "OK" or not data or not data[0]:
            return summary
        uids = data[0].split()
        with _conn() as c:
            for uid_bytes in uids:
                uid = uid_bytes.decode("ascii", errors="ignore")
                summary["checked"] += 1
                try:
                    typ2, msg_data = m.fetch(uid_bytes, "(RFC822)")
                    if typ2 != "OK" or not msg_data or not msg_data[0]:
                        summary["errors"] += 1
                        continue
                    raw = msg_data[0][1]
                    # Cheap idempotency probe BEFORE we parse the body:
                    # extract just the Message-ID header so we can skip
                    # already-processed emails without re-running the
                    # regex/HTML pipeline. UID is no longer trusted (Gmail
                    # re-uses UIDs after folder/label ops).
                    msg_for_id = email.message_from_bytes(raw)
                    mid = (msg_for_id.get("Message-ID")
                           or msg_for_id.get("Message-Id") or "").strip().strip("<>")
                    if _already_processed(c, uid, mid):
                        continue
                    outcome = _process_single_email(c, uid, raw)
                    if outcome == "paid":
                        summary["matched"] += 1
                except Exception as e:
                    log.exception("error processing uid %s: %s", uid, e)
                    summary["errors"] += 1
    finally:
        try:
            m.close()
        except Exception:
            pass
        try:
            m.logout()
        except Exception:
            pass
    return summary


def _poller_loop() -> None:
    log.info("Canada Interac poller started (interval=%ss)", IMAP_POLL_INTERVAL_SEC)
    while True:
        try:
            s = _imap_scan_once()
            if s["matched"] or s["errors"]:
                log.info("interac scan: %s", s)
        except Exception as e:  # pragma: no cover
            log.exception("poller iteration failed: %s", e)
        time.sleep(IMAP_POLL_INTERVAL_SEC)


def start_poller() -> None:
    global _poller_started
    with _poller_lock:
        if _poller_started:
            return
        _poller_started = True
    t = threading.Thread(target=_poller_loop, name="canada-interac-poller", daemon=True)
    t.start()


def _expire_stale_orders(c) -> None:
    now = _now_ms()
    c.execute(
        "UPDATE canada_orders SET status='expired' WHERE status='pending' AND expires_at<?",
        (now,),
    )


# ── FastAPI router ──────────────────────────────────────────────────
router = APIRouter(prefix="/api/canada", tags=["canada"])


@router.get("/health")
def health() -> dict:
    return {
        "ok": True,
        "gmail_user": GMAIL_USER,
        "gmail_pass_configured": bool(GMAIL_PASS),
        "expected_amount_cad": EXPECTED_AMOUNT_CAD,
        "poll_interval_sec": IMAP_POLL_INTERVAL_SEC,
    }


@router.post("/order/create")
def create_order(req: CreateOrderReq) -> dict:
    """Create a fresh pending order tied to the given Xtream username.

    If the user already has an active (unexpired pending) order, returns it
    instead of allocating a second one — prevents the user from spamming
    Order IDs by tapping the lock screen repeatedly.
    """
    user = req.xtream_username.strip().lower()
    if not user:
        raise HTTPException(400, "xtream_username required")
    now = _now_ms()
    with _conn() as c:
        _expire_stale_orders(c)
        # Existing license? Short-circuit unless caller is explicitly
        # asking to create a renewal order on top of an active license.
        if not req.force_new:
            lic = _get_license_row(c, user)
            if lic and int(lic["expires_at"]) > now:
                return {
                    "already_licensed": True,
                    "license": _serialize_license(lic),
                }
        existing = c.execute(
            """SELECT * FROM canada_orders WHERE xtream_username=? AND status='pending'
               AND expires_at>? ORDER BY created_at DESC LIMIT 1""",
            (user, now),
        ).fetchone()
        if existing:
            return {
                "order": _serialize_order(existing),
                "amount_cad": EXPECTED_AMOUNT_CAD,
                "email_to": GMAIL_USER,
                "reused": True,
            }
        oid = _new_order_id()
        c.execute(
            """INSERT INTO canada_orders (order_id, xtream_username, created_at,
               expires_at, status) VALUES (?, ?, ?, ?, 'pending')""",
            (oid, user, now, now + ORDER_TTL_MS),
        )
        row = c.execute("SELECT * FROM canada_orders WHERE order_id=?", (oid,)).fetchone()
        return {
            "order": _serialize_order(row),
            "amount_cad": EXPECTED_AMOUNT_CAD,
            "email_to": GMAIL_USER,
            "reused": False,
        }


@router.get("/order/status/{order_id}")
def order_status(order_id: str) -> dict:
    with _conn() as c:
        _expire_stale_orders(c)
        row = c.execute(
            "SELECT * FROM canada_orders WHERE order_id=?", (order_id,)
        ).fetchone()
        if row is None:
            raise HTTPException(404, "order not found")
        out = {"order": _serialize_order(row)}
        if row["status"] == "paid":
            lic = _get_license_row(c, row["xtream_username"])
            out["license"] = _serialize_license(lic)
        return out


@router.get("/license/{xtream_username}")
def license_status(xtream_username: str) -> dict:
    user = xtream_username.strip().lower()
    with _conn() as c:
        row = _get_license_row(c, user)
        return {"xtream_username": user, "license": _serialize_license(row)}


# ── Public heartbeat ────────────────────────────────────────────────
class HeartbeatReq(BaseModel):
    xtream_username: str = Field(..., min_length=1, max_length=128)
    device_id: str = Field(..., min_length=1, max_length=128)
    app_version: Optional[str] = Field(None, max_length=64)
    platform: Optional[str] = Field(None, max_length=32)  # "android-tv" | "android-mobile"
    model: Optional[str] = Field(None, max_length=128)


@router.post("/heartbeat")
def heartbeat(req: HeartbeatReq) -> dict:
    """Called by the Android client every ~5 min while in foreground.
    Records `last_seen_at` per device so the admin can see active
    devices/users. Cheap upsert — no IO beyond the SQLite write.
    """
    user = req.xtream_username.strip().lower()
    if not user or not req.device_id:
        raise HTTPException(400, "xtream_username and device_id required")
    now = _now_ms()
    with _conn() as c:
        c.execute(
            """INSERT INTO canada_devices
               (device_id, xtream_username, first_seen_at, last_seen_at,
                app_version, platform, model)
               VALUES (?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(device_id) DO UPDATE SET
                 xtream_username = excluded.xtream_username,
                 last_seen_at    = excluded.last_seen_at,
                 app_version     = COALESCE(excluded.app_version, canada_devices.app_version),
                 platform        = COALESCE(excluded.platform, canada_devices.platform),
                 model           = COALESCE(excluded.model, canada_devices.model)""",
            (req.device_id, user, now, now, req.app_version, req.platform, req.model),
        )
    return {"ok": True, "at": now}


# ── Inbound Base44 webhook (CDN-fee manual events) ─────────────────
#
# Base44 hits this endpoint when an admin clicks "mark CDN fee paid" /
# "revoke" inside the Base44 CMS (cash payments, manual fixes, etc).
# We mirror the action into our SQLite license table so the user is
# unlocked in the Android app within seconds.
#
# Security:
#   • HMAC-SHA256 of the raw body using the shared CDN_WEBHOOK_SECRET
#     env var. Constant-time compared.
#   • Timestamp window — reject events older than ±5 min (replay).
#   • Idempotency — every event_id is recorded; duplicates return
#     the previously-cached response.
#   • Defence-in-depth — the endpoint lives at /api/base44/* which is
#     publicly reachable but otherwise locked-down behind the HMAC.

WEBHOOK_SECRET = os.environ.get("CDN_WEBHOOK_SECRET", "")
WEBHOOK_TS_TOLERANCE_S = int(
    os.environ.get("BASE44_WEBHOOK_TIMESTAMP_TOLERANCE_S", "300")
)

# Separate router so the path stays clean (`/api/base44/webhook`)
# instead of e.g. `/api/canada/base44/webhook`.
webhook_router = APIRouter(prefix="/api/base44", tags=["base44-webhook"])


def _verify_hmac(raw_body: bytes, signature_header: str) -> bool:
    """Constant-time-compare the X-Base44-Signature header against
    `sha256=<hex>` of HMAC-SHA256(secret, raw_body). Returns True on
    match. False if header missing/malformed or secret unset."""
    if not WEBHOOK_SECRET or not signature_header:
        return False
    expected = hmac.new(
        WEBHOOK_SECRET.encode("utf-8"), raw_body, hashlib.sha256,
    ).hexdigest()
    # Accept "sha256=<hex>" or bare "<hex>" formats — be permissive on
    # input, strict on comparison.
    provided = signature_header.strip()
    if provided.lower().startswith("sha256="):
        provided = provided[7:]
    return hmac.compare_digest(expected, provided.lower().strip())


def _revoke_license_for(c: sqlite3.Connection, xtream_username: str) -> int:
    """Delete the license row. Returns rows affected."""
    cur = c.execute(
        "DELETE FROM canada_licenses WHERE xtream_username=?",
        (xtream_username,),
    )
    return cur.rowcount


def _inbox_cached(c: sqlite3.Connection, event_id: str) -> Optional[dict]:
    """Return the previously-stored response payload for this event_id
    if we've seen it before, else None."""
    row = c.execute(
        "SELECT status, applied, error, xtream_username "
        "FROM canada_base44_events_inbox WHERE event_id=?",
        (event_id,),
    ).fetchone()
    if row is None:
        return None
    return {
        "ok": row["status"] in ("applied", "ignored"),
        "duplicate": True,
        "event_id": event_id,
        "applied": row["applied"],
        "xtream_username": row["xtream_username"],
        "error": row["error"],
    }


def _inbox_persist(
    c: sqlite3.Connection, event_id: str, event_type: str,
    raw_body: bytes, xtream_username: Optional[str],
    status: str, applied: Optional[str] = None, error: Optional[str] = None,
) -> None:
    now = _now_ms()
    c.execute(
        """INSERT INTO canada_base44_events_inbox
            (event_id, event_type, received_at, processed_at,
             xtream_username, raw_payload, status, applied, error)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(event_id) DO UPDATE SET
             processed_at = excluded.processed_at,
             status       = excluded.status,
             applied      = excluded.applied,
             error        = excluded.error""",
        (
            event_id, event_type, now, now, xtream_username,
            raw_body.decode("utf-8", errors="replace")[:8000],
            status, applied, error,
        ),
    )


def _parse_webhook_timestamp(value: str) -> Optional[int]:
    """Parse the webhook timestamp into UTC unix seconds. Accepts:
      • Unix seconds (string of digits) — e.g. "1779200000"
      • Unix milliseconds — e.g. "1779200000000"
      • ISO 8601 UTC — e.g. "2026-02-08T20:00:00Z" or "2026-02-08T20:00:00.123Z"
    Base44 sends ISO 8601; older test tooling sends unix. Be permissive.
    Returns None if unparseable."""
    if not value:
        return None
    v = value.strip()
    # Numeric?
    if v.lstrip("-").isdigit():
        n = int(v)
        # Heuristic: >= 13 digits → milliseconds; coerce to seconds.
        return n // 1000 if n > 10**12 else n
    # ISO 8601 — Python 3.11+ handles trailing "Z" natively, but older
    # versions need it swapped to "+00:00" for fromisoformat.
    import datetime as _dt
    try:
        iso = v.rstrip()
        if iso.endswith("Z"):
            iso = iso[:-1] + "+00:00"
        return int(_dt.datetime.fromisoformat(iso).timestamp())
    except (ValueError, TypeError):
        return None


@webhook_router.post("/webhook")
async def base44_webhook(
    request: Request,
    x_base44_signature: Optional[str] = Header(None),
    x_base44_timestamp: Optional[str] = Header(None),
) -> dict:
    """Inbound Base44 webhook. See module docstring for protocol."""
    raw = await request.body()

    # 1. Signature verification — bail before any work if it fails.
    if not _verify_hmac(raw, x_base44_signature or ""):
        log.warning("base44 webhook: bad signature (sig=%s)", (x_base44_signature or "")[:16])
        raise HTTPException(401, "invalid signature")

    # 2. Parse body up-front so we can fall back to occurred_at for the
    #    replay-protection timestamp if Base44 omits the header.
    try:
        body = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as e:
        raise HTTPException(400, f"bad JSON: {e}")

    # 3. Timestamp window — accept either the X-Base44-Timestamp header
    #    OR the body's `occurred_at`. Both unix-seconds and ISO 8601
    #    are supported. At least one must be present for replay
    #    protection to do anything.
    ts_str = x_base44_timestamp or body.get("occurred_at") or ""
    ts = _parse_webhook_timestamp(str(ts_str))
    if ts is None:
        raise HTTPException(400, "missing or unparseable timestamp "
                                  "(need X-Base44-Timestamp header or occurred_at body field)")
    drift = abs(int(time.time()) - ts)
    if drift > WEBHOOK_TS_TOLERANCE_S:
        raise HTTPException(401, f"timestamp drift {drift}s exceeds tolerance")

    event_id = (body.get("event_id") or "").strip()
    event_type = (body.get("event_type") or "").strip()
    user = (body.get("xtream_username") or "").strip().lower()
    if not event_id:
        raise HTTPException(400, "event_id required")
    if not event_type:
        raise HTTPException(400, "event_type required")

    with _conn() as c:
        # 4. Idempotency — return cached response if we've seen this event.
        cached = _inbox_cached(c, event_id)
        if cached is not None:
            log.info("base44 webhook DUPLICATE event_id=%s", event_id)
            return cached

        # 5. Dispatch by event_type.
        try:
            if event_type == "cdn_fee_granted_manually":
                if not user:
                    raise ValueError("xtream_username required for grant")
                months = int(body.get("months") or 12)
                if months < 1 or months > 120:
                    raise ValueError(f"months out of range: {months}")
                # Re-use the same +N-months math as the manual admin
                # extend endpoint, so cash payments are treated identically
                # to Interac ones.
                now = _now_ms()
                row = _get_license_row(c, user)
                base = max(int(row["expires_at"]) if row else 0, now)
                new_expires = base + months * 30 * 24 * 60 * 60 * 1000
                c.execute(
                    """INSERT INTO canada_licenses
                        (xtream_username, paid_at, expires_at, last_order_id)
                       VALUES (?, ?, ?, ?)
                       ON CONFLICT(xtream_username) DO UPDATE SET
                         paid_at=excluded.paid_at,
                         expires_at=excluded.expires_at,
                         last_order_id=excluded.last_order_id""",
                    (user, now, new_expires, f"BASE44_MANUAL_{event_id[:32]}"),
                )
                actor = (body.get("actor") or "base44").strip()[:80]
                reason = (body.get("reason") or "").strip()[:160]
                method = (body.get("payment_method") or "").strip()[:32]
                amount = body.get("amount_cad")
                detail = (
                    f"+{months}m via base44 actor={actor} "
                    f"method={method} amount={amount} reason={reason}"
                )
                c.execute(
                    "INSERT INTO canada_admin_events "
                    "(at, action, xtream_username, actor, detail) "
                    "VALUES (?, 'base44_inbound_grant', ?, ?, ?)",
                    (now, user, actor, detail[:500]),
                )
                applied = f"license_granted_{months}m"
                resp = {
                    "ok": True,
                    "event_id": event_id,
                    "applied": applied,
                    "xtream_username": user,
                    "new_expires_at_ms": new_expires,
                }
                _inbox_persist(c, event_id, event_type, raw, user, "applied", applied)
                return resp

            elif event_type == "cdn_fee_revoked":
                if not user:
                    raise ValueError("xtream_username required for revoke")
                n = _revoke_license_for(c, user)
                actor = (body.get("actor") or "base44").strip()[:80]
                reason = (body.get("reason") or "").strip()[:160]
                c.execute(
                    "INSERT INTO canada_admin_events "
                    "(at, action, xtream_username, actor, detail) "
                    "VALUES (?, 'base44_inbound_revoke', ?, ?, ?)",
                    (_now_ms(), user, actor,
                     f"affected_rows={n} reason={reason}"[:500]),
                )
                applied = f"license_revoked (rows={n})"
                _inbox_persist(c, event_id, event_type, raw, user, "applied", applied)
                return {"ok": True, "event_id": event_id, "applied": applied,
                        "xtream_username": user}

            elif event_type == "xtream_username_changed":
                old = (body.get("old_xtream_username") or "").strip().lower()
                if not old or not user:
                    raise ValueError(
                        "both old_xtream_username and xtream_username required",
                    )
                cur = c.execute(
                    "UPDATE canada_licenses SET xtream_username=? WHERE xtream_username=?",
                    (user, old),
                )
                # Also rename device + audit rows for continuity.
                c.execute(
                    "UPDATE canada_devices SET xtream_username=? WHERE xtream_username=?",
                    (user, old),
                )
                applied = f"renamed {old}→{user} (rows={cur.rowcount})"
                c.execute(
                    "INSERT INTO canada_admin_events "
                    "(at, action, xtream_username, actor, detail) "
                    "VALUES (?, 'base44_inbound_rename', ?, ?, ?)",
                    (_now_ms(), user, (body.get("actor") or "base44")[:80],
                     applied[:500]),
                )
                _inbox_persist(c, event_id, event_type, raw, user, "applied", applied)
                return {"ok": True, "event_id": event_id, "applied": applied}

            else:
                # Forward-compatible: store unknown events but don't
                # error — Base44 may add new event_types in the future.
                _inbox_persist(c, event_id, event_type, raw, user, "ignored",
                               f"unknown event_type: {event_type}")
                log.warning("base44 webhook: unknown event_type=%s", event_type)
                return {"ok": True, "event_id": event_id, "ignored": True,
                        "reason": f"unknown event_type: {event_type}"}

        except (ValueError, HTTPException) as e:
            err = str(e.detail if isinstance(e, HTTPException) else e)
            _inbox_persist(c, event_id, event_type, raw, user, "failed",
                           error=err)
            raise HTTPException(400, err)
        except Exception as e:
            log.exception("base44 webhook failed: %s", e)
            _inbox_persist(c, event_id, event_type, raw, user, "failed",
                           error=f"{type(e).__name__}: {e}"[:500])
            raise HTTPException(500, f"internal error: {e}")


# ── Admin endpoints (gated by X-Admin-Token) ────────────────────────
admin_router = APIRouter(prefix="/api/admin/canada", tags=["canada-admin"])


def _check_admin(token: Optional[str]) -> None:
    if not ADMIN_TOKEN:
        raise HTTPException(503, "admin token not configured on server")
    if token != ADMIN_TOKEN:
        raise HTTPException(401, "invalid admin token")


class AdminGrantReq(BaseModel):
    xtream_username: str
    months: int = Field(12, ge=1, le=120)


@admin_router.post("/grant")
def admin_grant(req: AdminGrantReq, x_admin_token: Optional[str] = Header(None)) -> dict:
    _check_admin(x_admin_token)
    user = req.xtream_username.strip().lower()
    now = _now_ms()
    ms_to_add = int(req.months * 30.4375 * 24 * 60 * 60 * 1000)
    with _conn() as c:
        row = _get_license_row(c, user)
        base = max(int(row["expires_at"]) if row else 0, now)
        new_expires = base + ms_to_add
        c.execute(
            """INSERT INTO canada_licenses (xtream_username, paid_at, expires_at, last_order_id)
               VALUES (?, ?, ?, ?)
               ON CONFLICT(xtream_username) DO UPDATE SET
                  paid_at=excluded.paid_at,
                  expires_at=excluded.expires_at,
                  last_order_id=excluded.last_order_id""",
            (user, now, new_expires, "ADMIN_GRANT"),
        )
        # v1.44.87 — manual admin grants also push to Base44 so the
        # user record + confirmation email flow is identical to Interac
        # payments. The Order ID we send is unique per grant (using the
        # now-timestamp) so Base44's dedupe doesn't squash repeat manual
        # grants for renewals.
        try:
            base44_module.record_payment_sync(
                c,
                xtream_username=user,
                order_id=f"ADMIN_GRANT_{now}",
                amount_cad=0.0,  # manual grant — no Interac amount
                paid_at_ms=now,
                expires_at_ms=new_expires,
                interac_sender=f"admin grant ({req.months}m)",
            )
        except Exception as e:
            log.exception("base44 push failed for admin_grant user=%s: %s", user, e)
        row2 = _get_license_row(c, user)
        return {"granted": True, "license": _serialize_license(row2)}


class AdminRevokeReq(BaseModel):
    xtream_username: str


@admin_router.post("/revoke")
def admin_revoke(req: AdminRevokeReq, x_admin_token: Optional[str] = Header(None)) -> dict:
    _check_admin(x_admin_token)
    user = req.xtream_username.strip().lower()
    with _conn() as c:
        c.execute("DELETE FROM canada_licenses WHERE xtream_username=?", (user,))
        return {"revoked": True, "xtream_username": user}


@admin_router.get("/orders")
def admin_orders(x_admin_token: Optional[str] = Header(None), limit: int = 50) -> dict:
    _check_admin(x_admin_token)
    limit = max(1, min(500, limit))
    with _conn() as c:
        rows = c.execute(
            "SELECT * FROM canada_orders ORDER BY created_at DESC LIMIT ?", (limit,)
        ).fetchall()
        return {"orders": [_serialize_order(r) for r in rows]}


@admin_router.get("/licenses")
def admin_licenses(
    x_admin_token: Optional[str] = Header(None),
    limit: int = 1000,
    status: Optional[str] = None,
    q: Optional[str] = None,
) -> dict:
    """Returns enriched license rows joined with device + payment data.

    Filters:
      status = "active" | "expiring_30d" | "expired" | None (all)
      q      = username substring filter
    """
    _check_admin(x_admin_token)
    limit = max(1, min(2000, limit))
    now = _now_ms()
    thirty_d = 30 * 24 * 60 * 60 * 1000
    with _conn() as c:
        rows = c.execute(
            """SELECT l.*,
                  (SELECT COUNT(*) FROM canada_devices d
                     WHERE d.xtream_username = l.xtream_username) AS device_count,
                  (SELECT MAX(last_seen_at) FROM canada_devices d
                     WHERE d.xtream_username = l.xtream_username) AS last_seen_at,
                  (SELECT COUNT(*) FROM canada_orders o
                     WHERE o.xtream_username = l.xtream_username
                       AND o.status='paid') AS payment_count,
                  (SELECT COALESCE(SUM(CAST(o.interac_amount AS REAL)), 0)
                     FROM canada_orders o
                     WHERE o.xtream_username = l.xtream_username
                       AND o.status='paid') AS total_paid_cad
               FROM canada_licenses l
               ORDER BY l.expires_at DESC
               LIMIT ?""",
            (limit,),
        ).fetchall()
        out = []
        for r in rows:
            exp = int(r["expires_at"])
            if exp <= now:
                row_status = "expired"
            elif exp - now <= thirty_d:
                row_status = "expiring_30d"
            else:
                row_status = "active"
            if status and status != row_status:
                continue
            if q and q.lower() not in r["xtream_username"].lower():
                continue
            out.append({
                "xtream_username": r["xtream_username"],
                "paid_at": int(r["paid_at"]),
                "expires_at": exp,
                "last_order_id": r["last_order_id"],
                "device_count": int(r["device_count"] or 0),
                "last_seen_at": int(r["last_seen_at"]) if r["last_seen_at"] else None,
                "payment_count": int(r["payment_count"] or 0),
                "total_paid_cad": round(float(r["total_paid_cad"] or 0), 2),
                "status": row_status,
                "days_remaining": max(0, (exp - now) // (24 * 60 * 60 * 1000)),
            })
        return {"licenses": out, "total": len(out)}


@admin_router.get("/license/{username}/devices")
def admin_license_devices(
    username: str, x_admin_token: Optional[str] = Header(None)
) -> dict:
    """Per-user device list for the license detail drawer."""
    _check_admin(x_admin_token)
    user = username.strip().lower()
    with _conn() as c:
        rows = c.execute(
            """SELECT device_id, first_seen_at, last_seen_at, app_version,
                      platform, model
               FROM canada_devices
               WHERE xtream_username=?
               ORDER BY last_seen_at DESC""",
            (user,),
        ).fetchall()
        return {
            "xtream_username": user,
            "devices": [
                {
                    "device_id": r["device_id"],
                    "first_seen_at": int(r["first_seen_at"]),
                    "last_seen_at": int(r["last_seen_at"]),
                    "app_version": r["app_version"],
                    "platform": r["platform"],
                    "model": r["model"],
                }
                for r in rows
            ],
        }


# ── Payments ledger + CSV export ───────────────────────────────────
def _clean_sender(s) -> str:
    """Strip email-body cruft from the captured sender field. The Interac
    HTML parser sometimes grabs whitespace runs from the Gmail body
    instead of the bare name — this normalises to just the person's
    name. Defensive; safe to call on already-clean values."""
    if not s:
        return ""
    t = str(s)
    m = re.match(r"^(.+?)\s+have\s+been", t, re.I)
    if m:
        return m.group(1).strip().rstrip(",.")
    # Fall back: cut at the first double-space (Gmail boilerplate often
    # starts with several non-breaking spaces).
    return t.split("  ", 1)[0].strip().rstrip(",.")[:80]


def _paid_orders_in_range(c, from_ms: Optional[int], to_ms: Optional[int]):
    where = ["status='paid'"]
    params: list = []
    if from_ms is not None:
        where.append("paid_at >= ?")
        params.append(from_ms)
    if to_ms is not None:
        where.append("paid_at < ?")
        params.append(to_ms)
    sql = (
        "SELECT * FROM canada_orders WHERE " + " AND ".join(where) +
        " ORDER BY paid_at DESC"
    )
    return c.execute(sql, params).fetchall()


@admin_router.get("/payments")
def admin_payments(
    x_admin_token: Optional[str] = Header(None),
    from_ms: Optional[int] = None,
    to_ms: Optional[int] = None,
) -> dict:
    """JSON list of paid orders in [from_ms, to_ms). Both sides optional."""
    _check_admin(x_admin_token)
    with _conn() as c:
        rows = _paid_orders_in_range(c, from_ms, to_ms)
        total_cad = sum(
            float(r["interac_amount"] or 0) for r in rows if r["interac_amount"]
        )
        return {
            "from_ms": from_ms,
            "to_ms": to_ms,
            "count": len(rows),
            "total_cad": round(total_cad, 2),
            "payments": [
                {
                    "order_id": r["order_id"],
                    "xtream_username": r["xtream_username"],
                    "paid_at": int(r["paid_at"]) if r["paid_at"] else None,
                    "amount_cad": float(r["interac_amount"]) if r["interac_amount"] else None,
                    "interac_sender": _clean_sender(r["interac_sender"]),
                    "interac_email_uid": r["interac_email_uid"],
                    "created_at": int(r["created_at"]),
                    "status": r["status"],
                }
                for r in rows
            ],
        }


@admin_router.get("/payments.csv")
def admin_payments_csv(
    x_admin_token: Optional[str] = Header(None),
    from_ms: Optional[int] = None,
    to_ms: Optional[int] = None,
):
    """CSV export for the accountant. Columns:
       Date, Order ID, Username, Amount (CAD), Interac Sender,
       Payment Status, Reference (email UID)
    """
    _check_admin(x_admin_token)
    with _conn() as c:
        rows = _paid_orders_in_range(c, from_ms, to_ms)
    buf = io.StringIO()
    w = csv.writer(buf)
    w.writerow([
        "Date (UTC ISO)", "Order ID", "Username", "Amount (CAD)",
        "Interac Sender", "Payment Status", "Reference (Email UID)",
    ])
    import datetime as _dt
    for r in rows:
        ts = r["paid_at"] or r["created_at"]
        iso = _dt.datetime.utcfromtimestamp(int(ts) / 1000).isoformat() if ts else ""
        w.writerow([
            iso,
            r["order_id"],
            r["xtream_username"],
            r["interac_amount"] or "",
            _clean_sender(r["interac_sender"]),
            r["status"],
            r["interac_email_uid"] or "",
        ])
    buf.seek(0)
    return StreamingResponse(
        iter([buf.getvalue()]),
        media_type="text/csv",
        headers={"Content-Disposition": 'attachment; filename="canada_payments.csv"'},
    )


# ── Dashboard stats ─────────────────────────────────────────────────
@admin_router.get("/stats")
def admin_stats(x_admin_token: Optional[str] = Header(None)) -> dict:
    """Pre-aggregated dashboard numbers — single call powers the revenue UI."""
    _check_admin(x_admin_token)
    import datetime as _dt
    now = _now_ms()
    today = _dt.datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)
    start_today = int(today.timestamp() * 1000)
    start_week = int((today - _dt.timedelta(days=today.weekday())).timestamp() * 1000)
    start_month = int(today.replace(day=1).timestamp() * 1000)
    start_year = int(today.replace(month=1, day=1).timestamp() * 1000)
    # 12-month bar chart buckets
    months_back: list[tuple[int, int, str]] = []
    cursor = today.replace(day=1)
    for _ in range(12):
        prev = cursor
        # back one month
        year = prev.year if prev.month > 1 else prev.year - 1
        month = prev.month - 1 if prev.month > 1 else 12
        cursor = prev.replace(year=year, month=month, day=1)
    cursor = today.replace(day=1)
    # rebuild forward 12 entries (oldest first)
    starts = []
    pt = today.replace(day=1)
    for _ in range(12):
        starts.append(pt)
        year = pt.year if pt.month > 1 else pt.year - 1
        month = pt.month - 1 if pt.month > 1 else 12
        pt = pt.replace(year=year, month=month, day=1)
    starts.reverse()
    months_back = []
    for i, s in enumerate(starts):
        if i + 1 < len(starts):
            end = starts[i + 1]
        else:
            # last month ends at "now + 1 second" so we include current month
            end = today.replace(year=today.year + (1 if today.month == 12 else 0),
                                month=1 if today.month == 12 else today.month + 1, day=1)
        months_back.append((int(s.timestamp() * 1000),
                            int(end.timestamp() * 1000),
                            s.strftime("%Y-%m")))

    with _conn() as c:
        def sum_for(start_ms: int, end_ms: Optional[int] = None) -> tuple[int, float]:
            sql = "SELECT COUNT(*) c, COALESCE(SUM(CAST(interac_amount AS REAL)),0) s FROM canada_orders WHERE status='paid' AND paid_at >= ?"
            params: list = [start_ms]
            if end_ms is not None:
                sql += " AND paid_at < ?"
                params.append(end_ms)
            r = c.execute(sql, params).fetchone()
            return int(r["c"] or 0), round(float(r["s"] or 0), 2)

        all_count, all_total = sum_for(0)
        ytd_count, ytd_total = sum_for(start_year)
        mtd_count, mtd_total = sum_for(start_month)
        wtd_count, wtd_total = sum_for(start_week)
        td_count, td_total = sum_for(start_today)

        active = c.execute(
            "SELECT COUNT(*) c FROM canada_licenses WHERE expires_at > ?",
            (now,),
        ).fetchone()["c"]
        expiring_30d = c.execute(
            "SELECT COUNT(*) c FROM canada_licenses WHERE expires_at > ? AND expires_at <= ?",
            (now, now + 30 * 24 * 60 * 60 * 1000),
        ).fetchone()["c"]
        expired = c.execute(
            "SELECT COUNT(*) c FROM canada_licenses WHERE expires_at <= ?",
            (now,),
        ).fetchone()["c"]

        # devices seen in last 5 min / 24 h
        five_min = c.execute(
            "SELECT COUNT(*) c FROM canada_devices WHERE last_seen_at > ?",
            (now - 5 * 60 * 1000,),
        ).fetchone()["c"]
        day = c.execute(
            "SELECT COUNT(*) c FROM canada_devices WHERE last_seen_at > ?",
            (now - 24 * 60 * 60 * 1000,),
        ).fetchone()["c"]

        chart = []
        for (s, e, label) in months_back:
            cnt, tot = sum_for(s, e)
            chart.append({"month": label, "count": cnt, "total_cad": tot})

        # Expiring-soon list (top 30, soonest first)
        soon_rows = c.execute(
            """SELECT xtream_username, expires_at, last_order_id
               FROM canada_licenses
               WHERE expires_at > ? AND expires_at <= ?
               ORDER BY expires_at ASC LIMIT 30""",
            (now, now + 30 * 24 * 60 * 60 * 1000),
        ).fetchall()
        expiring_soon = [
            {
                "xtream_username": r["xtream_username"],
                "expires_at": int(r["expires_at"]),
                "days_remaining": max(0, (int(r["expires_at"]) - now) // (24 * 60 * 60 * 1000)),
                "last_order_id": r["last_order_id"],
            }
            for r in soon_rows
        ]

    return {
        "now_ms": now,
        "revenue": {
            "today":    {"count": td_count,  "total_cad": td_total},
            "week":     {"count": wtd_count, "total_cad": wtd_total},
            "month":    {"count": mtd_count, "total_cad": mtd_total},
            "ytd":      {"count": ytd_count, "total_cad": ytd_total},
            "all_time": {"count": all_count, "total_cad": all_total},
        },
        "licenses": {
            "active": int(active),
            "expiring_30d": int(expiring_30d),
            "expired": int(expired),
        },
        "active_devices": {
            "last_5min": int(five_min),
            "last_24h":  int(day),
        },
        "monthly_chart": chart,
        "expiring_soon": expiring_soon,
    }


# ── Manual extend + reminder ───────────────────────────────────────
class AdminExtendReq(BaseModel):
    xtream_username: str
    days: int = Field(365, ge=1, le=3650)
    reason: Optional[str] = None
    actor: Optional[str] = None  # who clicked the button


@admin_router.post("/license/{username}/extend")
def admin_extend(
    username: str,
    req: AdminExtendReq,
    x_admin_token: Optional[str] = Header(None),
) -> dict:
    """Extend a license by N days (default 365). Adds time on top of
    the current expiry if active, or starts a fresh window from now if
    expired/missing. Logged into canada_admin_events for accounting.
    """
    _check_admin(x_admin_token)
    user = (req.xtream_username or username).strip().lower()
    now = _now_ms()
    ms_to_add = int(req.days) * 24 * 60 * 60 * 1000
    with _conn() as c:
        row = _get_license_row(c, user)
        base = max(int(row["expires_at"]) if row else 0, now)
        new_expires = base + ms_to_add
        c.execute(
            """INSERT INTO canada_licenses (xtream_username, paid_at, expires_at, last_order_id)
               VALUES (?, ?, ?, ?)
               ON CONFLICT(xtream_username) DO UPDATE SET
                  expires_at=excluded.expires_at,
                  last_order_id=excluded.last_order_id""",
            (user, now, new_expires, "ADMIN_EXTEND"),
        )
        c.execute(
            "INSERT INTO canada_admin_events (at, action, xtream_username, actor, detail) "
            "VALUES (?, 'extend', ?, ?, ?)",
            (now, user, req.actor or "admin", f"+{req.days}d reason={req.reason or ''}"),
        )
        # v1.44.87 — manual admin extends also push to Base44.
        try:
            base44_module.record_payment_sync(
                c,
                xtream_username=user,
                order_id=f"ADMIN_EXTEND_{now}",
                amount_cad=0.0,
                paid_at_ms=now,
                expires_at_ms=new_expires,
                interac_sender=f"admin extend (+{req.days}d) by {req.actor or 'admin'}",
            )
        except Exception as e:
            log.exception("base44 push failed for admin_extend user=%s: %s", user, e)
        row2 = _get_license_row(c, user)
        return {"extended": True, "license": _serialize_license(row2)}


class AdminReminderReq(BaseModel):
    xtream_username: str
    to_email: str = Field(..., min_length=3, max_length=256)
    actor: Optional[str] = None


def _smtp_send(to_email: str, subject: str, body: str) -> dict:
    """Send a plain-text email via Gmail SMTP using the same credentials
    the IMAP poller uses. Returns {"sent": bool, "error": str|None}.
    """
    if not GMAIL_USER or not GMAIL_PASS:
        return {"sent": False, "error": "gmail credentials not configured"}
    msg = EmailMessage()
    msg["From"] = GMAIL_USER
    msg["To"] = to_email
    msg["Subject"] = subject
    msg.set_content(body)
    try:
        with smtplib.SMTP_SSL("smtp.gmail.com", 465, timeout=20) as s:
            s.login(GMAIL_USER, GMAIL_PASS)
            s.send_message(msg)
        return {"sent": True, "error": None}
    except Exception as e:
        log.exception("SMTP send failed for %s: %s", to_email, e)
        return {"sent": False, "error": str(e)}


@admin_router.post("/license/{username}/remind")
def admin_remind(
    username: str,
    req: AdminReminderReq,
    x_admin_token: Optional[str] = Header(None),
) -> dict:
    """Send a renewal reminder email. Logs the attempt into
    canada_admin_events regardless of SMTP success/failure so accounting
    has a record of outreach."""
    _check_admin(x_admin_token)
    user = (req.xtream_username or username).strip().lower()
    now = _now_ms()
    with _conn() as c:
        row = _get_license_row(c, user)
        if row is None:
            raise HTTPException(404, "license not found for that user")
        expires_at = int(row["expires_at"])
        days_left = max(0, (expires_at - now) // (24 * 60 * 60 * 1000))
        import datetime as _dt
        exp_iso = _dt.datetime.utcfromtimestamp(expires_at / 1000).strftime("%B %d, %Y")
        subject = (
            f"HushTV Canada — your subscription expires in {days_left} days"
            if days_left > 0
            else "HushTV Canada — your subscription has expired"
        )
        body = (
            f"Hi,\n\n"
            f"Your HushTV Canada subscription tied to username '{user}' is "
            f"{'set to expire' if days_left > 0 else 'expired'} on {exp_iso}.\n\n"
            f"To renew, open the HushTV Canada app — you'll see your Order ID "
            f"on the renewal screen. Send ${EXPECTED_AMOUNT_CAD:.2f} CAD via "
            f"Interac e-Transfer to {GMAIL_USER} with that Order ID typed into "
            f"the 'Message' field.\n\n"
            f"Once we receive your payment your subscription is automatically "
            f"extended by one year. Pay once, unlocks all your devices.\n\n"
            f"— HushTV Canada"
        )
        result = _smtp_send(req.to_email, subject, body)
        c.execute(
            "INSERT INTO canada_admin_events (at, action, xtream_username, actor, detail) "
            "VALUES (?, 'remind', ?, ?, ?)",
            (
                now, user, req.actor or "admin",
                f"to={req.to_email} sent={result['sent']} err={result.get('error') or ''}",
            ),
        )
        return {"reminded": result["sent"], "error": result.get("error"),
                "to": req.to_email, "days_remaining": days_left}


@admin_router.get("/events")
def admin_events(
    x_admin_token: Optional[str] = Header(None),
    limit: int = 200,
    username: Optional[str] = None,
) -> dict:
    """Audit log for admin actions (extend, revoke, remind)."""
    _check_admin(x_admin_token)
    limit = max(1, min(1000, limit))
    sql = "SELECT * FROM canada_admin_events"
    params: list = []
    if username:
        sql += " WHERE xtream_username=?"
        params.append(username.strip().lower())
    sql += " ORDER BY at DESC LIMIT ?"
    params.append(limit)
    with _conn() as c:
        rows = c.execute(sql, params).fetchall()
        return {
            "events": [
                {
                    "id": int(r["id"]),
                    "at": int(r["at"]),
                    "action": r["action"],
                    "xtream_username": r["xtream_username"],
                    "actor": r["actor"],
                    "detail": r["detail"],
                }
                for r in rows
            ]
        }


@admin_router.get("/base44/inbox")
def admin_base44_inbox(
    x_admin_token: Optional[str] = Header(None),
    status: Optional[str] = None,
    limit: int = 100,
) -> dict:
    """List inbound webhook events from Base44. Powers the new
    "Inbound from Base44" section in the admin Revenue tab."""
    _check_admin(x_admin_token)
    limit = max(1, min(500, limit))
    sql = "SELECT * FROM canada_base44_events_inbox"
    params: list = []
    if status:
        sql += " WHERE status=?"
        params.append(status)
    sql += " ORDER BY received_at DESC LIMIT ?"
    params.append(limit)
    with _conn() as c:
        rows = c.execute(sql, params).fetchall()
        return {
            "count": len(rows),
            "rows": [
                {
                    "event_id": r["event_id"],
                    "event_type": r["event_type"],
                    "received_at": int(r["received_at"]),
                    "processed_at": int(r["processed_at"]) if r["processed_at"] else None,
                    "xtream_username": r["xtream_username"],
                    "status": r["status"],
                    "applied": r["applied"],
                    "error": r["error"],
                }
                for r in rows
            ],
        }


@admin_router.post("/poll")
def admin_force_poll(x_admin_token: Optional[str] = Header(None)) -> dict:
    """Force an immediate IMAP scan — useful for QA + ops."""
    _check_admin(x_admin_token)
    s = _imap_scan_once()
    return {"scan": s}


# ── Base44 resync + queue inspection ────────────────────────────────
@admin_router.get("/base44/queue")
def admin_base44_queue(
    x_admin_token: Optional[str] = Header(None),
    status: Optional[str] = None,
    limit: int = 200,
) -> dict:
    """List the Base44 retry queue. Status: pending | failed | succeeded."""
    _check_admin(x_admin_token)
    limit = max(1, min(1000, limit))
    sql = "SELECT * FROM canada_base44_retry"
    params: list = []
    if status:
        sql += " WHERE status=?"
        params.append(status)
    sql += " ORDER BY last_attempt_at DESC LIMIT ?"
    params.append(limit)
    with _conn() as c:
        rows = c.execute(sql, params).fetchall()
        return {
            "count": len(rows),
            "rows": [
                {
                    "id": int(r["id"]),
                    "order_id": r["order_id"],
                    "xtream_username": r["xtream_username"],
                    "first_attempt_at": int(r["first_attempt_at"]),
                    "last_attempt_at": int(r["last_attempt_at"]),
                    "next_attempt_at": int(r["next_attempt_at"]),
                    "attempts": int(r["attempts"]),
                    "status": r["status"],
                    "last_error": r["last_error"],
                }
                for r in rows
            ],
        }


class Base44ResyncReq(BaseModel):
    order_id: str
    actor: Optional[str] = None


@admin_router.post("/base44/resync")
def admin_base44_resync(
    req: Base44ResyncReq,
    x_admin_token: Optional[str] = Header(None),
) -> dict:
    """Force-push a single paid order to Base44. Useful when:
      • Base44 was down at payment time and the retry queue gave up.
      • A pre-Base44 historical payment needs back-filling.
      • The admin updated user details in Base44 and wants HushTV to
        re-trigger the confirmation email.
    """
    _check_admin(x_admin_token)
    with _conn() as c:
        row = c.execute(
            "SELECT * FROM canada_orders WHERE order_id=? AND status='paid'",
            (req.order_id,),
        ).fetchone()
        if row is None:
            raise HTTPException(404, "paid order not found")
        lic = _get_license_row(c, row["xtream_username"])
        expires_at_ms = int(lic["expires_at"]) if lic else (
            int(row["paid_at"]) + LICENSE_YEAR_MS
        )
    result = base44_module.resync_one(
        xtream_username=row["xtream_username"],
        order_id=row["order_id"],
        amount_cad=float(row["interac_amount"]) if row["interac_amount"] else 0.0,
        paid_at_ms=int(row["paid_at"]),
        expires_at_ms=expires_at_ms,
        interac_sender=_clean_sender(row["interac_sender"]),
        actor=req.actor or "admin",
    )
    return result


@admin_router.post("/base44/resync-all")
def admin_base44_resync_all(
    x_admin_token: Optional[str] = Header(None),
    actor: Optional[str] = "admin-bulk",
) -> dict:
    """Bulk-resync every PAID license to Base44.

    Use after a Base44 migration, outage, or whenever you suspect
    drift between HushTV and Base44.

    Idempotency: order_id is deterministic per (user, expires_at) —
    re-running with no license changes hits Base44's dedupe and
    returns `duplicate: true` for every row (no spam emails). If a
    user's license was extended since last resync, their order_id
    changes, so Base44 records a new payment + fires a fresh email.
    """
    _check_admin(x_admin_token)
    actor = actor or "admin-bulk"
    summary = {
        "total": 0, "synced": 0, "duplicates": 0,
        "permanent_failures": 0, "transient_failures": 0,
        "errors": [],
    }
    with _conn() as c:
        rows = c.execute(
            """SELECT l.xtream_username, l.paid_at, l.expires_at, l.last_order_id,
                  (SELECT MAX(o.interac_amount) FROM canada_orders o
                     WHERE o.xtream_username=l.xtream_username
                       AND o.status='paid') AS last_amount,
                  (SELECT o.interac_sender FROM canada_orders o
                     WHERE o.xtream_username=l.xtream_username
                       AND o.status='paid'
                     ORDER BY o.paid_at DESC LIMIT 1) AS last_sender
               FROM canada_licenses l
               ORDER BY l.expires_at DESC""",
        ).fetchall()

    for r in rows:
        user = r["xtream_username"]
        expires_at_ms = int(r["expires_at"])
        # Deterministic per (user, expires_at) — Base44 dedupes on a
        # repeat run, but a fresh extend changes the expires_at and so
        # the order_id (→ new payment row + new confirmation email).
        order_id = f"RESYNC_{user}_{expires_at_ms}"
        amount = float(r["last_amount"]) if r["last_amount"] else 0.0
        sender = _clean_sender(r["last_sender"]) if r["last_sender"] else f"resync-all by {actor}"
        result = base44_module.resync_one(
            xtream_username=user,
            order_id=order_id,
            amount_cad=amount,
            paid_at_ms=int(r["paid_at"]),
            expires_at_ms=expires_at_ms,
            interac_sender=sender,
            actor=actor,
        )
        summary["total"] += 1
        if result.get("ok"):
            summary["synced"] += 1
            if result.get("duplicate"):
                summary["duplicates"] += 1
        else:
            http = result.get("http", 0)
            if http in (0,) or (isinstance(http, int) and http >= 500) or http == 429:
                summary["transient_failures"] += 1
            else:
                summary["permanent_failures"] += 1
            summary["errors"].append({
                "xtream_username": user,
                "error": result.get("error", "unknown"),
                "http": http,
            })
    # Top-level audit row so you can see the bulk action in /events.
    with _conn() as c:
        c.execute(
            "INSERT INTO canada_admin_events (at, action, xtream_username, actor, detail) "
            "VALUES (?, 'base44_resync_all', NULL, ?, ?)",
            (
                _now_ms(), actor,
                f"total={summary['total']} synced={summary['synced']} "
                f"dup={summary['duplicates']} perm_fail={summary['permanent_failures']} "
                f"transient={summary['transient_failures']}"[:500],
            ),
        )
    return summary


# Schema init on import so `import canada_payment_module` is enough.
try:
    _init_schema()
    base44_module.init_schema()
    base44_module.start_retry_worker()
except Exception as e:  # pragma: no cover
    log.exception("canada_payment_module schema init failed: %s", e)
