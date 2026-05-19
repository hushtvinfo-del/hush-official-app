#!/usr/bin/env bash
# rollback-perf-changes.sh — One-shot rollback to v1.44.95 pre-perf-tuning state.
#
# Restores:
#   • /var/www/hushtv/HushTV.apk           ← pre-perf dev APK
#   • /var/www/hushtv/hushtv-canada.apk    ← pre-perf canada APK
#   • /var/www/hushtv/version.json         ← pre-perf manifests
#   • /var/www/hushtv/version-canada.json
#   • /var/www/hushtv/admin.html           ← pre-perf admin panel
#   • /opt/hushtv-sync/canada_payment_module.py
#
# Backup timestamp lives in /app/_buildenv/.last-perf-rollback-tag
# Run from inside the agent pod:
#     bash /app/_buildenv/rollback-perf-changes.sh
#
# Devices will pick up the rolled-back APK on their next OTA check
# (within ~3s of opening the app).

set -euo pipefail
SERVER=66.163.113.147
PASS='A_i_36RO84SBAwbg'
TS=$(cat /app/_buildenv/.last-perf-rollback-tag 2>/dev/null || echo "")
if [ -z "$TS" ]; then
    echo "ERROR: /app/_buildenv/.last-perf-rollback-tag missing — can't determine which backup to restore."
    echo "Run 'ls /var/www/hushtv/backups/' on the server to pick a timestamp manually."
    exit 1
fi
echo "▶ Rolling back to pre-perf backup tagged $TS"

sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no root@$SERVER "set -e
cp /var/www/hushtv/backups/HushTV-1.44.95-pre-perf-$TS.apk            /var/www/hushtv/HushTV.apk
cp /var/www/hushtv/backups/hushtv-canada-1.44.95-pre-perf-$TS.apk     /var/www/hushtv/hushtv-canada.apk
cp /var/www/hushtv/backups/version-1.44.95-pre-perf-$TS.json          /var/www/hushtv/version.json
cp /var/www/hushtv/backups/version-canada-1.44.95-pre-perf-$TS.json   /var/www/hushtv/version-canada.json
cp /var/www/hushtv/backups/admin-1.44.95-pre-perf-$TS.html            /var/www/hushtv/admin.html
cp /opt/hushtv-sync/canada_payment_module.py.pre-perf-$TS.bak         /opt/hushtv-sync/canada_payment_module.py
systemctl restart hushtv-sync
echo
echo '✓ Rollback complete. Live manifest:'
curl -s https://hushtv.xyz/version.json | grep -E '\"versionName\"|\"versionCode\"'
"
