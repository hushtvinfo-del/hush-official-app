"""HushTV Admin Panel — backend.

A multi-tenant admin API for the HushTV Android TV / Mobile app.

Tenancy model
-------------
* Every business object (device, broadcast, config row, audit-log
  entry, …) carries a `reseller_id` foreign key.
* JWTs encode `role` ∈ {"super_admin", "reseller_admin"} and
  `reseller_id` (the user's primary tenant).
* Super-admins can read/write any reseller's data; reseller-admins
  are scoped to their own.

Why a single fat server.py?
--------------------------
The admin surface is small enough that splitting into 12 files
hides the request flow more than it helps. We keep concerns
separated by SECTION comments (Auth ▸ Resellers ▸ Devices ▸
Broadcasts ▸ Config ▸ Audit ▸ Public Android API). When this
file grows past ~1500 lines we'll break it apart.
"""
from __future__ import annotations

# ── env first (load_dotenv timing matters for bcrypt + JWT keys) ──
from dotenv import load_dotenv
from pathlib import Path
load_dotenv(Path(__file__).parent / '.env')

import asyncio
import logging
import os
import secrets
import string
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any, Literal

import bcrypt
import httpx
import jwt
from fastapi import (APIRouter, Depends, FastAPI, HTTPException, Query,
                     Request, Response)
from motor.motor_asyncio import AsyncIOMotorClient
from pydantic import BaseModel, ConfigDict, EmailStr, Field
from starlette.middleware.cors import CORSMiddleware

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
)
log = logging.getLogger("hushtv-admin")

# ────────────────────────────────────────────────────────────────
# Database
# ────────────────────────────────────────────────────────────────
mongo = AsyncIOMotorClient(os.environ['MONGO_URL'])
db = mongo[os.environ['DB_NAME']]

JWT_SECRET = os.environ['JWT_SECRET']
JWT_ALGO = "HS256"
ACCESS_TTL_MIN = 60 * 12   # 12 hours — admins log in for a session
REFRESH_TTL_DAYS = 7


# ────────────────────────────────────────────────────────────────
# Helpers — passwords, JWT, IDs
# ────────────────────────────────────────────────────────────────
def hash_password(pw: str) -> str:
    return bcrypt.hashpw(pw.encode(), bcrypt.gensalt()).decode()


def verify_password(pw: str, hashed: str) -> bool:
    return bcrypt.checkpw(pw.encode(), hashed.encode())


def now() -> datetime:
    return datetime.now(timezone.utc)


def encode_jwt(payload: dict, ttl: timedelta) -> str:
    body = {**payload, "exp": now() + ttl, "iat": now()}
    return jwt.encode(body, JWT_SECRET, algorithm=JWT_ALGO)


def decode_jwt(token: str) -> dict:
    return jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGO])


def make_id() -> str:
    return str(uuid.uuid4())


def make_slug(text: str) -> str:
    """Slug from display name. Falls back to a 6-char random id
    if the input is too short to make a usable slug."""
    cleaned = ''.join(
        c.lower() if c.isalnum() else '-' for c in text.strip()
    ).strip('-')
    cleaned = '-'.join(filter(None, cleaned.split('-')))
    if len(cleaned) < 3:
        cleaned = 'r' + ''.join(
            secrets.choice(string.ascii_lowercase + string.digits)
            for _ in range(6)
        )
    return cleaned[:48]


def make_activation_code() -> str:
    """Short human-readable code the user types into the Android
    app's activation screen on first launch. 6 chars, no
    ambiguous glyphs (no 0/O/1/I/L)."""
    alphabet = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    return ''.join(secrets.choice(alphabet) for _ in range(6))


# ────────────────────────────────────────────────────────────────
# Pydantic models
# ────────────────────────────────────────────────────────────────
class _Base(BaseModel):
    model_config = ConfigDict(extra="ignore")


class LoginIn(_Base):
    email: EmailStr
    password: str


class AdminUserOut(_Base):
    id: str
    email: EmailStr
    name: str
    role: Literal["super_admin", "reseller_admin"]
    reseller_id: str | None = None
    created_at: datetime


class ResellerCreateIn(_Base):
    display_name: str = Field(min_length=2, max_length=64)
    owner_email: EmailStr
    plan_tier: str = "standard"


class ResellerOut(_Base):
    id: str
    slug: str
    display_name: str
    owner_email: EmailStr
    activation_code: str
    plan_tier: str
    status: Literal["active", "suspended"]
    device_count: int = 0
    created_at: datetime


class BrandingPatch(_Base):
    logo_url: str | None = None
    splash_text: str | None = None
    accent_color: str | None = None
    app_name: str | None = None


class FeatureFlagsPatch(_Base):
    hush_plus: bool | None = None
    requests: bool | None = None
    search: bool | None = None
    epg: bool | None = None
    pip: bool | None = None


class ConfigPatch(_Base):
    branding: BrandingPatch | None = None
    feature_flags: FeatureFlagsPatch | None = None
    xtream_default: str | None = None
    min_app_version: str | None = None
    maintenance_mode: bool | None = None
    maintenance_message: str | None = None


class HeartbeatIn(_Base):
    device_id: str = Field(min_length=4, max_length=128)
    reseller_code: str
    model: str | None = None
    os_version: str | None = None
    app_version: str | None = None
    last_screen: str | None = None


class BroadcastCreateIn(_Base):
    target_type: Literal["all", "device", "group"] = "all"
    target_device_id: str | None = None
    target_filter: dict | None = None
    title: str = Field(min_length=1, max_length=120)
    body: str = Field(min_length=1, max_length=2000)
    severity: Literal["info", "warning", "critical"] = "info"
    scheduled_at: datetime | None = None


class BroadcastOut(_Base):
    id: str
    reseller_id: str
    target_type: str
    target_filter: dict | None = None
    title: str
    body: str
    severity: str
    scheduled_at: datetime | None = None
    sent_at: datetime | None = None
    stats: dict
    created_at: datetime


class ActivateIn(_Base):
    activation_code: str


class PublicConfigOut(_Base):
    """Subset of reseller config the Android app needs at boot."""
    reseller_id: str
    reseller_slug: str
    app_name: str
    branding: dict
    feature_flags: dict
    xtream_default: str | None
    min_app_version: str | None
    maintenance_mode: bool
    maintenance_message: str | None


# ────────────────────────────────────────────────────────────────
# Auth dependency
# ────────────────────────────────────────────────────────────────
async def current_admin(request: Request) -> dict:
    """Resolve the currently-authenticated admin user.

    Token is read from `access_token` httpOnly cookie first (the
    web admin's preferred channel); falls back to
    `Authorization: Bearer <token>` so curl + tools work. Returns
    a dict with id, email, name, role, reseller_id.
    """
    token = request.cookies.get("access_token")
    if not token:
        h = request.headers.get("Authorization", "")
        if h.startswith("Bearer "):
            token = h[7:]
    if not token:
        raise HTTPException(401, "Not authenticated")
    try:
        payload = decode_jwt(token)
        if payload.get("type") != "access":
            raise HTTPException(401, "Wrong token type")
    except jwt.ExpiredSignatureError:
        raise HTTPException(401, "Token expired")
    except jwt.InvalidTokenError:
        raise HTTPException(401, "Invalid token")

    user = await db.admin_users.find_one(
        {"id": payload["sub"]}, {"_id": 0, "password_hash": 0},
    )
    if not user:
        raise HTTPException(401, "User not found")
    return user


def require_super(user: dict = Depends(current_admin)) -> dict:
    """Raises 403 unless the caller is a super-admin."""
    if user["role"] != "super_admin":
        raise HTTPException(403, "Super-admin only")
    return user


def scope_reseller(user: dict, reseller_id: str | None) -> str:
    """Decide which `reseller_id` an admin's request should
    operate on.

    * Reseller-admins are LOCKED to their own reseller no matter
      what they pass in the URL/body.
    * Super-admins can target any reseller; if they don't pass
      one we operate on the reseller they're currently "viewing"
      (their own assigned reseller_id) — convenient for testing.
    """
    if user["role"] == "reseller_admin":
        return user["reseller_id"]
    return reseller_id or user["reseller_id"]


# ────────────────────────────────────────────────────────────────
# Audit log helper
# ────────────────────────────────────────────────────────────────
async def audit(
    *, actor: dict, action: str, target: str = "",
    reseller_id: str | None = None, payload: dict | None = None,
) -> None:
    """Append-only record of every administrator action. Best-
    effort: failures are logged but never block the originating
    request — auditing should never wedge a real operation."""
    try:
        await db.audit_log.insert_one({
            "id": make_id(),
            "reseller_id": reseller_id or actor.get("reseller_id"),
            "actor_id": actor["id"],
            "actor_email": actor["email"],
            "action": action,
            "target": target,
            "payload": payload or {},
            "created_at": now().isoformat(),
        })
    except Exception as exc:  # pragma: no cover
        log.warning("audit failed: %s", exc)


# ────────────────────────────────────────────────────────────────
# FastAPI app
# ────────────────────────────────────────────────────────────────
app = FastAPI(title="HushTV Admin Panel")
api = APIRouter(prefix="/api")


# ── Auth ───────────────────────────────────────────────────────
@api.post("/auth/login")
async def auth_login(body: LoginIn, response: Response):
    """Authenticate an admin. Sets httpOnly access + refresh
    cookies on success. Brute-force protection is enforced via
    `login_attempts` (5 fails → 15 min lockout)."""
    email = body.email.lower().strip()
    key = email  # simple key — IP not always available behind ingress

    attempt = await db.login_attempts.find_one({"key": key}) or {}
    if attempt.get("count", 0) >= 5 and attempt.get("locked_until"):
        if datetime.fromisoformat(attempt["locked_until"]) > now():
            raise HTTPException(429, "Too many failed attempts. Try again later.")

    user = await db.admin_users.find_one({"email": email})
    if not user or not verify_password(body.password, user["password_hash"]):
        # Increment fail counter.
        new_count = attempt.get("count", 0) + 1
        update = {"count": new_count, "last_failed_at": now().isoformat()}
        if new_count >= 5:
            update["locked_until"] = (now() + timedelta(minutes=15)).isoformat()
        await db.login_attempts.update_one(
            {"key": key},
            {"$set": {"key": key, **update}},
            upsert=True,
        )
        raise HTTPException(401, "Invalid email or password")

    # Success — clear attempts and issue tokens.
    await db.login_attempts.delete_one({"key": key})

    access = encode_jwt(
        {"sub": user["id"], "role": user["role"],
         "reseller_id": user.get("reseller_id"), "type": "access"},
        timedelta(minutes=ACCESS_TTL_MIN),
    )
    refresh = encode_jwt(
        {"sub": user["id"], "type": "refresh"},
        timedelta(days=REFRESH_TTL_DAYS),
    )
    response.set_cookie(
        "access_token", access, httponly=True, secure=True,
        samesite="lax", max_age=ACCESS_TTL_MIN * 60, path="/",
    )
    response.set_cookie(
        "refresh_token", refresh, httponly=True, secure=True,
        samesite="lax", max_age=REFRESH_TTL_DAYS * 86400, path="/",
    )
    user.pop("password_hash", None)
    user.pop("_id", None)
    return user


@api.post("/auth/logout")
async def auth_logout(response: Response):
    response.delete_cookie("access_token", path="/")
    response.delete_cookie("refresh_token", path="/")
    return {"ok": True}


@api.get("/auth/me")
async def auth_me(user: dict = Depends(current_admin)):
    return user


# ── Resellers ──────────────────────────────────────────────────
@api.get("/admin/resellers")
async def list_resellers(user: dict = Depends(require_super)):
    """Super-admin only — list every reseller plus their device
    count for the dashboard."""
    items = []
    async for r in db.resellers.find({}, {"_id": 0}):
        r["device_count"] = await db.devices.count_documents(
            {"reseller_id": r["id"]}
        )
        items.append(r)
    return {"items": items}


@api.post("/admin/resellers")
async def create_reseller(
    body: ResellerCreateIn,
    user: dict = Depends(require_super),
):
    slug = make_slug(body.display_name)
    # Avoid slug collisions.
    while await db.resellers.find_one({"slug": slug}):
        slug = make_slug(body.display_name) + "-" + secrets.token_hex(2)

    activation_code = make_activation_code()
    while await db.resellers.find_one({"activation_code": activation_code}):
        activation_code = make_activation_code()

    rec = {
        "id": make_id(),
        "slug": slug,
        "display_name": body.display_name,
        "owner_email": body.owner_email,
        "activation_code": activation_code,
        "plan_tier": body.plan_tier,
        "status": "active",
        "created_at": now().isoformat(),
    }
    await db.resellers.insert_one(rec)
    rec.pop("_id", None)  # Mongo mutates rec to add ObjectId; strip before reuse.
    # Seed a default config for the new reseller.
    await db.reseller_configs.insert_one({
        "reseller_id": rec["id"],
        "branding": _default_branding(rec["display_name"]),
        "feature_flags": _default_feature_flags(),
        "xtream_default": "",
        "min_app_version": "1.42.95",
        "maintenance_mode": False,
        "maintenance_message": "",
        "updated_at": now().isoformat(),
    })
    await audit(actor=user, action="reseller.create",
                target=rec["id"], reseller_id=rec["id"], payload=rec)
    rec["device_count"] = 0
    return rec


@api.patch("/admin/resellers/{reseller_id}")
async def patch_reseller(
    reseller_id: str,
    body: dict,
    user: dict = Depends(require_super),
):
    allowed = {"display_name", "owner_email", "plan_tier", "status"}
    update = {k: v for k, v in body.items() if k in allowed}
    if not update:
        raise HTTPException(400, "No allowed fields supplied")
    await db.resellers.update_one(
        {"id": reseller_id}, {"$set": update}
    )
    await audit(actor=user, action="reseller.patch",
                target=reseller_id, reseller_id=reseller_id, payload=update)
    return {"ok": True}


@api.post("/admin/resellers/{reseller_id}/regenerate-code")
async def regenerate_code(
    reseller_id: str, user: dict = Depends(require_super),
):
    code = make_activation_code()
    while await db.resellers.find_one({"activation_code": code}):
        code = make_activation_code()
    await db.resellers.update_one(
        {"id": reseller_id}, {"$set": {"activation_code": code}}
    )
    await audit(actor=user, action="reseller.regenerate_code",
                target=reseller_id, reseller_id=reseller_id)
    return {"activation_code": code}


# ── Reseller config ────────────────────────────────────────────
def _default_branding(name: str) -> dict:
    return {
        "logo_url": "",
        "splash_text": name,
        "accent_color": "#06B6D4",
        "app_name": name,
    }


def _default_feature_flags() -> dict:
    return {
        "hush_plus": True,
        "requests": True,
        "search": True,
        "epg": True,
        "pip": False,
    }


@api.get("/admin/config")
async def get_config(
    reseller_id: str | None = Query(default=None),
    user: dict = Depends(current_admin),
):
    rid = scope_reseller(user, reseller_id)
    cfg = await db.reseller_configs.find_one(
        {"reseller_id": rid}, {"_id": 0},
    )
    if not cfg:
        raise HTTPException(404, "Reseller config not found")
    return cfg


@api.patch("/admin/config")
async def patch_config(
    body: ConfigPatch,
    reseller_id: str | None = Query(default=None),
    user: dict = Depends(current_admin),
):
    rid = scope_reseller(user, reseller_id)
    cfg = await db.reseller_configs.find_one({"reseller_id": rid})
    if not cfg:
        raise HTTPException(404, "Reseller config not found")
    update: dict[str, Any] = {"updated_at": now().isoformat()}
    # Patch branding piece-wise (so missing fields don't wipe).
    if body.branding:
        merged = {**cfg.get("branding", _default_branding("HushTV"))}
        for k, v in body.branding.model_dump(exclude_unset=True).items():
            if v is not None:
                merged[k] = v
        update["branding"] = merged
    if body.feature_flags:
        merged_ff = {**cfg.get("feature_flags", _default_feature_flags())}
        for k, v in body.feature_flags.model_dump(exclude_unset=True).items():
            if v is not None:
                merged_ff[k] = v
        update["feature_flags"] = merged_ff
    for plain_key in (
        "xtream_default", "min_app_version", "maintenance_mode",
        "maintenance_message",
    ):
        val = getattr(body, plain_key)
        if val is not None:
            update[plain_key] = val
    await db.reseller_configs.update_one(
        {"reseller_id": rid}, {"$set": update}
    )
    await audit(actor=user, action="config.patch",
                target=rid, reseller_id=rid, payload=update)
    return {"ok": True, "updated": list(update.keys())}


# ── Devices ────────────────────────────────────────────────────
@api.get("/admin/devices")
async def list_devices(
    online_within_seconds: int = 90,
    reseller_id: str | None = Query(default=None),
    user: dict = Depends(current_admin),
):
    rid = scope_reseller(user, reseller_id)
    cutoff = (now() - timedelta(seconds=online_within_seconds)).isoformat()
    items = []
    online = 0
    async for d in db.devices.find({"reseller_id": rid}, {"_id": 0}):
        is_online = d.get("last_seen", "") >= cutoff
        d["online"] = is_online
        if is_online:
            online += 1
        items.append(d)
    items.sort(key=lambda d: d.get("last_seen", ""), reverse=True)
    return {
        "items": items,
        "total": len(items),
        "online": online,
    }


@api.post("/admin/devices/{device_id}/block")
async def block_device(
    device_id: str,
    user: dict = Depends(current_admin),
):
    rid_filter: dict[str, Any] = {"id": device_id}
    if user["role"] == "reseller_admin":
        rid_filter["reseller_id"] = user["reseller_id"]
    res = await db.devices.update_one(rid_filter, {"$set": {"status": "blocked"}})
    if res.matched_count == 0:
        raise HTTPException(404, "Device not found")
    await audit(actor=user, action="device.block", target=device_id)
    return {"ok": True}


@api.post("/admin/devices/{device_id}/unblock")
async def unblock_device(
    device_id: str,
    user: dict = Depends(current_admin),
):
    rid_filter: dict[str, Any] = {"id": device_id}
    if user["role"] == "reseller_admin":
        rid_filter["reseller_id"] = user["reseller_id"]
    res = await db.devices.update_one(rid_filter, {"$set": {"status": "active"}})
    if res.matched_count == 0:
        raise HTTPException(404, "Device not found")
    await audit(actor=user, action="device.unblock", target=device_id)
    return {"ok": True}


# ── Broadcasts ─────────────────────────────────────────────────
@api.get("/admin/broadcasts")
async def list_broadcasts(
    reseller_id: str | None = Query(default=None),
    limit: int = 50,
    user: dict = Depends(current_admin),
):
    rid = scope_reseller(user, reseller_id)
    cur = db.broadcasts.find({"reseller_id": rid}, {"_id": 0}) \
        .sort("created_at", -1).limit(limit)
    items = await cur.to_list(length=limit)
    return {"items": items}


@api.post("/admin/broadcasts")
async def create_broadcast(
    body: BroadcastCreateIn,
    reseller_id: str | None = Query(default=None),
    user: dict = Depends(current_admin),
):
    rid = scope_reseller(user, reseller_id)
    rec = {
        "id": make_id(),
        "reseller_id": rid,
        "target_type": body.target_type,
        "target_device_id": body.target_device_id,
        "target_filter": body.target_filter or {},
        "title": body.title,
        "body": body.body,
        "severity": body.severity,
        "scheduled_at": (body.scheduled_at.isoformat()
                         if body.scheduled_at else None),
        "sent_at": (None if body.scheduled_at else now().isoformat()),
        "created_at": now().isoformat(),
        "stats": {"target_count": 0, "delivered": 0,
                  "displayed": 0, "dismissed": 0},
    }
    # Compute target device set up-front so the long-poll endpoint
    # can join against `device_messages` cheaply.
    device_filter: dict[str, Any] = {"reseller_id": rid,
                                     "status": {"$ne": "blocked"}}
    if body.target_type == "device" and body.target_device_id:
        device_filter["id"] = body.target_device_id
    elif body.target_type == "group" and body.target_filter:
        # Whitelisted filter keys to avoid Mongo injection.
        ok = {"app_version", "model", "country", "os_version"}
        for k, v in body.target_filter.items():
            if k in ok:
                device_filter[k] = v
    targets = []
    async for d in db.devices.find(device_filter, {"id": 1, "_id": 0}):
        targets.append(d["id"])
    rec["stats"]["target_count"] = len(targets)
    await db.broadcasts.insert_one(rec)
    if targets:
        # Fan-out: one device_messages row per target.
        await db.device_messages.insert_many([
            {
                "id": make_id(),
                "broadcast_id": rec["id"],
                "device_id": did,
                "reseller_id": rid,
                "delivered_at": None,
                "displayed_at": None,
                "dismissed_at": None,
                "created_at": now().isoformat(),
            }
            for did in targets
        ])
    await audit(actor=user, action="broadcast.create",
                target=rec["id"], reseller_id=rid,
                payload={"title": body.title,
                         "target_type": body.target_type,
                         "target_count": len(targets)})
    rec.pop("_id", None)
    return rec


# ── Audit log ──────────────────────────────────────────────────
@api.get("/admin/audit-log")
async def get_audit_log(
    limit: int = 200,
    reseller_id: str | None = Query(default=None),
    user: dict = Depends(current_admin),
):
    rid = scope_reseller(user, reseller_id)
    flt: dict[str, Any] = (
        {} if user["role"] == "super_admin" and reseller_id is None
        else {"reseller_id": rid}
    )
    cur = db.audit_log.find(flt, {"_id": 0}) \
        .sort("created_at", -1).limit(limit)
    return {"items": await cur.to_list(length=limit)}


# ── Dashboard summary ──────────────────────────────────────────
@api.get("/admin/summary")
async def dashboard_summary(
    reseller_id: str | None = Query(default=None),
    user: dict = Depends(current_admin),
):
    """Single dashboard endpoint that batches the counts the
    Dashboard page wants. Avoids 4 separate roundtrips on first
    paint."""
    rid = scope_reseller(user, reseller_id)
    cutoff = (now() - timedelta(seconds=90)).isoformat()
    online = await db.devices.count_documents(
        {"reseller_id": rid, "last_seen": {"$gte": cutoff}}
    )
    total_devices = await db.devices.count_documents({"reseller_id": rid})
    blocked = await db.devices.count_documents(
        {"reseller_id": rid, "status": "blocked"}
    )
    broadcasts_24h = await db.broadcasts.count_documents({
        "reseller_id": rid,
        "created_at": {"$gte": (now() - timedelta(hours=24)).isoformat()},
    })
    summary = {
        "online_devices": online,
        "total_devices": total_devices,
        "blocked_devices": blocked,
        "broadcasts_24h": broadcasts_24h,
    }
    if user["role"] == "super_admin":
        summary["total_resellers"] = await db.resellers.count_documents({})
    return summary


# ────────────────────────────────────────────────────────────────
# Public API (called by the Android app — no auth required)
# ────────────────────────────────────────────────────────────────
@api.post("/activate")
async def activate(body: ActivateIn):
    """Resolve an activation code → public reseller config the
    Android app uses to brand itself on first launch."""
    code = body.activation_code.upper().strip()
    reseller = await db.resellers.find_one(
        {"activation_code": code, "status": "active"},
        {"_id": 0},
    )
    if not reseller:
        raise HTTPException(404, "Invalid or suspended activation code")
    return await _public_config(reseller)


@api.get("/config/{reseller_slug}")
async def get_public_config(reseller_slug: str):
    """Re-fetch on subsequent launches (already-activated apps)."""
    reseller = await db.resellers.find_one(
        {"slug": reseller_slug, "status": "active"},
        {"_id": 0},
    )
    if not reseller:
        raise HTTPException(404, "Reseller not found or suspended")
    return await _public_config(reseller)


async def _public_config(reseller: dict) -> PublicConfigOut:
    cfg = await db.reseller_configs.find_one(
        {"reseller_id": reseller["id"]}, {"_id": 0}
    ) or {}
    branding = cfg.get("branding", _default_branding(reseller["display_name"]))
    return PublicConfigOut(
        reseller_id=reseller["id"],
        reseller_slug=reseller["slug"],
        app_name=branding.get("app_name") or reseller["display_name"],
        branding=branding,
        feature_flags=cfg.get("feature_flags", _default_feature_flags()),
        xtream_default=cfg.get("xtream_default") or None,
        min_app_version=cfg.get("min_app_version"),
        maintenance_mode=bool(cfg.get("maintenance_mode")),
        maintenance_message=cfg.get("maintenance_message"),
    )


@api.post("/heartbeat")
async def heartbeat(body: HeartbeatIn):
    """Anonymous device beacon — called every ~30 s while the
    app is open. Upserts the device row and returns whether
    there are any pending broadcast messages."""
    reseller = await db.resellers.find_one(
        {"activation_code": body.reseller_code.upper().strip(),
         "status": "active"},
        {"id": 1, "_id": 0},
    )
    if not reseller:
        raise HTTPException(404, "Unknown reseller code")
    update = {
        "last_seen": now().isoformat(),
        "model": body.model or "",
        "os_version": body.os_version or "",
        "app_version": body.app_version or "",
        "last_screen": body.last_screen or "",
    }
    on_insert = {
        "id": body.device_id,
        "reseller_id": reseller["id"],
        "first_seen": now().isoformat(),
        "status": "active",
    }
    await db.devices.update_one(
        {"id": body.device_id, "reseller_id": reseller["id"]},
        {"$set": update, "$setOnInsert": on_insert},
        upsert=True,
    )
    pending = await db.device_messages.count_documents({
        "device_id": body.device_id,
        "reseller_id": reseller["id"],
        "delivered_at": None,
    })
    return {"ok": True, "pending_messages": pending}


@api.get("/messages/pending")
async def messages_pending(
    device_id: str = Query(..., min_length=4),
    reseller_code: str = Query(..., min_length=4),
):
    """Long-poll endpoint — returns pending broadcasts for this
    device and atomically marks them delivered so they don't
    repeat on the next poll."""
    reseller = await db.resellers.find_one(
        {"activation_code": reseller_code.upper().strip(),
         "status": "active"},
        {"id": 1, "_id": 0},
    )
    if not reseller:
        raise HTTPException(404, "Unknown reseller code")

    pending = []
    async for dm in db.device_messages.find({
        "device_id": device_id,
        "reseller_id": reseller["id"],
        "delivered_at": None,
    }, {"_id": 0}):
        bc = await db.broadcasts.find_one(
            {"id": dm["broadcast_id"]}, {"_id": 0}
        )
        if not bc:
            continue
        pending.append({
            "delivery_id": dm["id"],
            "broadcast_id": bc["id"],
            "title": bc["title"],
            "body": bc["body"],
            "severity": bc["severity"],
            "created_at": bc.get("created_at"),
        })
    if pending:
        ids = [p["delivery_id"] for p in pending]
        await db.device_messages.update_many(
            {"id": {"$in": ids}},
            {"$set": {"delivered_at": now().isoformat()}},
        )
        # Bump the broadcast's aggregate `delivered` counter — we
        # iterate one update per broadcast since the fan-out per
        # device is normally tiny (a single open broadcast at a
        # time). When/if we batch broadcasts in flight we'll
        # group these by broadcast_id first.
        for p in pending:
            await db.broadcasts.update_one(
                {"id": p["broadcast_id"]},
                {"$inc": {"stats.delivered": 1}},
            )
    return {"items": pending}


@api.post("/messages/{delivery_id}/ack")
async def ack_message(
    delivery_id: str,
    state: Literal["displayed", "dismissed"] = "displayed",
):
    """Optional client-side ack so we know the user actually saw
    a banner (vs just received it). Updates delivery state and
    bumps the broadcast's aggregate counter."""
    field = "displayed_at" if state == "displayed" else "dismissed_at"
    rec = await db.device_messages.find_one_and_update(
        {"id": delivery_id, field: None},
        {"$set": {field: now().isoformat()}},
    )
    if rec:
        await db.broadcasts.update_one(
            {"id": rec["broadcast_id"]},
            {"$inc": {f"stats.{state}": 1}},
        )
    return {"ok": True}


# ────────────────────────────────────────────────────────────────
# Diagnostics — "Network & Stream Health" reports
# ────────────────────────────────────────────────────────────────
#
# Phase-1 design: the Android app runs a 5-stage wizard (DNS,
# speed, Xtream reachability, reference-clip playback, provider
# stream playback), rolls the results up into one JSON document,
# and POSTs it here. We store per-reseller so super-admins + the
# reseller-admin can see every device's latest report.
#
# The reference clip sits on our OTA server:
#   https://hushtv.xyz/diagnostic-reference.mp4
# (30 s MP4, 3.5 Mbps, faststart-encoded). Client measures TTFF,
# rebuffer count, dropped frames, ExoPlayer-observed bandwidth
# during the playback phase. Same metrics collected for the
# user's provider stream so we can say "our clip played clean,
# theirs stalled → provider's fault" with numbers, not hunches.
class DiagnosticReportIn(_Base):
    device_id: str = Field(min_length=4, max_length=128)
    reseller_code: str
    app_version: str | None = None
    model: str | None = None
    os_version: str | None = None
    # Test results — free-form JSON so we can add new fields
    # client-side without changing the server schema.
    network: dict | None = None     # DNS / ping / download / jitter / loss
    xtream: dict | None = None      # portal reachability + API latency
    reference: dict | None = None   # playback metrics against our clip
    provider: dict | None = None    # playback metrics against their stream
    verdict: str | None = None      # client-rolled-up conclusion
    notes: str | None = None        # any captured error strings


@api.post("/diagnostics/report")
async def submit_diagnostic(body: DiagnosticReportIn):
    """Public endpoint — the Android app's Settings → Health Check
    screen uploads here without auth. Reseller is resolved by
    `reseller_code`. Reports are append-only; no update path."""
    reseller = await db.resellers.find_one(
        {"activation_code": body.reseller_code.upper().strip(),
         "status": "active"},
        {"id": 1, "_id": 0},
    )
    if not reseller:
        raise HTTPException(404, "Unknown reseller code")
    rec = {
        "id": make_id(),
        "reseller_id": reseller["id"],
        "device_id": body.device_id,
        "app_version": body.app_version or "",
        "model": body.model or "",
        "os_version": body.os_version or "",
        "network": body.network or {},
        "xtream": body.xtream or {},
        "reference": body.reference or {},
        "provider": body.provider or {},
        "verdict": body.verdict or "unknown",
        "notes": body.notes or "",
        "created_at": now().isoformat(),
    }
    await db.diagnostics.insert_one(rec)
    rec.pop("_id", None)
    return {"ok": True, "report_id": rec["id"]}


@api.get("/admin/diagnostics")
async def list_diagnostics(
    limit: int = 100,
    device_id: str | None = None,
    reseller_id: str | None = Query(default=None),
    user: dict = Depends(current_admin),
):
    """Admin list — newest first, filterable by device."""
    rid = scope_reseller(user, reseller_id)
    flt: dict[str, Any] = {"reseller_id": rid}
    if device_id:
        flt["device_id"] = device_id
    cur = db.diagnostics.find(flt, {"_id": 0}) \
        .sort("created_at", -1).limit(limit)
    return {"items": await cur.to_list(length=limit)}


@api.get("/admin/diagnostics/{report_id}")
async def get_diagnostic(
    report_id: str,
    user: dict = Depends(current_admin),
):
    flt: dict[str, Any] = {"id": report_id}
    if user["role"] == "reseller_admin":
        flt["reseller_id"] = user["reseller_id"]
    rec = await db.diagnostics.find_one(flt, {"_id": 0})
    if not rec:
        raise HTTPException(404, "Report not found")
    return rec


# ─────────────────────────────────────────────────────────────────
# DVR Cluster — super-admin-only
# ─────────────────────────────────────────────────────────────────
# A "DVR node" is a remote server running /opt/hushdvr/dvr_service.py
# with its nginx proxy on port 80. We store a registry of nodes in
# the `dvr_servers` collection and fan-out /api/dvr/stats queries
# whenever the admin panel asks for the cluster dashboard.
#
# Nodes are NOT multi-tenant today: every reseller's users hit the
# same cluster because the DVR service is provider-agnostic. Adding
# per-reseller pinning later is a schema-only change.

class DvrServerIn(BaseModel):
    ip: str = Field(..., min_length=3, max_length=128)
    label: str = Field(..., min_length=1, max_length=64)
    # Shared secret the node expects on /api/dvr/stats?token=…
    stats_token: str = Field(..., min_length=6, max_length=128)
    # Optional https prefix override; defaults to http://{ip}.
    base_url: str | None = None
    notes: str | None = None


class DvrServerOut(BaseModel):
    id: str
    ip: str
    label: str
    base_url: str
    notes: str | None = None
    added_at: datetime
    active: bool
    # Live stats (populated by the list endpoint via fan-out).
    online: bool = False
    error: str | None = None
    disk_total_bytes: int = 0
    disk_used_bytes: int = 0
    disk_free_bytes: int = 0
    pct_used: float = 0.0
    active_recordings: int = 0
    total_recordings: int = 0
    users_with_data: int = 0
    uptime_s: int = 0


def _dvr_base_url(srv: dict) -> str:
    return (srv.get("base_url") or "").rstrip("/") or f"http://{srv['ip']}"


async def _fetch_node_stats(srv: dict, client: httpx.AsyncClient) -> dict:
    """
    Single-node stats fetch. Returns a dict with `online`, plus all
    disk/usage fields when reachable, or `error` when not. Never
    raises — the admin UI shows an "Offline" row instead.
    """
    try:
        r = await client.get(
            f"{_dvr_base_url(srv)}/api/dvr/stats",
            params={"token": srv.get("stats_token", "")},
            timeout=5.0,
        )
        if r.status_code != 200:
            return {"online": False, "error": f"HTTP {r.status_code}"}
        j = r.json()
        disk = j.get("disk") or {}
        return {
            "online": True,
            "error": None,
            "disk_total_bytes": int(disk.get("total_bytes", 0)),
            "disk_used_bytes": int(disk.get("used_bytes", 0)),
            "disk_free_bytes": int(disk.get("free_bytes", 0)),
            "pct_used": float(disk.get("pct_used", 0.0)),
            "active_recordings": int(j.get("active_recordings", 0)),
            "total_recordings": int(j.get("total_recordings", 0)),
            "users_with_data": int(j.get("users_with_data", 0)),
            "uptime_s": int(j.get("uptime_s", 0)),
        }
    except Exception as exc:  # noqa: BLE001
        return {"online": False, "error": type(exc).__name__}


@api.get("/admin/dvr/servers")
async def list_dvr_servers(
    user: dict = Depends(require_super),
) -> dict[str, Any]:
    """
    Cluster dashboard source. Returns every registered DVR node
    merged with its live stats (from a fan-out). Also returns a
    cluster-wide summary + an `add_server_urgent` flag when any
    node is past 90% disk usage, which the UI surfaces as a yellow
    banner above the server table.
    """
    servers = await db.dvr_servers.find(
        {}, {"_id": 0},
    ).sort("added_at", 1).to_list(length=200)
    async with httpx.AsyncClient() as client:
        live = await asyncio.gather(
            *(_fetch_node_stats(s, client) for s in servers),
            return_exceptions=False,
        )
    for s, stats in zip(servers, live):
        s.update(stats)
        # Never leak the secret back to the UI.
        s.pop("stats_token", None)
    # Cluster summary
    online = [s for s in servers if s.get("online")]
    total = sum(s.get("disk_total_bytes", 0) for s in online)
    used = sum(s.get("disk_used_bytes", 0) for s in online)
    free = sum(s.get("disk_free_bytes", 0) for s in online)
    add_urgent = any(s.get("pct_used", 0) >= 90 for s in online)
    return {
        "items": servers,
        "summary": {
            "servers_total": len(servers),
            "servers_online": len(online),
            "disk_total_bytes": total,
            "disk_used_bytes": used,
            "disk_free_bytes": free,
            "pct_used": (used / total * 100.0) if total > 0 else 0.0,
            "active_recordings": sum(s.get("active_recordings", 0) for s in online),
            "total_recordings": sum(s.get("total_recordings", 0) for s in online),
            "users_with_data": sum(s.get("users_with_data", 0) for s in online),
            "add_server_urgent": add_urgent,
        },
    }


@api.post("/admin/dvr/servers", status_code=201)
async def add_dvr_server(
    body: DvrServerIn,
    user: dict = Depends(require_super),
) -> dict[str, Any]:
    """
    Register a new DVR node in the cluster. We verify the node is
    reachable and the stats-token is correct before persisting, so a
    typo in the IP or token fails fast with a clear message instead
    of silently producing an "offline" row forever.
    """
    srv_doc = {
        "id": make_id(),
        "ip": body.ip.strip(),
        "label": body.label.strip(),
        "base_url": (body.base_url or "").strip() or None,
        "stats_token": body.stats_token,
        "notes": (body.notes or "").strip() or None,
        "added_at": datetime.now(timezone.utc),
        "active": True,
        "added_by": user["id"],
    }
    async with httpx.AsyncClient() as client:
        probe = await _fetch_node_stats(srv_doc, client)
    if not probe.get("online"):
        raise HTTPException(
            400,
            f"Couldn't reach the new DVR node: {probe.get('error') or 'offline'}. "
            "Double-check the IP and stats token.",
        )
    await db.dvr_servers.insert_one(srv_doc)
    await audit(
        actor=user, action="dvr_server.add",
        target=srv_doc["id"], payload={"ip": srv_doc["ip"]},
    )
    srv_doc.pop("_id", None)
    srv_doc.pop("stats_token", None)
    srv_doc.update(probe)
    return srv_doc


@api.delete("/admin/dvr/servers/{server_id}")
async def delete_dvr_server(
    server_id: str,
    user: dict = Depends(require_super),
) -> dict[str, Any]:
    res = await db.dvr_servers.delete_one({"id": server_id})
    if res.deleted_count == 0:
        raise HTTPException(404, "DVR node not found")
    await audit(
        actor=user, action="dvr_server.remove", target=server_id,
    )
    return {"ok": True, "id": server_id}


@app.on_event("startup")
async def startup():
    await db.admin_users.create_index("email", unique=True)
    await db.admin_users.create_index("id", unique=True)
    await db.resellers.create_index("slug", unique=True)
    await db.resellers.create_index("activation_code", unique=True)
    await db.resellers.create_index("id", unique=True)
    await db.devices.create_index([("reseller_id", 1), ("id", 1)], unique=True)
    await db.devices.create_index("last_seen")
    await db.broadcasts.create_index([("reseller_id", 1), ("created_at", -1)])
    await db.device_messages.create_index([("device_id", 1), ("delivered_at", 1)])
    await db.audit_log.create_index([("reseller_id", 1), ("created_at", -1)])
    await db.reseller_configs.create_index("reseller_id", unique=True)
    await db.diagnostics.create_index([("reseller_id", 1), ("created_at", -1)])
    await db.diagnostics.create_index([("reseller_id", 1), ("device_id", 1),
                                       ("created_at", -1)])
    await db.dvr_servers.create_index("id", unique=True)
    await _seed_defaults()


async def _seed_defaults():
    # Default reseller (HushTV) — created so the super-admin has
    # an immediate working tenant to look at.
    default_slug = os.environ.get("DEFAULT_RESELLER_SLUG", "hushtv")
    default_name = os.environ.get("DEFAULT_RESELLER_NAME", "HushTV")
    reseller = await db.resellers.find_one({"slug": default_slug})
    if not reseller:
        rid = make_id()
        code = make_activation_code()
        reseller = {
            "id": rid,
            "slug": default_slug,
            "display_name": default_name,
            "owner_email": os.environ.get("ADMIN_EMAIL",
                                          "admin@hushtv.xyz"),
            "activation_code": code,
            "plan_tier": "owner",
            "status": "active",
            "created_at": now().isoformat(),
        }
        await db.resellers.insert_one(reseller)
        await db.reseller_configs.insert_one({
            "reseller_id": rid,
            "branding": _default_branding(default_name),
            "feature_flags": _default_feature_flags(),
            "xtream_default": "",
            "min_app_version": "1.42.95",
            "maintenance_mode": False,
            "maintenance_message": "",
            "updated_at": now().isoformat(),
        })
        log.info("Seeded default reseller %s (code=%s)",
                 default_slug, code)
    # Default super-admin.
    email = os.environ["ADMIN_EMAIL"].lower().strip()
    pwd = os.environ["ADMIN_PASSWORD"]
    existing = await db.admin_users.find_one({"email": email})
    if not existing:
        await db.admin_users.insert_one({
            "id": make_id(),
            "email": email,
            "name": "HushTV Owner",
            "role": "super_admin",
            "reseller_id": reseller["id"],
            "password_hash": hash_password(pwd),
            "created_at": now().isoformat(),
        })
        log.info("Seeded super-admin %s", email)
    elif not verify_password(pwd, existing["password_hash"]):
        # Password rotated in .env — bring DB in line.
        await db.admin_users.update_one(
            {"id": existing["id"]},
            {"$set": {"password_hash": hash_password(pwd)}},
        )
        log.info("Rotated super-admin password for %s", email)


@app.on_event("shutdown")
async def shutdown():
    mongo.close()


# ────────────────────────────────────────────────────────────────
# Wire-up — CORS + routes
# ────────────────────────────────────────────────────────────────
allowed_origins = os.environ.get(
    "CORS_ORIGINS",
    "http://localhost:3000",
).split(",")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in allowed_origins if o.strip()],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(api)

# ─── HushTV Canada license proxy ───────────────────────────────────
# The Canada $40 (or test-mode $5/$10) CDN-proxy-fee endpoints live on
# the sync_server FastAPI at https://hushtv.xyz/api/admin/canada/*
# behind an X-Admin-Token. We expose a thin authenticated proxy here so
# the React admin panel can talk to its OWN backend (cookie-auth) and
# this layer adds the X-Admin-Token before calling the sync server.
# Keeps the sync-server admin token off the browser side entirely.

CANADA_SYNC_BASE = os.environ.get(
    "CANADA_SYNC_BASE", "https://hushtv.xyz",
).rstrip("/")
CANADA_ADMIN_TOKEN = os.environ.get(
    "CANADA_ADMIN_TOKEN",
    os.environ.get("SPORTS_ADMIN_TOKEN", ""),
)

_canada_api = APIRouter(
    prefix="/api/admin/canada",
    tags=["canada-admin-proxy"],
    dependencies=[Depends(current_admin)],  # cookie-auth required
)


async def _canada_proxy(method: str, path: str, json_body=None, params=None):
    if not CANADA_ADMIN_TOKEN:
        raise HTTPException(503, "CANADA_ADMIN_TOKEN not configured on backend")
    url = f"{CANADA_SYNC_BASE}/api/admin/canada{path}"
    headers = {"X-Admin-Token": CANADA_ADMIN_TOKEN}
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            r = await client.request(method, url, json=json_body,
                                     params=params, headers=headers)
    except httpx.HTTPError as e:
        raise HTTPException(502, f"sync server unreachable: {e}")
    if r.status_code >= 400:
        # bubble up the upstream message so admins can debug
        try:
            detail = r.json().get("detail") or r.text
        except Exception:
            detail = r.text
        raise HTTPException(r.status_code, detail or "upstream error")
    return r.json()


# ── License lookup (read-only, by xtream username) ─
@_canada_api.get("/license/{xtream_username}")
async def canada_license(xtream_username: str):
    # Public license endpoint on the sync server (no admin token needed)
    # but we proxy it so the admin panel doesn't have to know about the
    # different base URL.
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            r = await client.get(
                f"{CANADA_SYNC_BASE}/api/canada/license/{xtream_username.strip().lower()}"
            )
    except httpx.HTTPError as e:
        raise HTTPException(502, f"sync server unreachable: {e}")
    if r.status_code >= 400:
        raise HTTPException(r.status_code, r.text)
    return r.json()


# ── Grant a license (the simple manual-approve flow) ─
class _GrantReq(BaseModel):
    xtream_username: str
    months: int = 12


@_canada_api.post("/grant")
async def canada_grant(req: _GrantReq):
    return await _canada_proxy("POST", "/grant", json_body=req.model_dump())


class _RevokeReq(BaseModel):
    xtream_username: str


@_canada_api.post("/revoke")
async def canada_revoke(req: _RevokeReq):
    return await _canada_proxy("POST", "/revoke", json_body=req.model_dump())


@_canada_api.get("/orders")
async def canada_orders(limit: int = 50):
    return await _canada_proxy("GET", "/orders", params={"limit": limit})


@_canada_api.get("/licenses")
async def canada_licenses(limit: int = 500):
    return await _canada_proxy("GET", "/licenses", params={"limit": limit})


@_canada_api.post("/poll")
async def canada_poll():
    return await _canada_proxy("POST", "/poll")


app.include_router(_canada_api)
