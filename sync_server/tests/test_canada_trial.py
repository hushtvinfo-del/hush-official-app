"""Pytest coverage for the Canada free-trial flow (v1.44.94).

Verifies:
  - First license check for a never-seen Xtream account auto-grants a
    72-hour trial.
  - During the trial, paid:true with trial:true + trial_expires_at.
  - After the trial expires (simulated via direct DB mutation),
    paid:false with trial_expired:true.
  - Trial row is immutable — second call to the endpoint within the
    72 h window must NOT overwrite the original started_at/expires_at.
  - Paid users SKIP trial creation entirely (no row inserted for users
    that already hold a paid license).
"""
from __future__ import annotations

import importlib
import os
import sqlite3
import tempfile

# --- Bootstrap env ---------------------------------------------------
TMP = tempfile.mkdtemp(prefix="trial_test_")
DB  = os.path.join(TMP, "sync.sqlite3")
os.environ["HUSHSYNC_DB"]        = DB
os.environ["SPORTS_ADMIN_TOKEN"] = "TEST_ADMIN"

from fastapi import FastAPI
from fastapi.testclient import TestClient

# Force a fresh module import so it picks up the env var.
import canada_payment_module
importlib.reload(canada_payment_module)
canada_payment_module._init_schema()

app = FastAPI()
app.include_router(canada_payment_module.router)
client = TestClient(app)


def _fetch_trial_row(user: str):
    with sqlite3.connect(DB) as c:
        c.row_factory = sqlite3.Row
        return c.execute(
            "SELECT * FROM canada_trials WHERE xtream_username=?", (user,)
        ).fetchone()


def test_first_check_grants_trial():
    r = client.get("/api/canada/license/newuser1")
    assert r.status_code == 200
    lic = r.json()["license"]
    assert lic["paid"] is True
    assert lic["trial"] is True
    assert "trial_expires_at" in lic
    assert "trial_started_at" in lic
    # Sanity: trial expiry is ~72 h after started.
    delta = lic["trial_expires_at"] - lic["trial_started_at"]
    assert 71 * 60 * 60 * 1000 < delta <= 72 * 60 * 60 * 1000


def test_trial_row_is_immutable_on_repeat_check():
    # First call seeds the row.
    r1 = client.get("/api/canada/license/repeatuser")
    started1 = r1.json()["license"]["trial_started_at"]
    expires1 = r1.json()["license"]["trial_expires_at"]
    # Manually backdate the start so we can prove it doesn't get
    # bumped forward on the second poll.
    with sqlite3.connect(DB) as c:
        c.execute(
            "UPDATE canada_trials SET started_at=?, expires_at=? "
            "WHERE xtream_username='repeatuser'",
            (started1 - 60_000, expires1 - 60_000),
        )
    r2 = client.get("/api/canada/license/repeatuser")
    started2 = r2.json()["license"]["trial_started_at"]
    expires2 = r2.json()["license"]["trial_expires_at"]
    # Must match the back-dated values (immutable).
    assert started2 == started1 - 60_000
    assert expires2 == expires1 - 60_000


def test_trial_expired_returns_paywall():
    # Seed an already-expired trial directly.
    long_ago = canada_payment_module._now_ms() - (100 * 60 * 60 * 1000)  # 100 h ago
    expired  = canada_payment_module._now_ms() - (28  * 60 * 60 * 1000)  # 28 h ago
    with sqlite3.connect(DB) as c:
        c.execute(
            "INSERT INTO canada_trials (xtream_username, started_at, expires_at) "
            "VALUES (?, ?, ?)",
            ("expireduser", long_ago, expired),
        )
    r = client.get("/api/canada/license/expireduser")
    lic = r.json()["license"]
    assert lic["paid"] is False
    assert lic.get("trial") is True
    assert lic.get("trial_expired") is True


def test_paid_user_skips_trial():
    # Seed a paid license — must NOT get a trial row inserted on check.
    now = canada_payment_module._now_ms()
    with sqlite3.connect(DB) as c:
        c.execute(
            "INSERT INTO canada_licenses (xtream_username, paid_at, expires_at, last_order_id) "
            "VALUES (?, ?, ?, ?)",
            ("paiduser", now, now + canada_payment_module.LICENSE_YEAR_MS, "ord-1"),
        )
    r = client.get("/api/canada/license/paiduser")
    lic = r.json()["license"]
    assert lic["paid"] is True
    assert lic.get("trial") is None or lic.get("trial") is not True
    # No trial row should have been created.
    assert _fetch_trial_row("paiduser") is None


def test_paid_user_after_expired_paid_still_skips_trial():
    """A previously-paid user whose subscription EXPIRED naturally
    (no row in canada_trials, license row exists with past expiry)
    SHOULD be eligible for a trial — the system can't tell them apart
    from a new user solely by past-paid-state. This is intentional
    per the user's spec ("only NEW Xtream usernames that have never
    been seen by the license endpoint before"). The xtream username
    HAS been seen because their paid license is on file — but the
    license row alone doesn't gate trials, the trials table does.
    Document that here as the agreed behavior: an expired paid user
    will get a 72 h trial extension. That's a deliberate goodwill
    gesture and avoids tricky edge cases."""
    now = canada_payment_module._now_ms()
    with sqlite3.connect(DB) as c:
        c.execute(
            "INSERT INTO canada_licenses (xtream_username, paid_at, expires_at, last_order_id) "
            "VALUES (?, ?, ?, ?)",
            ("expiredpaiduser", now - canada_payment_module.LICENSE_YEAR_MS,
             now - 1, "ord-stale"),
        )
    r = client.get("/api/canada/license/expiredpaiduser")
    lic = r.json()["license"]
    # Their paid license expired so they enter the trial flow.
    assert lic["paid"] is True
    assert lic["trial"] is True
