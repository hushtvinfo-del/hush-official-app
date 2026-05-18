"""HushTV cross-device sync service.

Listens on 127.0.0.1:5056. Reverse-proxied by nginx as
`/api/sync/...`.

Storage: a single SQLite database at `/var/hushtv-sync/sync.sqlite3`
with one row per `(user_id, store)` pair holding the most recent
serialized prefs blob plus a `ts` (ms) for last-write-wins.

Special-case: when `store == "hushtv_watch_progress"` we don't blow
away the existing row with the upload — instead we merge
per-record by the embedded `lastWatchedAt` so two devices both
watching different titles concurrently never clobber each other's
positions. Other stores are simple LWW on the whole blob.

Endpoints:
  GET  /api/sync/health
  POST /api/sync/state    body = SyncRequest
"""
from __future__ import annotations

import json
import logging
import os
import sqlite3
import threading
import time
from contextlib import contextmanager
from typing import Any, Dict, Iterable, List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

DB_PATH = os.environ.get("HUSHSYNC_DB", "/var/hushtv-sync/sync.sqlite3")
SHARED_TOKEN = os.environ.get("HUSHSYNC_TOKEN", "")  # optional shared bearer

# ── DB ──────────────────────────────────────────────────────────────
_db_lock = threading.Lock()


def _ensure_db_dir() -> None:
    d = os.path.dirname(DB_PATH)
    if d and not os.path.isdir(d):
        os.makedirs(d, exist_ok=True)


def _connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, timeout=20, isolation_level=None)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.row_factory = sqlite3.Row
    return conn


def _init() -> None:
    _ensure_db_dir()
    with _connect() as c:
        c.executescript(
            """
            CREATE TABLE IF NOT EXISTS sync_state (
                user_id TEXT NOT NULL,
                store   TEXT NOT NULL,
                ts      INTEGER NOT NULL,
                blob    TEXT NOT NULL,
                PRIMARY KEY (user_id, store)
            );
            CREATE INDEX IF NOT EXISTS sync_state_user_idx
                ON sync_state(user_id);
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


# ── Schemas ─────────────────────────────────────────────────────────
class SyncStoreUpload(BaseModel):
    store: str = Field(..., min_length=1, max_length=64)
    ts: int = Field(..., ge=0)
    blob: Dict[str, str] = Field(default_factory=dict)


class SyncRequest(BaseModel):
    user_id: str = Field(..., min_length=4, max_length=64)
    uploads: List[SyncStoreUpload] = Field(default_factory=list)
    known_ts: Dict[str, int] = Field(default_factory=dict)


class SyncStoreDownload(BaseModel):
    store: str
    ts: int
    blob: Dict[str, str]


class SyncResponse(BaseModel):
    server_ts: int
    downloads: List[SyncStoreDownload]


# ── CW per-record merge ─────────────────────────────────────────────
CW_STORE = "hushtv_watch_progress"
US = "\u001f"  # field separator inside the encoded entry string


def _ts_from_cw(s: str) -> int:
    if not s:
        return 0
    parts = s.split(US)
    if len(parts) < 7:
        return 0
    try:
        return int(parts[6])
    except (ValueError, TypeError):
        return 0


def _merge_cw(server_blob: Dict[str, str], client_blob: Dict[str, str]) -> Dict[str, str]:
    """Per-record LWW by embedded `lastWatchedAt`."""
    out = dict(server_blob)
    for k, v in client_blob.items():
        if _ts_from_cw(v) > _ts_from_cw(out.get(k, "")):
            out[k] = v
    return out


# ── App ────────────────────────────────────────────────────────────
log = logging.getLogger("hushsync")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
app = FastAPI(title="HushTV sync")


@app.on_event("startup")
def _startup() -> None:
    _init()
    log.info("HushTV sync ready, DB=%s", DB_PATH)
    # Sports module — separate SQLite DB, separate background worker.
    # Routes mounted under /api/sports/* and /api/admin/sports/*.
    try:
        import sports_module  # type: ignore
        sports_module.init()
        app.include_router(sports_module.router)
        app.include_router(sports_module.admin_router)
        log.info("HushTV sports module mounted, DB=%s", sports_module.DB_PATH)
    except Exception as e:
        log.exception("sports module failed to mount: %s", e)

    # Canada $40 CDN Proxy Fee module — Interac e-Transfer auto-verify.
    # Routes mounted under /api/canada/* and /api/admin/canada/*.
    try:
        import canada_payment_module  # type: ignore
        app.include_router(canada_payment_module.router)
        app.include_router(canada_payment_module.admin_router)
        canada_payment_module.start_poller()
        log.info("HushTV canada payment module mounted, poller running")
    except Exception as e:
        log.exception("canada payment module failed to mount: %s", e)


@app.get("/api/sync/health")
def health() -> Dict[str, Any]:
    with _conn() as c:
        cur = c.execute("SELECT COUNT(*) FROM sync_state")
        rows = cur.fetchone()[0]
    return {"ok": True, "rows": rows, "now_ms": int(time.time() * 1000)}


@app.post("/api/sync/state", response_model=SyncResponse)
def sync_state(req: SyncRequest) -> SyncResponse:
    user_id = req.user_id.strip()
    if not user_id:
        raise HTTPException(400, "user_id required")
    server_ts = int(time.time() * 1000)

    with _conn() as c:
        # Apply uploads
        for up in req.uploads:
            store = up.store.strip()
            if not store:
                continue
            existing_row = c.execute(
                "SELECT ts, blob FROM sync_state WHERE user_id=? AND store=?",
                (user_id, store),
            ).fetchone()
            if store == CW_STORE and existing_row is not None:
                # Per-record merge
                try:
                    existing_blob = json.loads(existing_row["blob"])
                except (json.JSONDecodeError, TypeError):
                    existing_blob = {}
                merged = _merge_cw(existing_blob, up.blob)
                new_ts = max(int(existing_row["ts"]), int(up.ts), server_ts)
                c.execute(
                    "INSERT OR REPLACE INTO sync_state (user_id, store, ts, blob) "
                    "VALUES (?, ?, ?, ?)",
                    (user_id, store, new_ts, json.dumps(merged)),
                )
            else:
                # LWW on the whole blob
                if existing_row is not None and int(up.ts) <= int(existing_row["ts"]):
                    # Older or equal — skip
                    continue
                c.execute(
                    "INSERT OR REPLACE INTO sync_state (user_id, store, ts, blob) "
                    "VALUES (?, ?, ?, ?)",
                    (user_id, store, int(up.ts), json.dumps(up.blob)),
                )

        # Compute downloads (server has newer than client knew)
        downloads: List[SyncStoreDownload] = []
        cur = c.execute(
            "SELECT store, ts, blob FROM sync_state WHERE user_id=?",
            (user_id,),
        )
        for row in cur.fetchall():
            store = row["store"]
            ts = int(row["ts"])
            client_known = int(req.known_ts.get(store, 0))
            if ts > client_known:
                try:
                    blob = json.loads(row["blob"])
                except (json.JSONDecodeError, TypeError):
                    blob = {}
                downloads.append(
                    SyncStoreDownload(store=store, ts=ts, blob=blob)
                )

    return SyncResponse(server_ts=server_ts, downloads=downloads)
