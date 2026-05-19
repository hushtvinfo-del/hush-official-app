"""End-to-end tests for the Canada payment FastAPI endpoints.

Uses a temp SQLite DB and the FastAPI TestClient. Does NOT touch IMAP.
"""
from __future__ import annotations

import os
import sys
import tempfile
import time

# Point DB at a temp file BEFORE importing the module.
_tmp = tempfile.mkdtemp(prefix="canada_test_")
os.environ["HUSHSYNC_DB"] = os.path.join(_tmp, "sync.sqlite3")
os.environ["SPORTS_ADMIN_TOKEN"] = "test-admin-token"

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import canada_payment_module as cpm  # noqa: E402
from fastapi import FastAPI  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

cpm.ADMIN_TOKEN = "test-admin-token"

app = FastAPI()
app.include_router(cpm.router)
app.include_router(cpm.admin_router)
client = TestClient(app)


def _create_order(user="testuser1"):
    r = client.post("/api/canada/order/create", json={"xtream_username": user})
    assert r.status_code == 200, r.text
    return r.json()


def test_health():
    r = client.get("/api/canada/health")
    assert r.status_code == 200
    j = r.json()
    assert j["ok"] is True
    assert j["expected_amount_cad"] == cpm.EXPECTED_AMOUNT_CAD


def test_create_order_basic():
    j = _create_order("alice")
    assert "order" in j
    oid = j["order"]["order_id"]
    assert oid.isdigit() and len(oid) == 8
    assert j["order"]["status"] == "pending"
    assert j["amount_cad"] == cpm.EXPECTED_AMOUNT_CAD
    assert j["email_to"]


def test_create_order_reused_when_pending():
    j1 = _create_order("bob")
    j2 = _create_order("bob")
    assert j1["order"]["order_id"] == j2["order"]["order_id"]
    assert j2["reused"] is True


def test_order_status_pending_then_unknown():
    j = _create_order("carol")
    oid = j["order"]["order_id"]
    s = client.get(f"/api/canada/order/status/{oid}")
    assert s.status_code == 200
    assert s.json()["order"]["status"] == "pending"
    miss = client.get("/api/canada/order/status/00000000")
    assert miss.status_code == 404


def test_license_unpaid_then_paid_via_email_match():
    j = _create_order("dave")
    oid = j["order"]["order_id"]
    # license = trial (auto-granted by v1.44.94). Before the Interac
    # email is matched, the user has not actually paid — but they
    # are auto-enrolled in the 72 h free trial. The endpoint
    # surfaces this as paid:true + trial:true.
    lic = client.get("/api/canada/license/dave").json()
    assert lic["license"]["paid"] is True
    assert lic["license"]["trial"] is True

    # Simulate the IMAP poller receiving an Interac email matching this order.
    fake_html = f"""<html><body>
        Amount: $50.00 (CAD)<br>
        Sent From: Dave Tester<br>
        Message: {oid}
    </body></html>"""
    raw = (b"From: notify@payments.interac.ca\r\n"
           b"To: hushtv.info@gmail.com\r\n"
           b"Subject: INTERAC e-Transfer: Auto-Deposit\r\n"
           b"Content-Type: text/html; charset=utf-8\r\n\r\n"
           + fake_html.encode("utf-8"))
    with cpm._conn() as c:
        outcome = cpm._process_single_email(c, "test-uid-1", raw)
    assert outcome == "paid"

    s = client.get(f"/api/canada/order/status/{oid}").json()
    assert s["order"]["status"] == "paid"
    assert s["license"]["paid"] is True
    assert s["license"]["days_remaining"] >= 360

    lic2 = client.get("/api/canada/license/dave").json()
    assert lic2["license"]["paid"] is True


def test_email_with_low_amount_rejected():
    j = _create_order("eve")
    oid = j["order"]["order_id"]
    low = cpm.EXPECTED_AMOUNT_CAD / 2.0  # half the expected → must be rejected
    fake_html = f"Amount: ${low:.2f} (CAD) Message: {oid}".encode("utf-8")
    raw = b"From: notify@payments.interac.ca\r\nContent-Type: text/html\r\n\r\n" + fake_html
    with cpm._conn() as c:
        outcome = cpm._process_single_email(c, "test-uid-2", raw)
    assert outcome == "amount_too_low"
    s = client.get(f"/api/canada/order/status/{oid}").json()
    assert s["order"]["status"] == "pending"


def test_email_not_from_interac_skipped():
    j = _create_order("frank")
    oid = j["order"]["order_id"]
    raw = (b"From: scammer@example.com\r\nContent-Type: text/html\r\n\r\n"
           + f"Amount: $50.00 (CAD) Message: {oid}".encode())
    with cpm._conn() as c:
        outcome = cpm._process_single_email(c, "test-uid-3", raw)
    assert outcome == "skipped_not_interac"


def test_email_unknown_order_id():
    raw = (b"From: notify@payments.interac.ca\r\nContent-Type: text/html\r\n\r\n"
           b"Amount: $50.00 (CAD) Message: 00000001")
    with cpm._conn() as c:
        outcome = cpm._process_single_email(c, "test-uid-4", raw)
    assert outcome == "unknown_order"


def test_admin_endpoints_require_token():
    r = client.post("/api/admin/canada/grant",
                    json={"xtream_username": "x", "months": 12})
    assert r.status_code == 401
    r2 = client.post("/api/admin/canada/grant",
                     json={"xtream_username": "manualgrant", "months": 12},
                     headers={"X-Admin-Token": "test-admin-token"})
    assert r2.status_code == 200
    j = r2.json()
    assert j["granted"] is True
    assert j["license"]["paid"] is True
    assert j["license"]["days_remaining"] >= 360

    rev = client.post("/api/admin/canada/revoke",
                      json={"xtream_username": "manualgrant"},
                      headers={"X-Admin-Token": "test-admin-token"})
    assert rev.status_code == 200
    lic = client.get("/api/canada/license/manualgrant").json()
    # v1.44.94 — Revoked users SHOULD still see paid:true / trial:true
    # because revoke wipes their paid license but doesn't pre-emptively
    # write a trial row. The very next license check auto-grants the
    # 72 h trial. We deliberately don't try to detect "this user had
    # a paid license once" — it leads to confusing edge cases. The
    # admin can manually wipe the trial row from canada_trials if
    # they want a hard ban.
    assert lic["license"]["paid"] is True
    assert lic["license"]["trial"] is True


def test_already_licensed_short_circuits_order():
    # grant first
    client.post("/api/admin/canada/grant",
                json={"xtream_username": "earlyadopter", "months": 12},
                headers={"X-Admin-Token": "test-admin-token"})
    j = _create_order("earlyadopter")
    assert j.get("already_licensed") is True


def test_force_new_creates_renewal_order_for_licensed_user():
    # grant first
    client.post("/api/admin/canada/grant",
                json={"xtream_username": "renewaltester", "months": 12},
                headers={"X-Admin-Token": "test-admin-token"})
    r = client.post("/api/canada/order/create",
                    json={"xtream_username": "renewaltester", "force_new": True})
    assert r.status_code == 200
    j = r.json()
    assert j.get("already_licensed") is not True
    assert "order" in j
    assert j["order"]["status"] == "pending"


def test_double_email_processing_is_idempotent():
    j = _create_order("greta")
    oid = j["order"]["order_id"]
    raw = (b"From: notify@payments.interac.ca\r\nContent-Type: text/html\r\n\r\n"
           + f"Amount: $50.00 (CAD) Message: {oid}".encode())
    with cpm._conn() as c:
        out1 = cpm._process_single_email(c, "dup-uid", raw)
        out2 = cpm._process_single_email(c, "dup-uid-2", raw)
    assert out1 == "paid"
    assert out2 == "already_paid"


def test_order_expiry():
    # backdate an order then verify it's marked expired by the next read.
    j = _create_order("hilda")
    oid = j["order"]["order_id"]
    long_ago = int(time.time() * 1000) - 999_999_999
    with cpm._conn() as c:
        c.execute("UPDATE canada_orders SET expires_at=? WHERE order_id=?", (long_ago, oid))
    s = client.get(f"/api/canada/order/status/{oid}").json()
    assert s["order"]["status"] == "expired"


if __name__ == "__main__":
    import subprocess
    sys.exit(subprocess.call(["pytest", "-q", __file__]))
