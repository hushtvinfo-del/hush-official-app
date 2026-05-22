"""
Smoke tests for the v1.44.120 ApplicationExitInfo crash-monitor wiring.

Verifies:
  1. `_classify_kind` recognizes the new "exit_info" tag both from the
     explicit `kind` field AND from the `thread=exit-info` header in
     legacy reports.
  2. `_signature` produces a stable EXIT::<REASON>::<SIGNAL> grouping
     key for ApplicationExitInfo payloads.
  3. The `/recent` and `/top` endpoints accept `kind=exit_info` without
     400-ing on regex validation.
  4. The `/stats` totals include the new `exit_info` bucket.
"""
from __future__ import annotations

import json
import os
import shutil
import sys
import tempfile
from datetime import datetime, timezone

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import crash_monitor as cm  # noqa: E402


@pytest.fixture(autouse=True)
def _isolated_crash_dir(monkeypatch):
    """Point crash_monitor.CRASH_DIR at a tmpdir so we don't pollute
    /var/hushtv-crash and we don't pick up real crash reports."""
    tmp = tempfile.mkdtemp(prefix="hushtv-crash-test-")
    monkeypatch.setattr(cm, "CRASH_DIR", tmp)
    # Clear the in-process scan cache between tests.
    monkeypatch.setattr(cm, "_cache", {})
    # Bypass admin token gate.
    monkeypatch.setattr(cm, "ADMIN_TOKEN", "")
    yield tmp
    shutil.rmtree(tmp, ignore_errors=True)


def _write_report(crash_dir: str, day: str, fname: str, payload: dict) -> str:
    daydir = os.path.join(crash_dir, day)
    os.makedirs(daydir, exist_ok=True)
    path = os.path.join(daydir, fname)
    with open(path, "w") as f:
        json.dump(payload, f)
    return path


def test_classify_kind_recognizes_explicit_exit_info():
    assert cm._classify_kind({"kind": "exit_info", "trace": "anything"}) == "exit_info"


def test_classify_kind_recognizes_thread_header():
    """Future-proof against an upload that drops the kind field."""
    trace = (
        "===== HushTV 2026-02-20T01:23:45Z v1.44.120#520 thread=exit-info =====\n"
        "EXIT-INFO reason=CRASH_NATIVE\n"
        "status=11 (SIGSEGV)\n"
    )
    assert cm._classify_kind({"kind": "", "trace": trace}) == "exit_info"


def test_classify_kind_recognizes_boot_died():
    trace = (
        "===== HushTV 2026-02-21T01:23:45Z v1.44.121#521 thread=boot-died =====\n"
        "BOOT-DIED last_stage=main_setContent\n"
    )
    assert cm._classify_kind({"kind": "boot_died", "trace": trace}) == "boot_died"
    # And from header alone
    assert cm._classify_kind({"kind": "", "trace": trace}) == "boot_died"


def test_signature_boot_died_groups_by_stage():
    trace = (
        "===== HushTV 2026-02-21T01:23:45Z v1.44.121#521 thread=boot-died =====\n"
        "BOOT-DIED last_stage=main_setContent\n"
    )
    assert cm._signature(trace) == "BOOT::main_setContent"


def test_signature_native_crash():
    trace = (
        "===== HushTV 2026-02-20T01:23:45Z v1.44.120#520 thread=exit-info =====\n"
        "EXIT-INFO reason=CRASH_NATIVE\n"
        "status=11 (SIGSEGV)\n"
        "importance=FOREGROUND\n"
    )
    assert cm._signature(trace) == "EXIT::CRASH_NATIVE::SIGSEGV"


def test_signature_low_memory():
    trace = (
        "===== HushTV 2026-02-20T01:23:45Z v1.44.120#520 thread=exit-info =====\n"
        "EXIT-INFO reason=LOW_MEMORY\n"
        "status=0 (signal-0)\n"
    )
    assert cm._signature(trace) == "EXIT::LOW_MEMORY::signal-0"


def test_stats_includes_exit_info_bucket(_isolated_crash_dir):
    """Drop a fresh exit_info report and verify /stats picks it up
    under the new totals.exit_info key."""
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    captured = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    _write_report(_isolated_crash_dir, today, "010203-aaa-Test-Device.json", {
        "device": "Test-Device-12345678",
        "android_sdk": "30",
        "app_version": "1.44.120",
        "version_code": "520",
        "captured_at": captured,
        "installed_version": "1.44.120",
        "kind": "exit_info",
        "exit_reason": "CRASH_NATIVE",
        "exit_status": "11",
        "trace": (
            "===== HushTV " + captured + " v1.44.120#520 thread=exit-info =====\n"
            "EXIT-INFO reason=CRASH_NATIVE\n"
            "status=11 (SIGSEGV)\n"
        ),
    })
    out = cm.crash_stats(days=1, user="", x_admin_token=None)
    assert out["totals"]["exit_info"] == 1


def test_recent_endpoint_accepts_exit_info_kind():
    out = cm.crash_recent(days=1, kind="exit_info", limit=10, user="", x_admin_token=None)
    assert out["kind"] == "exit_info"
    assert out["rows"] == []


def test_top_endpoint_accepts_exit_info_kind():
    out = cm.crash_top(days=1, kind="exit_info", limit=10, user="", x_admin_token=None)
    assert out["kind"] == "exit_info"
    assert out["groups"] == []
