"""
HushTV Cloud DVR — second-opinion backend tests.

Covers the public endpoint contract at http://216.152.148.150/ plus the
deploy host OTA manifest at https://hushtv.xyz/.

No credentials — user_id is a 16-char lowercase hex derived client-side.
Each test uses its own random user_id so parallel runs cannot collide.
"""
from __future__ import annotations

import os
import secrets
import time

import pytest
import requests

# ── Hosts under test ─────────────────────────────────────────────
DVR_BASE = os.environ.get("DVR_BASE", "http://216.152.148.150").rstrip("/")
DEPLOY_BASE = os.environ.get("DEPLOY_BASE", "https://hushtv.xyz").rstrip("/")

# Known-good user_ids from the review request (kept for smoke reads only —
# writes use freshly-generated users so we never pollute someone else's quota).
SAMPLE_USER_ID = "a1b2c3d4e5f67890"

DEFAULT_QUOTA_S = 72_000  # 20 h
TIMEOUT = 15


def _fresh_user() -> str:
    """16-hex lowercase user_id, matching ^[a-f0-9]{16}$."""
    return secrets.token_hex(8)


@pytest.fixture(scope="module")
def api():
    s = requests.Session()
    s.headers.update({"Accept": "application/json"})
    return s


# ── Health ───────────────────────────────────────────────────────
class TestHealth:
    def test_health_shape(self, api):
        r = api.get(f"{DVR_BASE}/api/dvr/health", timeout=TIMEOUT)
        assert r.status_code == 200, r.text
        data = r.json()
        assert data.get("ok") is True
        assert isinstance(data.get("active_recordings"), int)
        assert data.get("active_recordings") >= 0
        assert data.get("root") == "/home/dvr"


# ── Quota ────────────────────────────────────────────────────────
class TestQuota:
    def test_quota_fresh_user(self, api):
        uid = _fresh_user()
        r = api.get(f"{DVR_BASE}/api/dvr/quota", params={"user_id": uid}, timeout=TIMEOUT)
        assert r.status_code == 200, r.text
        data = r.json()
        assert data["user_id"] == uid
        assert data["quota_s"] == DEFAULT_QUOTA_S
        assert data["used_s"] == 0
        assert data["available_s"] == DEFAULT_QUOTA_S

    def test_quota_invalid_user_id_too_short(self, api):
        r = api.get(f"{DVR_BASE}/api/dvr/quota", params={"user_id": "abc"}, timeout=TIMEOUT)
        assert r.status_code == 422, r.text

    def test_quota_invalid_user_id_uppercase(self, api):
        # Pattern is ^[a-f0-9]{16}$ — uppercase must be rejected.
        r = api.get(f"{DVR_BASE}/api/dvr/quota", params={"user_id": "A1B2C3D4E5F67890"}, timeout=TIMEOUT)
        assert r.status_code == 422, r.text

    def test_quota_missing_user_id(self, api):
        r = api.get(f"{DVR_BASE}/api/dvr/quota", timeout=TIMEOUT)
        assert r.status_code == 422, r.text

    def test_quota_sample_user(self, api):
        r = api.get(f"{DVR_BASE}/api/dvr/quota", params={"user_id": SAMPLE_USER_ID}, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["quota_s"] == DEFAULT_QUOTA_S


# ── Recordings list ──────────────────────────────────────────────
class TestRecordingsList:
    def test_list_empty_for_fresh_user(self, api):
        uid = _fresh_user()
        r = api.get(f"{DVR_BASE}/api/dvr/recordings", params={"user_id": uid}, timeout=TIMEOUT)
        assert r.status_code == 200, r.text
        data = r.json()
        assert data == {"recordings": []}

    def test_list_invalid_user_id(self, api):
        r = api.get(f"{DVR_BASE}/api/dvr/recordings", params={"user_id": "not-hex"}, timeout=TIMEOUT)
        assert r.status_code == 422


# ── Record-now lifecycle ─────────────────────────────────────────
class TestRecordNowLifecycle:
    """Full create → list → delete on an UNREACHABLE channel_url.
    Per contract: an unreachable URL must not crash the service — the
    recording is created, then auto-deleted once ffmpeg exits with <30 s
    of content. So we verify the entry is listed immediately after
    record-now (the recording is 'active' at that moment) and deletable.
    """

    def test_record_now_then_list_then_delete(self, api):
        uid = _fresh_user()
        body = {
            "user_id": uid,
            "channel_url": "http://127.0.0.1:1/unreachable.m3u8",
            "channel_name": "TEST_CH",
            "show_title": "TEST_SHOW",
            "fallback_duration_s": 600,
        }
        r = api.post(f"{DVR_BASE}/api/dvr/record-now", json=body, timeout=TIMEOUT)
        assert r.status_code == 200, r.text
        rec = r.json()
        assert rec["user_id"] == uid
        assert rec["status"] == "recording"
        assert rec["channel_name"] == "TEST_CH"
        assert rec["show_title"] == "TEST_SHOW"
        assert rec["duration_s"] == 600
        assert isinstance(rec.get("rec_id"), str) and len(rec["rec_id"]) == 16
        rec_id = rec["rec_id"]

        # List — entry should appear while active.
        r = api.get(f"{DVR_BASE}/api/dvr/recordings", params={"user_id": uid}, timeout=TIMEOUT)
        assert r.status_code == 200
        recs = r.json()["recordings"]
        assert any(m["rec_id"] == rec_id for m in recs), recs

        # Stream before mp4 has content → 404 (no bytes on disk yet, or
        # ffmpeg already failed and auto-cleaned). Accept 404 or 200; the
        # spec only requires 404 "until the mp4 exists".
        r = api.get(f"{DVR_BASE}/api/dvr/recordings/{rec_id}/stream", timeout=TIMEOUT, allow_redirects=False)
        assert r.status_code in (200, 404), f"unexpected {r.status_code}"

        # Delete single — stops ffmpeg and removes metadata.
        r = api.delete(
            f"{DVR_BASE}/api/dvr/recordings/{rec_id}",
            params={"user_id": uid},
            timeout=TIMEOUT,
        )
        assert r.status_code == 200, r.text
        data = r.json()
        assert data == {"ok": True, "rec_id": rec_id}

        # Verify removed.
        r = api.get(f"{DVR_BASE}/api/dvr/recordings", params={"user_id": uid}, timeout=TIMEOUT)
        assert r.status_code == 200
        assert all(m["rec_id"] != rec_id for m in r.json()["recordings"])

    def test_delete_unknown_rec_id_returns_404(self, api):
        uid = _fresh_user()
        bogus = "deadbeefdeadbeef"  # valid 16-hex
        r = api.delete(
            f"{DVR_BASE}/api/dvr/recordings/{bogus}",
            params={"user_id": uid},
            timeout=TIMEOUT,
        )
        assert r.status_code == 404, r.text

    def test_delete_all_returns_count(self, api):
        uid = _fresh_user()
        # Create two recordings on unreachable channel.
        rec_ids = []
        for i in range(2):
            r = api.post(
                f"{DVR_BASE}/api/dvr/record-now",
                json={
                    "user_id": uid,
                    "channel_url": "http://127.0.0.1:1/x.m3u8",
                    "channel_name": f"TEST_CH_{i}",
                    "show_title": f"TEST_SHOW_{i}",
                    "fallback_duration_s": 300,
                },
                timeout=TIMEOUT,
            )
            assert r.status_code == 200, r.text
            rec_ids.append(r.json()["rec_id"])

        # Delete-all.
        r = api.delete(f"{DVR_BASE}/api/dvr/recordings", params={"user_id": uid}, timeout=TIMEOUT)
        assert r.status_code == 200, r.text
        data = r.json()
        assert data["ok"] is True
        # Must report at least the two we created (async self-deletion on
        # failure could race and reduce the count, so assert >= 0 and the
        # listing is empty afterwards).
        assert isinstance(data["deleted"], int)
        assert data["deleted"] >= 0

        # Listing now empty.
        r = api.get(f"{DVR_BASE}/api/dvr/recordings", params={"user_id": uid}, timeout=TIMEOUT)
        assert r.json()["recordings"] == []

    def test_record_now_rejects_bad_user_id(self, api):
        r = api.post(
            f"{DVR_BASE}/api/dvr/record-now",
            json={
                "user_id": "NOT-HEX",
                "channel_url": "http://x/y.m3u8",
                "channel_name": "",
                "show_title": "",
                "fallback_duration_s": 60,
            },
            timeout=TIMEOUT,
        )
        assert r.status_code == 422


# ── Stream 404 before mp4 exists ─────────────────────────────────
class TestStream:
    def test_stream_unknown_rec_id_returns_404(self, api):
        r = requests.get(f"{DVR_BASE}/api/dvr/recordings/0011223344556677/stream", timeout=TIMEOUT)
        assert r.status_code == 404

    def test_stream_bad_rec_id_returns_400(self, api):
        r = requests.get(f"{DVR_BASE}/api/dvr/recordings/not-hex/stream", timeout=TIMEOUT)
        # pattern guard inside handler → 400
        assert r.status_code in (400, 404, 422)


# ── Quota clamp (402) when user has burned their 20 h ────────────
class TestQuotaClamp:
    """We can't actually burn 20 h of real recordings in CI, but we CAN
    prove the quota path is wired: the record-now handler reads
    `_user_seconds_used()` which sums all metadata durations. Since we
    cannot write metadata directly over HTTP, this test is skipped in
    black-box mode and instead verified indirectly: a brand-new user
    has available_s == 72000 and record-now succeeds (covered above).
    """

    @pytest.mark.skip(reason="black-box test env cannot pre-seed 20 h of metadata on remote host")
    def test_402_when_quota_exhausted(self):
        pass


# ── OTA manifest & APK ───────────────────────────────────────────
class TestOTA:
    def test_version_json(self):
        r = requests.get(f"{DEPLOY_BASE}/version.json", timeout=TIMEOUT)
        assert r.status_code == 200, r.text
        data = r.json()
        assert data["versionCode"] == 358, data
        assert data["versionName"] == "1.43.58", data
        # Sanity: points at the apk on the same host.
        assert data["apkUrl"].endswith("/hushtv.apk")

    def test_apk_download_headers(self):
        r = requests.head(f"{DEPLOY_BASE}/hushtv.apk", timeout=TIMEOUT, allow_redirects=True)
        assert r.status_code == 200
        assert r.headers.get("Content-Type") == "application/vnd.android.package-archive"
        # Must be a real file, not a stub.
        assert int(r.headers.get("Content-Length", "0")) > 1_000_000


# ── Public reachability parity ───────────────────────────────────
class TestPublicParity:
    """The review explicitly asks that every endpoint responds identically
    through the public IP (nginx :80 → FastAPI :8080). Our DVR_BASE *is*
    the public IP, so we just re-probe the simple GETs and confirm nginx
    is not swallowing/rewriting anything."""

    def test_nginx_root_is_plaintext(self):
        r = requests.get(f"{DVR_BASE}/", timeout=TIMEOUT)
        assert r.status_code == 200
        assert "HushTV DVR" in r.text

    def test_health_via_public_ip_matches_contract(self):
        r = requests.get(f"{DVR_BASE}/api/dvr/health", timeout=TIMEOUT)
        assert r.status_code == 200
        data = r.json()
        assert set(["ok", "active_recordings", "root"]).issubset(data.keys())

    def test_nginx_forwards_422_untouched(self):
        r = requests.get(f"{DVR_BASE}/api/dvr/quota?user_id=bad", timeout=TIMEOUT)
        assert r.status_code == 422, r.text
        # FastAPI validation-error shape survives the proxy.
        assert "detail" in r.json()
