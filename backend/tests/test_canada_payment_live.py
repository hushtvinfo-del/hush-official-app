"""End-to-end live black-box tests for the HushTV Canada Interac payment gateway.

Targets the production sync_server hosted at https://hushtv.xyz/ (nginx ->
FastAPI on 66.163.113.147:5056). Covers:
  * /api/canada/health
  * /api/canada/order/create  (+ idempotency / re-use semantics)
  * /api/canada/order/status/{order_id}
  * /api/canada/license/{xtream_username}
  * /api/admin/canada/{grant, revoke, orders, licenses, poll}  with X-Admin-Token
  * Regression: /api/sports/home, /api/sync/health
"""
from __future__ import annotations

import os
import re
import time

import pytest
import requests

BASE_URL = "https://hushtv.xyz"
ADMIN_TOKEN = "b4010e5b9271baa489f3cbaabb2baf58f96c814146054a7f"
EXPECTED_AMOUNT = 40.0
EMAIL_TO = "Hushtv.info@gmail.com"

_TS = int(time.time())


def _unique_user(tag: str) -> str:
    return f"qa_{tag}_{_TS}_{os.getpid()}"


@pytest.fixture(scope="session")
def s() -> requests.Session:
    sess = requests.Session()
    sess.headers.update({"Content-Type": "application/json"})
    return sess


@pytest.fixture(scope="session")
def admin_headers() -> dict:
    return {"X-Admin-Token": ADMIN_TOKEN, "Content-Type": "application/json"}


# ─────────────────────────────── HEALTH ────────────────────────────────
class TestHealth:
    def test_canada_health(self, s):
        r = s.get(f"{BASE_URL}/api/canada/health", timeout=15)
        assert r.status_code == 200, r.text
        body = r.json()
        assert body.get("ok") is True
        assert body.get("gmail_pass_configured") is True, (
            "IMAP poller cannot authenticate without app password — "
            "Interac auto-deposit reconciliation is broken"
        )
        assert float(body.get("expected_amount_cad")) == EXPECTED_AMOUNT
        assert body.get("gmail_user", "").lower() == EMAIL_TO.lower()


# ───────────────────────── ORDER CREATE / STATUS ───────────────────────
class TestOrderCreate:
    def test_create_basic_shape(self, s):
        user = _unique_user("basic")
        r = s.post(
            f"{BASE_URL}/api/canada/order/create",
            json={"xtream_username": user},
            timeout=15,
        )
        assert r.status_code == 200, r.text
        body = r.json()
        assert body["amount_cad"] == EXPECTED_AMOUNT
        assert body["email_to"].lower() == EMAIL_TO.lower()
        order = body["order"]
        # 8-digit numeric order id
        assert re.fullmatch(r"\d{8}", order["order_id"]), order["order_id"]
        assert order["status"] == "pending"
        assert order["xtream_username"] == user.lower()
        assert isinstance(order["created_at"], int)
        assert isinstance(order["expires_at"], int)
        assert order["expires_at"] > order["created_at"]
        assert order["paid_at"] in (None, 0)
        assert body["reused"] is False

    def test_create_reuses_existing_pending_order(self, s):
        """Two creates for the same xtream_username back-to-back must
        re-use the same pending order (no Order ID inflation)."""
        user = _unique_user("reuse")
        r1 = s.post(
            f"{BASE_URL}/api/canada/order/create",
            json={"xtream_username": user},
            timeout=15,
        )
        assert r1.status_code == 200, r1.text
        oid1 = r1.json()["order"]["order_id"]

        r2 = s.post(
            f"{BASE_URL}/api/canada/order/create",
            json={"xtream_username": user},
            timeout=15,
        )
        assert r2.status_code == 200, r2.text
        body2 = r2.json()
        assert body2["reused"] is True
        assert body2["order"]["order_id"] == oid1, (
            "Duplicate create must re-use the existing pending Order ID"
        )

    def test_create_normalizes_username_case(self, s):
        user = _unique_user("CASE").upper()
        r1 = s.post(
            f"{BASE_URL}/api/canada/order/create",
            json={"xtream_username": user},
            timeout=15,
        )
        assert r1.status_code == 200
        # Server lowercases internally
        assert r1.json()["order"]["xtream_username"] == user.lower()
        # Second call with original-case must still re-use
        r2 = s.post(
            f"{BASE_URL}/api/canada/order/create",
            json={"xtream_username": user.lower()},
            timeout=15,
        )
        assert r2.status_code == 200
        assert r2.json()["reused"] is True
        assert r2.json()["order"]["order_id"] == r1.json()["order"]["order_id"]

    def test_create_rejects_empty_username(self, s):
        r = s.post(
            f"{BASE_URL}/api/canada/order/create",
            json={"xtream_username": ""},
            timeout=15,
        )
        # 400 (empty) or 422 (validation) both acceptable
        assert r.status_code in (400, 422), r.text


class TestOrderStatus:
    def test_status_for_known_order(self, s):
        user = _unique_user("status")
        r = s.post(
            f"{BASE_URL}/api/canada/order/create",
            json={"xtream_username": user},
            timeout=15,
        )
        oid = r.json()["order"]["order_id"]

        r2 = s.get(f"{BASE_URL}/api/canada/order/status/{oid}", timeout=15)
        assert r2.status_code == 200, r2.text
        body = r2.json()
        assert body["order"]["order_id"] == oid
        assert body["order"]["status"] == "pending"
        # No license yet for unpaid orders
        assert "license" not in body or body["license"].get("paid") in (False, None)

    def test_status_unknown_order_404(self, s):
        r = s.get(f"{BASE_URL}/api/canada/order/status/00000001", timeout=15)
        assert r.status_code == 404, r.text


# ─────────────────────────────── LICENSE ───────────────────────────────
class TestLicensePublic:
    def test_license_unpaid_user(self, s):
        user = _unique_user("noPay")
        r = s.get(f"{BASE_URL}/api/canada/license/{user}", timeout=15)
        assert r.status_code == 200, r.text
        body = r.json()
        assert body["xtream_username"] == user.lower()
        assert body["license"]["paid"] is False


# ─────────────────────────────── ADMIN ─────────────────────────────────
class TestAdminAuth:
    def test_grant_without_token_returns_401(self, s):
        r = s.post(
            f"{BASE_URL}/api/admin/canada/grant",
            json={"xtream_username": _unique_user("noTok"), "months": 12},
            timeout=15,
        )
        assert r.status_code == 401, r.text

    def test_grant_with_wrong_token_returns_401(self, s):
        r = s.post(
            f"{BASE_URL}/api/admin/canada/grant",
            headers={"X-Admin-Token": "wrong-token-xxx", "Content-Type": "application/json"},
            json={"xtream_username": _unique_user("badTok"), "months": 12},
            timeout=15,
        )
        assert r.status_code == 401, r.text

    def test_orders_without_token_returns_401(self, s):
        r = s.get(f"{BASE_URL}/api/admin/canada/orders", timeout=15)
        assert r.status_code == 401, r.text


class TestAdminGrantRevoke:
    USER = _unique_user("adminGR")

    def test_01_grant_12_months(self, s, admin_headers):
        r = s.post(
            f"{BASE_URL}/api/admin/canada/grant",
            headers=admin_headers,
            json={"xtream_username": self.USER, "months": 12},
            timeout=15,
        )
        assert r.status_code == 200, r.text
        body = r.json()
        assert body.get("granted") is True
        lic = body["license"]
        assert lic["paid"] is True
        # 12 months ≈ 365 days (allow ±3 day slack)
        assert 360 <= lic["days_remaining"] <= 370, lic
        assert lic["last_order_id"] == "ADMIN_GRANT"

    def test_02_license_lookup_after_grant(self, s):
        r = s.get(f"{BASE_URL}/api/canada/license/{self.USER}", timeout=15)
        assert r.status_code == 200, r.text
        lic = r.json()["license"]
        assert lic["paid"] is True
        assert 360 <= lic["days_remaining"] <= 370

    def test_03_create_order_for_already_licensed_user(self, s):
        """If the user already has an active license, /order/create must
        short-circuit with already_licensed:true rather than allocate
        a needless Order ID."""
        r = s.post(
            f"{BASE_URL}/api/canada/order/create",
            json={"xtream_username": self.USER},
            timeout=15,
        )
        assert r.status_code == 200, r.text
        body = r.json()
        assert body.get("already_licensed") is True, body
        assert body["license"]["paid"] is True

    def test_04_grant_extends_existing_license(self, s, admin_headers):
        """A second grant should ADD onto the existing expiry, not reset."""
        # Capture current expiry
        r0 = s.get(f"{BASE_URL}/api/canada/license/{self.USER}", timeout=15)
        before = r0.json()["license"]["expires_at"]

        r = s.post(
            f"{BASE_URL}/api/admin/canada/grant",
            headers=admin_headers,
            json={"xtream_username": self.USER, "months": 1},
            timeout=15,
        )
        assert r.status_code == 200, r.text
        after = r.json()["license"]["expires_at"]
        # 1 month ≈ 30.4 days  → ~2.63e9 ms
        delta_ms = after - before
        assert delta_ms > 25 * 24 * 60 * 60 * 1000, f"delta_ms={delta_ms} (too small)"
        assert delta_ms < 35 * 24 * 60 * 60 * 1000, f"delta_ms={delta_ms} (too large)"

    def test_05_revoke(self, s, admin_headers):
        r = s.post(
            f"{BASE_URL}/api/admin/canada/revoke",
            headers=admin_headers,
            json={"xtream_username": self.USER},
            timeout=15,
        )
        assert r.status_code == 200, r.text
        body = r.json()
        assert body.get("revoked") is True
        assert body.get("xtream_username") == self.USER.lower()

    def test_06_license_lookup_after_revoke(self, s):
        r = s.get(f"{BASE_URL}/api/canada/license/{self.USER}", timeout=15)
        assert r.status_code == 200, r.text
        assert r.json()["license"]["paid"] is False


class TestAdminListing:
    def test_list_orders_includes_recent_qa_orders(self, s, admin_headers):
        # Seed an order that should show up at the top
        user = _unique_user("listOrd")
        c = s.post(
            f"{BASE_URL}/api/canada/order/create",
            json={"xtream_username": user},
            timeout=15,
        )
        assert c.status_code == 200
        oid = c.json()["order"]["order_id"]

        r = s.get(
            f"{BASE_URL}/api/admin/canada/orders?limit=100",
            headers=admin_headers,
            timeout=15,
        )
        assert r.status_code == 200, r.text
        body = r.json()
        assert "orders" in body
        assert isinstance(body["orders"], list)
        assert len(body["orders"]) >= 1
        ids = {o["order_id"] for o in body["orders"]}
        assert oid in ids, f"freshly-created order {oid} missing from admin/orders"

    def test_list_licenses(self, s, admin_headers):
        # Make sure at least one license exists
        user = _unique_user("listLic")
        s.post(
            f"{BASE_URL}/api/admin/canada/grant",
            headers=admin_headers,
            json={"xtream_username": user, "months": 1},
            timeout=15,
        )
        r = s.get(
            f"{BASE_URL}/api/admin/canada/licenses?limit=500",
            headers=admin_headers,
            timeout=15,
        )
        assert r.status_code == 200, r.text
        body = r.json()
        assert "licenses" in body
        assert isinstance(body["licenses"], list)
        users = {lic["xtream_username"] for lic in body["licenses"]}
        assert user.lower() in users
        # cleanup so we don't leave forever-licenses kicking around
        s.post(
            f"{BASE_URL}/api/admin/canada/revoke",
            headers=admin_headers,
            json={"xtream_username": user},
            timeout=15,
        )


class TestAdminForcePoll:
    def test_force_poll_imap_round_trip(self, s, admin_headers):
        """Validates IMAP credentials + connectivity end-to-end."""
        r = s.post(
            f"{BASE_URL}/api/admin/canada/poll",
            headers=admin_headers,
            timeout=60,
        )
        assert r.status_code == 200, r.text
        body = r.json()
        assert "scan" in body, body
        scan = body["scan"]
        for k in ("checked", "matched", "errors"):
            assert k in scan, f"missing key {k} in scan summary: {scan}"
            assert isinstance(scan[k], int)
        # If IMAP login itself failed, the implementation increments `errors`
        # but more importantly cannot LIST inbox, so checked would be 0 AND
        # errors > 0. We only fail loudly if errors dominates.
        # (A real Gmail with no recent Interac mail returns checked=0,errors=0.)
        assert scan["errors"] == 0 or scan["checked"] > 0, (
            f"Suspicious scan summary, likely IMAP auth failure: {scan}"
        )


# ─────────────────── REGRESSION (other routers still alive) ──────────
class TestRegression:
    def test_sports_home(self, s):
        r = s.get(f"{BASE_URL}/api/sports/home", timeout=20)
        assert r.status_code == 200, r.text

    def test_sync_health(self, s):
        r = s.get(f"{BASE_URL}/api/sync/health", timeout=15)
        assert r.status_code == 200, r.text


# ─────────────────── Session-level cleanup of QA licenses ────────────
@pytest.fixture(scope="session", autouse=True)
def _qa_cleanup(admin_headers):
    """Revoke every qa_*_<TS>_<PID>_* license we created during the run."""
    yield
    try:
        r = requests.get(
            f"{BASE_URL}/api/admin/canada/licenses?limit=2000",
            headers=admin_headers,
            timeout=20,
        )
        if r.status_code != 200:
            return
        for lic in r.json().get("licenses", []):
            uname = lic.get("xtream_username", "")
            if uname.startswith(f"qa_") and str(_TS) in uname:
                requests.post(
                    f"{BASE_URL}/api/admin/canada/revoke",
                    headers=admin_headers,
                    json={"xtream_username": uname},
                    timeout=10,
                )
    except Exception:
        pass
