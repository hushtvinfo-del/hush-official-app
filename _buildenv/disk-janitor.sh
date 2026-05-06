#!/usr/bin/env bash
# disk-janitor.sh — Disk hygiene used by the build pipeline.
#
# Two phases:
#   1) Local /app pod cleanup. The /app volume is fixed at 9.8 GB by
#      the Emergent K8s template. A typical Gradle build needs ~2 GB
#      of free working room. If we drop below the LOW_WATER threshold
#      we aggressively prune build artifacts, archived APKs, and
#      transient caches.
#   2) Remote OTA-server cleanup. The deploy server has 1.1 TB and
#      isn't going to fill anytime soon, but staged/legacy APKs do
#      accumulate. Rotate so we keep at most KEEP_REMOTE recent files
#      per channel.
#
# Run by build-and-deploy-dev.sh (and promote-to-official.sh) before
# anything that produces large output.
#
# Re-running this is safe and idempotent.

set -euo pipefail

# ── Tuning knobs ────────────────────────────────────────────────────
LOW_WATER_GB="${HUSHTV_LOW_WATER_GB:-2}"   # free GB threshold on /app
KEEP_REMOTE="${HUSHTV_KEEP_REMOTE:-3}"     # keep N most-recent staged APKs
REMOTE_HOST="${HUSHTV_DEPLOY_HOST:-root@66.163.113.147}"
REMOTE_PASS="${HUSHTV_DEPLOY_PASS:-A_i_36RO84SBAwbg}"
REMOTE_DIR="${HUSHTV_DEPLOY_DIR:-/var/www/hushtv}"

# ── Helpers ─────────────────────────────────────────────────────────
free_gb() {
    df -BG /app | awk 'NR==2 {gsub("G","",$4); print $4+0}'
}

bytes_human() {
    numfmt --to=iec --suffix=B "$1" 2>/dev/null || echo "${1}B"
}

# ── Phase 1: local pod cleanup ──────────────────────────────────────
echo "▶ disk-janitor: /app free = $(free_gb)G (low-water: ${LOW_WATER_GB}G)"

# Always: drop Gradle build outputs older than the most recent. They
# regenerate per build and are tens of MB each.
if [ -d /app/androidtv/app/build/outputs/apk ]; then
    find /app/androidtv/app/build/outputs/apk -name 'app-*.apk' -mmin +60 \
        -print -delete 2>/dev/null || true
fi

# Always: archived dist APKs older than the latest 3.
if [ -d /app/dist ]; then
    { ls -1t /app/dist/HushTV-android-tv-v*.apk 2>/dev/null \
        | tail -n +4 \
        | xargs -r rm -v; } || true
fi

# If we're below the low-water mark, escalate.
if [ "$(free_gb)" -lt "$LOW_WATER_GB" ]; then
    echo "▶ disk-janitor: below ${LOW_WATER_GB}G — running aggressive cleanup"

    # Wipe Gradle's transient build dir entirely. Safe — it rebuilds.
    rm -rf /app/androidtv/app/build || true
    rm -rf /app/androidtv/.gradle || true

    # Drop daemon caches (older than 7 days). Daemon will respawn.
    find /root/.gradle/caches -maxdepth 1 -type d -mtime +7 \
        -name 'modules-*' -exec rm -rf {} + 2>/dev/null || true
    find /root/.gradle/daemon -maxdepth 1 -type d -mtime +1 \
        -exec rm -rf {} + 2>/dev/null || true

    # Trim apt and pip caches.
    apt-get clean 2>/dev/null || true
    rm -rf /root/.cache/pip 2>/dev/null || true

    # Prune git pack files older than 30 days. The repo is mostly
    # binary commits which compress well after gc.
    if [ -d /app/.git ] && [ "$(free_gb)" -lt "$LOW_WATER_GB" ]; then
        echo "▶ disk-janitor: still tight — running git gc --aggressive"
        git -C /app gc --aggressive --prune=now 2>/dev/null || true
    fi

    echo "▶ disk-janitor: post-clean free = $(free_gb)G"
fi

# ── Phase 2: rotate remote staged APKs ──────────────────────────────
# Cheap and silent unless something's truly out of whack.
if command -v sshpass >/dev/null 2>&1; then
    REMOTE_LIST=$(
        sshpass -p "$REMOTE_PASS" ssh -o StrictHostKeyChecking=no \
            -o ConnectTimeout=8 "$REMOTE_HOST" \
            "ls -1t $REMOTE_DIR/HushTV-Official-staged*.apk 2>/dev/null | tail -n +$((KEEP_REMOTE + 1))" \
            2>/dev/null || true
    )
    if [ -n "$REMOTE_LIST" ]; then
        echo "▶ disk-janitor: rotating $(echo "$REMOTE_LIST" | wc -l) old staged APK(s) on $REMOTE_HOST"
        # shellcheck disable=SC2086
        sshpass -p "$REMOTE_PASS" ssh -o StrictHostKeyChecking=no \
            "$REMOTE_HOST" "rm -f $(echo "$REMOTE_LIST" | tr '\n' ' ')" \
            2>/dev/null || true
    fi
fi

echo "▶ disk-janitor: done."
