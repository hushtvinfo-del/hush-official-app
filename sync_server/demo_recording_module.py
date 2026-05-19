"""HushTV — Demo recording uploads.

Dev-flavor Android clients (and only those) can record their screen via
MediaProjection and POST the resulting MP4 here. Admins then list +
download recordings from the standalone admin page.

Endpoints
---------
  POST /api/demo/upload                 (public, gated by DEMO_UPLOAD_TOKEN)
  GET  /api/admin/demo/recordings       (admin Basic-Auth)
  GET  /api/admin/demo/recordings/{id}  (admin Basic-Auth, streams MP4)
  DELETE /api/admin/demo/recordings/{id} (admin)

Storage
-------
  • /var/hushtv-sync/recordings/<id>.mp4   — raw uploaded file
  • SQLite table `demo_recordings`         — metadata

Auth
----
  • Upload  : `X-Demo-Upload-Token: <DEMO_UPLOAD_TOKEN env>` header.
              Token is baked into the dev-flavor BuildConfig at build
              time — never shipped to official / canada builds.
  • Admin   : X-Admin-Token header (same gate as the rest of /admin).
"""
from __future__ import annotations

import logging
import os
import sqlite3
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, Header, HTTPException, Request
from fastapi.responses import FileResponse

log = logging.getLogger("demo_recording")

DB_PATH = os.environ.get("HUSHSYNC_DB", "/var/hushtv-sync/sync.sqlite3")
RECORDINGS_DIR = Path(os.environ.get(
    "DEMO_RECORDINGS_DIR", "/var/hushtv-sync/recordings",
))
RECORDINGS_DIR.mkdir(parents=True, exist_ok=True)

UPLOAD_TOKEN = os.environ.get("DEMO_UPLOAD_TOKEN", "")
ADMIN_TOKEN = os.environ.get("SPORTS_ADMIN_TOKEN", "")

# Maximum upload size — 2 GB. Real demos are ~50-200 MB. We size big
# so a long recording (15+ min at 8 Mbps) doesn't truncate.
MAX_UPLOAD_BYTES = 2 * 1024 * 1024 * 1024


@contextmanager
def _conn():
    c = sqlite3.connect(DB_PATH, timeout=20)
    c.row_factory = sqlite3.Row
    try:
        yield c
        c.commit()
    finally:
        c.close()


def init_schema() -> None:
    with _conn() as c:
        c.executescript(
            """
            CREATE TABLE IF NOT EXISTS demo_recordings (
                id            TEXT PRIMARY KEY,        -- short uuid
                uploaded_at   INTEGER NOT NULL,        -- epoch ms
                device_id     TEXT,                    -- ANDROID_ID
                device_label  TEXT,                    -- model name
                duration_ms   INTEGER,                 -- optional, set by client
                size_bytes    INTEGER NOT NULL,
                filename      TEXT NOT NULL,           -- on-disk filename
                app_version   TEXT,
                note          TEXT                     -- optional user note
            );
            CREATE INDEX IF NOT EXISTS demo_recordings_uploaded_idx
                ON demo_recordings(uploaded_at DESC);
            """
        )


router = APIRouter(prefix="/api/demo", tags=["demo"])
admin_router = APIRouter(prefix="/api/admin/demo", tags=["demo-admin"])


def _check_upload_token(provided: Optional[str]) -> None:
    if not UPLOAD_TOKEN:
        raise HTTPException(503, "DEMO_UPLOAD_TOKEN not configured")
    if not provided or provided != UPLOAD_TOKEN:
        raise HTTPException(401, "invalid demo upload token")


def _check_admin(provided: Optional[str]) -> None:
    if not ADMIN_TOKEN:
        raise HTTPException(503, "SPORTS_ADMIN_TOKEN not configured")
    if not provided or provided != ADMIN_TOKEN:
        raise HTTPException(401, "invalid admin token")


@router.post("/upload")
async def upload(
    request: Request,
    x_demo_upload_token: Optional[str] = Header(None),
    x_device_id: Optional[str] = Header(None),
    x_device_label: Optional[str] = Header(None),
    x_app_version: Optional[str] = Header(None),
    x_duration_ms: Optional[str] = Header(None),
    x_note: Optional[str] = Header(None),
) -> dict:
    """Streaming MP4 upload from the dev-flavor Android client.

    The client POSTs the raw MP4 as the request body (not multipart) so
    we can stream to disk without buffering the whole file in RAM. A
    200 MB upload is ~25 MB/s on a decent home line, finishing in 8 s.
    """
    _check_upload_token(x_demo_upload_token)
    ct = (request.headers.get("content-type") or "").lower()
    if "video/mp4" not in ct and "application/octet-stream" not in ct:
        raise HTTPException(415, f"unsupported content-type: {ct}")
    rec_id = uuid.uuid4().hex[:12]
    filename = f"demo-{rec_id}.mp4"
    dest = RECORDINGS_DIR / filename
    try:
        size = 0
        with dest.open("wb") as f:
            async for chunk in request.stream():
                if not chunk:
                    continue
                size += len(chunk)
                if size > MAX_UPLOAD_BYTES:
                    f.close()
                    dest.unlink(missing_ok=True)
                    raise HTTPException(413, "upload exceeds 2 GB limit")
                f.write(chunk)
        if size == 0:
            dest.unlink(missing_ok=True)
            raise HTTPException(400, "empty body")
        with _conn() as c:
            c.execute(
                """INSERT INTO demo_recordings
                    (id, uploaded_at, device_id, device_label,
                     duration_ms, size_bytes, filename, app_version, note)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    rec_id, int(time.time() * 1000),
                    (x_device_id or "")[:128] or None,
                    (x_device_label or "")[:128] or None,
                    int(x_duration_ms) if x_duration_ms and x_duration_ms.isdigit() else None,
                    size, filename,
                    (x_app_version or "")[:64] or None,
                    (x_note or "")[:512] or None,
                ),
            )
        log.info("demo upload OK id=%s size=%d device=%s", rec_id, size, x_device_id)
        return {
            "id": rec_id,
            "size_bytes": size,
            "url": f"/api/admin/demo/recordings/{rec_id}",
        }
    except HTTPException:
        raise
    except Exception as e:
        log.exception("demo upload FAILED id=%s: %s", rec_id, e)
        dest.unlink(missing_ok=True)
        raise HTTPException(500, f"upload failed: {e}")


@admin_router.get("/recordings")
def list_recordings(
    x_admin_token: Optional[str] = Header(None),
    limit: int = 100,
) -> dict:
    _check_admin(x_admin_token)
    limit = max(1, min(500, limit))
    with _conn() as c:
        rows = c.execute(
            """SELECT * FROM demo_recordings
               ORDER BY uploaded_at DESC LIMIT ?""", (limit,),
        ).fetchall()
        return {
            "count": len(rows),
            "rows": [
                {
                    "id": r["id"],
                    "uploaded_at": int(r["uploaded_at"]),
                    "device_id": r["device_id"],
                    "device_label": r["device_label"],
                    "duration_ms": int(r["duration_ms"]) if r["duration_ms"] else None,
                    "size_bytes": int(r["size_bytes"]),
                    "size_human": _human_size(int(r["size_bytes"])),
                    "filename": r["filename"],
                    "app_version": r["app_version"],
                    "note": r["note"],
                    "download_url": f"/api/admin/demo/recordings/{r['id']}",
                }
                for r in rows
            ],
        }


def _human_size(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024:
            return f"{n:.1f} {unit}"
        n /= 1024
    return f"{n:.1f} TB"


@admin_router.get("/recordings/{rec_id}")
def download(
    rec_id: str, x_admin_token: Optional[str] = Header(None),
):
    _check_admin(x_admin_token)
    with _conn() as c:
        row = c.execute(
            "SELECT filename FROM demo_recordings WHERE id=?", (rec_id,),
        ).fetchone()
    if row is None:
        raise HTTPException(404, "recording not found")
    path = RECORDINGS_DIR / row["filename"]
    if not path.exists():
        raise HTTPException(410, "file missing on disk (was it manually deleted?)")
    return FileResponse(
        path,
        media_type="video/mp4",
        filename=row["filename"],
    )


@admin_router.delete("/recordings/{rec_id}")
def delete_recording(
    rec_id: str, x_admin_token: Optional[str] = Header(None),
) -> dict:
    _check_admin(x_admin_token)
    with _conn() as c:
        row = c.execute(
            "SELECT filename FROM demo_recordings WHERE id=?", (rec_id,),
        ).fetchone()
        if row is None:
            raise HTTPException(404, "not found")
        path = RECORDINGS_DIR / row["filename"]
        path.unlink(missing_ok=True)
        c.execute("DELETE FROM demo_recordings WHERE id=?", (rec_id,))
        return {"deleted": rec_id}


try:
    init_schema()
except Exception as e:  # pragma: no cover
    log.exception("demo_recording_module schema init failed: %s", e)
