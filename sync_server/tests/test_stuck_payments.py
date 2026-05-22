"""v1.44.123 stuck-payments safety-net tests.

Coverage:
  • POST /api/canada/support/payment-ticket — happy path + idempotency
    + spoofing rejection.
  • GET  /api/admin/canada/stuck-payments — surfaces $50 / no_order_id
    rows AND open customer tickets; correctly EXCLUDES unknown_order
    rows (Base44 flow per user direction).
  • POST /api/admin/canada/stuck-payments/match — grants license,
    flips outcome to 'paid', auto-closes any open ticket for that
    order, rejects re-matching already-resolved emails, rejects
    wrong-amount emails.
  • _build_daily_drift_report — returns counts + subject reflecting
    the two queues.
"""
from __future__ import annotations

import os
import sys
import tempfile

# Point DB at a temp file BEFORE importing the module.
_tmp = tempfile.mkdtemp(prefix="canada_test_stuck_")
os.environ["HUSHSYNC_DB"] = os.path.join(_tmp, "sync.sqlite3")
os.environ["SPORTS_ADMIN_TOKEN"] = "test-admin-token"

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import canada_payment_module as cpm  # noqa: E402
from fastapi import FastAPI  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

cpm.ADMIN_TOKEN = "test-admin-token"

# Disable SMTP for the duration of the test suite — we don't want
# every test sending an email to support@hushtv.com.
cpm._smtp_send = lambda *_a, **_kw: {"sent": False, "error": "test-stub"}

app = FastAPI()
app.include_router(cpm.router)
app.include_router(cpm.admin_router)
client = TestClient(app)


def _create_order(user="testuser_stuck"):
    r = client.post("/api/canada/order/create", json={"xtream_username": user})
    assert r.status_code == 200, r.text
    return r.json()["order"]["order_id"]


def _seed_processed_email(uid: str, outcome: str, amount: float,
                          sender: str = "Some Customer have been auto") -> None:
    """Insert a row directly into canada_processed_emails for tests."""
    with cpm._conn() as c:
        c.execute(
            """INSERT INTO canada_processed_emails
               (uid, message_id, processed_at, order_id, amount, sender, outcome)
               VALUES (?, ?, ?, NULL, ?, ?, ?)""",
            (uid, f"<{uid}@test.local>", cpm._now_ms(),
             f"{amount:.2f}", sender, outcome),
        )


# ---------------- Customer ticket endpoint ----------------

def test_payment_ticket_happy_path():
    order_id = _create_order("ticketuser1")
    r = client.post(
        "/api/canada/support/payment-ticket",
        json={"xtream_username": "ticketuser1", "order_id": order_id,
              "interac_reference": "REF-123", "customer_note": "I sent it"},
    )
    assert r.status_code == 200, r.text
    j = r.json()
    assert j["ok"] is True
    assert j["ticket_id"].startswith("T")
    assert "deduplicated" not in j


def test_payment_ticket_idempotent_repeat():
    """Two POSTs for the same (user, order) within the open window
    must return the SAME ticket_id with deduplicated=True."""
    order_id = _create_order("ticketuser2")
    first = client.post(
        "/api/canada/support/payment-ticket",
        json={"xtream_username": "ticketuser2", "order_id": order_id},
    ).json()
    second = client.post(
        "/api/canada/support/payment-ticket",
        json={"xtream_username": "ticketuser2", "order_id": order_id},
    ).json()
    assert first["ticket_id"] == second["ticket_id"]
    assert second.get("deduplicated") is True


def test_payment_ticket_rejects_spoofed_order():
    """Order belongs to a different user → 404 (prevents abuse)."""
    order_id = _create_order("realowner")
    r = client.post(
        "/api/canada/support/payment-ticket",
        json={"xtream_username": "imposter", "order_id": order_id},
    )
    assert r.status_code == 404


def test_payment_ticket_rejects_unknown_order():
    r = client.post(
        "/api/canada/support/payment-ticket",
        json={"xtream_username": "ticketuser3", "order_id": "99999999"},
    )
    assert r.status_code == 404


# ---------------- Admin stuck-payments listing ----------------

def test_admin_stuck_payments_surfaces_unmatched_50_emails():
    _seed_processed_email("uid-50-stuck", "no_order_id", 50.00)
    r = client.get(
        "/api/admin/canada/stuck-payments",
        headers={"X-Admin-Token": "test-admin-token"},
    )
    assert r.status_code == 200
    j = r.json()
    assert j["stuck_emails_count"] >= 1
    uids = [e["uid"] for e in j["stuck_emails"]]
    assert "uid-50-stuck" in uids


def test_admin_stuck_payments_excludes_unknown_order_base44_flow():
    """Per direction: `unknown_order` rows belong to the Base44 initial
    purchase flow and must NOT appear in the stuck-payments panel."""
    _seed_processed_email("uid-base44-1", "unknown_order", 50.00)
    r = client.get(
        "/api/admin/canada/stuck-payments",
        headers={"X-Admin-Token": "test-admin-token"},
    )
    assert r.status_code == 200
    uids = [e["uid"] for e in r.json()["stuck_emails"]]
    assert "uid-base44-1" not in uids


def test_admin_stuck_payments_excludes_wrong_amount():
    """An email with amount=$100 (not $50) is not for our flow."""
    _seed_processed_email("uid-100-noid", "no_order_id", 100.00)
    r = client.get(
        "/api/admin/canada/stuck-payments",
        headers={"X-Admin-Token": "test-admin-token"},
    )
    uids = [e["uid"] for e in r.json()["stuck_emails"]]
    assert "uid-100-noid" not in uids


def test_admin_stuck_payments_lists_open_tickets():
    order_id = _create_order("tickettestlist")
    client.post(
        "/api/canada/support/payment-ticket",
        json={"xtream_username": "tickettestlist", "order_id": order_id},
    )
    r = client.get(
        "/api/admin/canada/stuck-payments",
        headers={"X-Admin-Token": "test-admin-token"},
    )
    j = r.json()
    assert j["open_tickets_count"] >= 1
    orders = [t["order_id"] for t in j["open_tickets"]]
    assert order_id in orders


def test_admin_stuck_payments_requires_admin_token():
    r = client.get("/api/admin/canada/stuck-payments")
    assert r.status_code in (401, 403, 422)


# ---------------- Admin manual-match endpoint ----------------

def test_admin_stuck_payment_match_grants_license_and_resolves_ticket():
    """End-to-end: seed a stuck $50 email + create a pending order +
    open a ticket, then admin clicks 'Match'. Must:
      (a) flip order to paid + grant license
      (b) flip the email outcome to 'paid' (disappears from stuck list)
      (c) auto-close the open ticket
    """
    user = "matchhappy"
    order_id = _create_order(user)
    _seed_processed_email("uid-match-1", "no_order_id", 50.00,
                          sender="Match Happy have been auto")
    client.post(
        "/api/canada/support/payment-ticket",
        json={"xtream_username": user, "order_id": order_id},
    )

    r = client.post(
        "/api/admin/canada/stuck-payments/match",
        headers={"X-Admin-Token": "test-admin-token"},
        json={"uid": "uid-match-1", "order_id": order_id, "actor": "test"},
    )
    assert r.status_code == 200, r.text
    j = r.json()
    assert j["ok"] is True
    assert j["xtream_username"] == user
    assert j["license"]["paid"] is True

    # Email outcome now 'paid' → stuck-payments list no longer contains it.
    r2 = client.get(
        "/api/admin/canada/stuck-payments",
        headers={"X-Admin-Token": "test-admin-token"},
    )
    uids = [e["uid"] for e in r2.json()["stuck_emails"]]
    assert "uid-match-1" not in uids

    # Open-ticket count for this order = 0.
    open_orders = [t["order_id"] for t in r2.json()["open_tickets"]]
    assert order_id not in open_orders


def test_admin_stuck_payment_match_rejects_wrong_amount():
    _seed_processed_email("uid-wrong-amt", "no_order_id", 25.00)
    order_id = _create_order("wrongamtuser")
    r = client.post(
        "/api/admin/canada/stuck-payments/match",
        headers={"X-Admin-Token": "test-admin-token"},
        json={"uid": "uid-wrong-amt", "order_id": order_id},
    )
    assert r.status_code == 409


def test_admin_stuck_payment_match_rejects_already_resolved():
    _seed_processed_email("uid-already-paid", "paid", 50.00)
    order_id = _create_order("alreadypaiduser")
    r = client.post(
        "/api/admin/canada/stuck-payments/match",
        headers={"X-Admin-Token": "test-admin-token"},
        json={"uid": "uid-already-paid", "order_id": order_id},
    )
    assert r.status_code == 409


# ---------------- Daily drift report builder ----------------

def test_build_daily_drift_report_subject_signals_clean_vs_dirty():
    """Drop a stuck email + an open ticket and verify the subject
    line reflects them."""
    _seed_processed_email("uid-drift-1", "no_order_id", 50.00)
    order_id = _create_order("driftuser")
    client.post(
        "/api/canada/support/payment-ticket",
        json={"xtream_username": "driftuser", "order_id": order_id},
    )
    subject, body, stats = cpm._build_daily_drift_report()
    assert stats["needs_action"] >= 1
    assert "stuck $50 emails" in subject
    assert "uid-drift-1" in body
    assert order_id in body
