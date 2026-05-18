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
in three new tables:
  - canada_orders         (order_id PK, xtream_username, status, …)
  - canada_licenses       (xtream_username PK, paid_at, expires_at)
  - canada_processed_emails (uid PK)   — idempotency for IMAP poller

Routes mounted under /api/canada/* by hushsync_app.py.
"""
from __future__ import annotations

import email
import imaplib
import logging
import os
import random
import re
import sqlite3
import threading
import time
from contextlib import contextmanager
from email.message import Message
from typing import Optional

from bs4 import BeautifulSoup
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

log = logging.getLogger("canada_payment")

DB_PATH = os.environ.get("HUSHSYNC_DB", "/var/hushtv-sync/sync.sqlite3")
GMAIL_USER = os.environ.get("INTERAC_GMAIL_USER", "Hushtv.info@gmail.com")
GMAIL_PASS = os.environ.get("INTERAC_GMAIL_APP_PASSWORD", "")
ADMIN_TOKEN = os.environ.get("SPORTS_ADMIN_TOKEN", "")

# Business constants
EXPECTED_AMOUNT_CAD = 40.00          # the single, fixed CDN proxy fee
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
            """
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
    # Fallback: any 8-digit number in the body (less safe — only used if no
    # explicit Message: label was matched).
    if not order_id:
        oid_m = _ORDER_ID_RE.search(flat)
        if oid_m:
            order_id = oid_m.group(1)

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


def _already_processed(c, uid: str) -> bool:
    return c.execute(
        "SELECT 1 FROM canada_processed_emails WHERE uid=?", (uid,)
    ).fetchone() is not None


def _record_processed(c, uid: str, order_id: Optional[str], amount: Optional[float],
                      sender: Optional[str], outcome: str) -> None:
    c.execute(
        """INSERT OR REPLACE INTO canada_processed_emails
           (uid, processed_at, order_id, amount, sender, outcome)
           VALUES (?, ?, ?, ?, ?, ?)""",
        (uid, _now_ms(), order_id, f"{amount:.2f}" if amount else None, sender, outcome),
    )


def _process_single_email(c, uid: str, raw_msg_bytes: bytes) -> str:
    """Parse one email, reconcile against orders. Returns an outcome tag."""
    msg = email.message_from_bytes(raw_msg_bytes)
    from_addr = (msg.get("From") or "").lower()
    if INTERAC_SENDER not in from_addr:
        return "skipped_not_interac"
    body = _extract_email_body(msg)
    parsed = parse_interac_email(body)
    amount = parsed["amount_cad"]
    order_id = parsed["order_id"]
    sender_name = parsed["sender_name"]

    if amount is None or amount < EXPECTED_AMOUNT_CAD - 0.01:
        log.info("Interac email uid=%s amount=%s < expected — skipping", uid, amount)
        _record_processed(c, uid, order_id, amount, sender_name, "amount_too_low")
        return "amount_too_low"

    if not order_id:
        log.info("Interac email uid=%s has no parseable order id", uid)
        _record_processed(c, uid, order_id, amount, sender_name, "no_order_id")
        return "no_order_id"

    row = c.execute(
        "SELECT * FROM canada_orders WHERE order_id=?", (order_id,)
    ).fetchone()
    if row is None:
        log.info("Interac email uid=%s references unknown order_id=%s", uid, order_id)
        _record_processed(c, uid, order_id, amount, sender_name, "unknown_order")
        return "unknown_order"

    if row["status"] == "paid":
        _record_processed(c, uid, order_id, amount, sender_name, "already_paid")
        return "already_paid"

    # Reconcile.
    now = _now_ms()
    c.execute(
        """UPDATE canada_orders SET status='paid', paid_at=?, interac_amount=?,
           interac_sender=?, interac_email_uid=? WHERE order_id=?""",
        (now, f"{amount:.2f}", sender_name, uid, order_id),
    )
    _grant_license(c, row["xtream_username"], now, order_id)
    _record_processed(c, uid, order_id, amount, sender_name, "paid")
    log.info("Interac payment matched: order_id=%s user=%s amount=%.2f",
             order_id, row["xtream_username"], amount)
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
                if _already_processed(c, uid):
                    continue
                try:
                    typ2, msg_data = m.fetch(uid_bytes, "(RFC822)")
                    if typ2 != "OK" or not msg_data or not msg_data[0]:
                        summary["errors"] += 1
                        continue
                    raw = msg_data[0][1]
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
def admin_licenses(x_admin_token: Optional[str] = Header(None), limit: int = 500) -> dict:
    _check_admin(x_admin_token)
    limit = max(1, min(2000, limit))
    with _conn() as c:
        rows = c.execute(
            "SELECT * FROM canada_licenses ORDER BY expires_at DESC LIMIT ?", (limit,)
        ).fetchall()
        return {
            "licenses": [
                {
                    "xtream_username": r["xtream_username"],
                    "paid_at": int(r["paid_at"]),
                    "expires_at": int(r["expires_at"]),
                    "last_order_id": r["last_order_id"],
                }
                for r in rows
            ]
        }


@admin_router.post("/poll")
def admin_force_poll(x_admin_token: Optional[str] = Header(None)) -> dict:
    """Force an immediate IMAP scan — useful for QA + ops."""
    _check_admin(x_admin_token)
    s = _imap_scan_once()
    return {"scan": s}


# Schema init on import so `import canada_payment_module` is enough.
try:
    _init_schema()
except Exception as e:  # pragma: no cover
    log.exception("canada_payment_module schema init failed: %s", e)
