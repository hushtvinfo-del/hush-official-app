"""HushTV admin panel — API integration tests."""
import os
import time
import uuid
import requests
import pytest

BASE_URL = os.environ.get("REACT_APP_BACKEND_URL", "https://tv-apk-build.preview.emergentagent.com").rstrip("/")
ADMIN_EMAIL = "admin@hushtv.xyz"
ADMIN_PASSWORD = "HushTV2026!"


@pytest.fixture(scope="module")
def admin_session():
    """Login session w/ httpOnly cookies."""
    s = requests.Session()
    # First, possibly clear lockouts via valid login
    r = s.post(f"{BASE_URL}/api/auth/login",
               json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD},
               timeout=20)
    if r.status_code == 429:
        # locked from previous run — wait briefly & retry; if still locked, skip dependent tests
        time.sleep(3)
        r = s.post(f"{BASE_URL}/api/auth/login",
                   json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD}, timeout=20)
    assert r.status_code == 200, f"login failed: {r.status_code} {r.text}"
    data = r.json()
    assert data["email"] == ADMIN_EMAIL
    assert data["role"] == "super_admin"
    return s


@pytest.fixture(scope="module")
def default_reseller(admin_session):
    r = admin_session.get(f"{BASE_URL}/api/admin/resellers", timeout=15)
    assert r.status_code == 200
    items = r.json()["items"]
    hush = next((x for x in items if x["slug"] == "hushtv"), None)
    assert hush is not None, "default HushTV reseller missing"
    return hush


# ── Auth ──────────────────────────────────────────────────────
class TestAuth:
    def test_login_success(self, admin_session):
        # session set up — re-call /me
        r = admin_session.get(f"{BASE_URL}/api/auth/me", timeout=15)
        assert r.status_code == 200
        u = r.json()
        assert u["email"] == ADMIN_EMAIL
        assert u["role"] == "super_admin"
        assert "id" in u
        assert u.get("reseller_id")

    def test_me_without_auth(self):
        r = requests.get(f"{BASE_URL}/api/auth/me", timeout=15)
        assert r.status_code == 401

    def test_resellers_without_auth(self):
        r = requests.get(f"{BASE_URL}/api/admin/resellers", timeout=15)
        assert r.status_code == 401

    def test_bad_password_401(self):
        # unique email so we don't trip the real admin lockout
        s = requests.Session()
        r = s.post(f"{BASE_URL}/api/auth/login",
                   json={"email": "nobody-xyz@hushtv.xyz", "password": "wrong"}, timeout=15)
        assert r.status_code == 401

    def test_brute_force_429(self):
        # use a dedicated bogus account so we don't lock the admin
        bogus = f"bf-{uuid.uuid4().hex[:8]}@hushtv.xyz"
        last = None
        for i in range(7):
            last = requests.post(f"{BASE_URL}/api/auth/login",
                                 json={"email": bogus, "password": "wrong"}, timeout=15)
        # After 5 fails, server returns 429
        assert last.status_code == 429, f"expected 429 after brute-force, got {last.status_code}"


# ── Summary / Resellers ───────────────────────────────────────
class TestSummaryAndResellers:
    def test_summary(self, admin_session):
        r = admin_session.get(f"{BASE_URL}/api/admin/summary", timeout=15)
        assert r.status_code == 200
        d = r.json()
        for k in ("online_devices", "total_devices", "blocked_devices", "broadcasts_24h", "total_resellers"):
            assert k in d, f"missing key {k}"

    def test_list_resellers_includes_default(self, default_reseller):
        assert default_reseller["slug"] == "hushtv"
        assert "activation_code" in default_reseller
        assert len(default_reseller["activation_code"]) == 6

    def test_create_reseller(self, admin_session):
        unique = f"ACME {uuid.uuid4().hex[:6]}"
        r = admin_session.post(f"{BASE_URL}/api/admin/resellers",
                               json={"display_name": unique,
                                     "owner_email": f"acme-{uuid.uuid4().hex[:6]}@x.com"},
                               timeout=15)
        assert r.status_code == 200, r.text
        rec = r.json()
        assert rec["display_name"] == unique
        assert "id" in rec and "slug" in rec and "activation_code" in rec
        assert len(rec["activation_code"]) == 6
        # persistence: GET resellers should now include it
        r2 = admin_session.get(f"{BASE_URL}/api/admin/resellers", timeout=15)
        assert any(x["id"] == rec["id"] for x in r2.json()["items"])

    def test_regenerate_code(self, admin_session, default_reseller):
        old = default_reseller["activation_code"]
        r = admin_session.post(
            f"{BASE_URL}/api/admin/resellers/{default_reseller['id']}/regenerate-code",
            timeout=15)
        assert r.status_code == 200
        new_code = r.json()["activation_code"]
        assert new_code and new_code != old


# ── Config patch + get ────────────────────────────────────────
class TestConfig:
    def test_patch_and_get_config(self, admin_session):
        patch = {
            "branding": {"app_name": "ACME TV", "accent_color": "#8B5CF6"},
            "feature_flags": {"hush_plus": False},
            "xtream_default": "iptv.acme.com:25461",
        }
        r = admin_session.patch(f"{BASE_URL}/api/admin/config", json=patch, timeout=15)
        assert r.status_code == 200, r.text
        r2 = admin_session.get(f"{BASE_URL}/api/admin/config", timeout=15)
        assert r2.status_code == 200
        cfg = r2.json()
        assert cfg["branding"]["app_name"] == "ACME TV"
        assert cfg["branding"]["accent_color"] == "#8B5CF6"
        assert cfg["feature_flags"]["hush_plus"] is False
        assert cfg["xtream_default"] == "iptv.acme.com:25461"


# ── Public heartbeat / messages ───────────────────────────────
class TestPublicAndDevices:
    def test_heartbeat_invalid_code_404(self):
        r = requests.post(f"{BASE_URL}/api/heartbeat",
                          json={"device_id": f"TEST-{uuid.uuid4().hex[:8]}",
                                "reseller_code": "XXXXXX"}, timeout=15)
        assert r.status_code == 404

    def test_full_device_broadcast_flow(self, admin_session, default_reseller):
        # Note: regen test may have changed activation code — refetch
        r = admin_session.get(f"{BASE_URL}/api/admin/resellers", timeout=15)
        hush = next(x for x in r.json()["items"] if x["slug"] == "hushtv")
        code = hush["activation_code"]

        device_id = f"TEST-DEV-{uuid.uuid4().hex[:10]}"
        # heartbeat creates device
        h = requests.post(f"{BASE_URL}/api/heartbeat",
                          json={"device_id": device_id, "reseller_code": code,
                                "model": "TestPixel", "app_version": "1.0.0"}, timeout=15)
        assert h.status_code == 200, h.text
        body = h.json()
        assert body["ok"] is True and "pending_messages" in body

        # device shows up in admin list
        d = admin_session.get(f"{BASE_URL}/api/admin/devices", timeout=15)
        assert d.status_code == 200
        items = d.json()["items"]
        mine = next((x for x in items if x["id"] == device_id), None)
        assert mine is not None
        assert mine["online"] is True

        # block / unblock
        b = admin_session.post(f"{BASE_URL}/api/admin/devices/{device_id}/block", timeout=15)
        assert b.status_code == 200
        d2 = admin_session.get(f"{BASE_URL}/api/admin/devices", timeout=15).json()["items"]
        assert next(x for x in d2 if x["id"] == device_id)["status"] == "blocked"
        u = admin_session.post(f"{BASE_URL}/api/admin/devices/{device_id}/unblock", timeout=15)
        assert u.status_code == 200
        d3 = admin_session.get(f"{BASE_URL}/api/admin/devices", timeout=15).json()["items"]
        assert next(x for x in d3 if x["id"] == device_id)["status"] == "active"

        # broadcast all
        bc = admin_session.post(f"{BASE_URL}/api/admin/broadcasts",
                                json={"target_type": "all", "title": "TEST BC",
                                      "body": "hello", "severity": "info"}, timeout=15)
        assert bc.status_code == 200, bc.text
        rec = bc.json()
        assert rec["stats"]["target_count"] >= 1

        # device pulls message
        p1 = requests.get(f"{BASE_URL}/api/messages/pending",
                          params={"device_id": device_id, "reseller_code": code},
                          timeout=15)
        assert p1.status_code == 200
        items = p1.json()["items"]
        assert len(items) >= 1
        delivery = items[0]
        assert delivery["title"] == "TEST BC"

        # second poll yields empty (delivered_at marked)
        p2 = requests.get(f"{BASE_URL}/api/messages/pending",
                          params={"device_id": device_id, "reseller_code": code},
                          timeout=15)
        assert p2.status_code == 200
        assert all(x["delivery_id"] != delivery["delivery_id"] for x in p2.json()["items"])

        # ack displayed
        ack = requests.post(f"{BASE_URL}/api/messages/{delivery['delivery_id']}/ack",
                            params={"state": "displayed"}, timeout=15)
        assert ack.status_code == 200

        # broadcast stats reflects displayed
        lst = admin_session.get(f"{BASE_URL}/api/admin/broadcasts", timeout=15).json()["items"]
        match = next((x for x in lst if x["id"] == rec["id"]), None)
        assert match is not None
        assert match["stats"]["displayed"] >= 1


# ── Audit log ─────────────────────────────────────────────────
class TestAudit:
    def test_audit_log_has_entries(self, admin_session):
        r = admin_session.get(f"{BASE_URL}/api/admin/audit-log", timeout=15)
        assert r.status_code == 200
        items = r.json()["items"]
        actions = {i["action"] for i in items}
        # At minimum we should have created a broadcast and blocked a device in this run
        assert "broadcast.create" in actions
        assert "device.block" in actions
