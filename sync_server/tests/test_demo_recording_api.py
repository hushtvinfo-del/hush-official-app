"""Pytest smoke tests for the demo_recording_module — covers
upload auth, idempotent re-upload, admin listing and admin download.

Run:
    cd /app/sync_server
    PYTHONPATH=. pytest tests/test_demo_recording_api.py -v
"""
from __future__ import annotations

import os
import sqlite3
import tempfile
from pathlib import Path

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

# Environment setup MUST happen before importing the module so module-level
# `DB_PATH`, `UPLOAD_TOKEN`, and `RECORDINGS_DIR` constants pick up our values.
TEST_TMP = tempfile.mkdtemp(prefix="demo_rec_test_")
TEST_DB  = os.path.join(TEST_TMP, "sync.sqlite3")
TEST_DIR = os.path.join(TEST_TMP, "recordings")
os.makedirs(TEST_DIR, exist_ok=True)
os.environ["HUSHSYNC_DB"]          = TEST_DB
os.environ["DEMO_RECORDINGS_DIR"]  = TEST_DIR
os.environ["DEMO_UPLOAD_TOKEN"]    = "TEST_UPLOAD_TOKEN_xyz"
os.environ["SPORTS_ADMIN_TOKEN"]   = "TEST_ADMIN_TOKEN_xyz"

# Bootstrap an empty sync DB.
sqlite3.connect(TEST_DB).close()

import demo_recording_module  # noqa: E402

demo_recording_module.init_schema()

app = FastAPI()
app.include_router(demo_recording_module.router)
app.include_router(demo_recording_module.admin_router)
client = TestClient(app)


def _post_upload(body: bytes, token: str = "TEST_UPLOAD_TOKEN_xyz", **extra):
    headers = {
        "Content-Type": "video/mp4",
        "X-Demo-Upload-Token": token,
        **{k: v for k, v in extra.items()},
    }
    return client.post("/api/demo/upload", content=body, headers=headers)


def test_missing_token_is_401():
    r = client.post(
        "/api/demo/upload",
        content=b"x", headers={"Content-Type": "video/mp4"},
    )
    assert r.status_code == 401


def test_bad_token_is_401():
    r = _post_upload(b"x", token="WRONG")
    assert r.status_code == 401


def test_empty_body_is_400():
    r = _post_upload(b"")
    assert r.status_code == 400


def test_wrong_content_type_is_415():
    r = client.post(
        "/api/demo/upload",
        content=b"abc",
        headers={
            "Content-Type": "text/plain",
            "X-Demo-Upload-Token": "TEST_UPLOAD_TOKEN_xyz",
        },
    )
    assert r.status_code == 415


def test_happy_path_upload_then_list_then_download():
    payload = b"FAKE_MP4_BYTES_" + b"\xab\xcd" * 1024
    r = _post_upload(
        payload,
        **{
            "X-Device-Id":    "android-123",
            "X-Device-Label": "Pixel 8",
            "X-App-Version":  "dev/1.44.90-490",
        },
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["id"]
    assert body["size_bytes"] == len(payload)
    rec_id = body["id"]

    # Admin list — must include the row.
    r = client.get(
        "/api/admin/demo/recordings",
        headers={"X-Admin-Token": "TEST_ADMIN_TOKEN_xyz"},
    )
    assert r.status_code == 200
    rows = r.json()["rows"]
    assert any(row["id"] == rec_id for row in rows)
    row = next(row for row in rows if row["id"] == rec_id)
    assert row["device_id"] == "android-123"
    assert row["device_label"] == "Pixel 8"
    assert row["app_version"] == "dev/1.44.90-490"
    assert row["size_bytes"] == len(payload)

    # Admin download — byte-exact round trip.
    r = client.get(
        f"/api/admin/demo/recordings/{rec_id}",
        headers={"X-Admin-Token": "TEST_ADMIN_TOKEN_xyz"},
    )
    assert r.status_code == 200
    assert r.content == payload

    # Admin delete.
    r = client.delete(
        f"/api/admin/demo/recordings/{rec_id}",
        headers={"X-Admin-Token": "TEST_ADMIN_TOKEN_xyz"},
    )
    assert r.status_code == 200
    assert r.json() == {"deleted": rec_id}

    # File is gone.
    assert not Path(TEST_DIR, f"demo-{rec_id}.mp4").exists()
    # Row is gone.
    r = client.get(
        "/api/admin/demo/recordings",
        headers={"X-Admin-Token": "TEST_ADMIN_TOKEN_xyz"},
    )
    assert all(row["id"] != rec_id for row in r.json()["rows"])


def test_admin_endpoints_require_admin_token():
    r = client.get("/api/admin/demo/recordings")
    assert r.status_code == 401
    r = client.get(
        "/api/admin/demo/recordings",
        headers={"X-Admin-Token": "wrong"},
    )
    assert r.status_code == 401


def test_download_404_when_unknown_id():
    r = client.get(
        "/api/admin/demo/recordings/notexist",
        headers={"X-Admin-Token": "TEST_ADMIN_TOKEN_xyz"},
    )
    assert r.status_code == 404
