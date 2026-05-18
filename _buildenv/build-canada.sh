#!/usr/bin/env bash
# Build the Canada-branded flavor → publish to its own OTA URL.
#
# Usage:
#   /app/_buildenv/build-canada.sh
#
# Same pattern as promote-to-official.sh except for the canada flavor:
#   • Builds  app/build/outputs/apk/canada/debug/app-canada-debug.apk
#   • Uploads → root@66.163.113.147:/var/www/hushtv/hushtv-canada.apk
#   • Uploads → root@66.163.113.147:/var/www/hushtv/version-canada.json
#
# Distribution URLs:
#   APK:      https://hushtv.xyz/hushtv-canada.apk
#   Manifest: https://hushtv.xyz/version-canada.json

set -euo pipefail

cd /app/androidtv

/app/_buildenv/disk-janitor.sh

echo "▶ Building canada-flavor APK…"
./gradlew assembleCanadaDebug

APK_LOCAL=app/build/outputs/apk/canada/debug/app-canada-debug.apk
echo "▶ Built $APK_LOCAL ($(du -h "$APK_LOCAL" | cut -f1))"

echo "▶ Uploading APK + manifest to OTA server…"
sshpass -p 'A_i_36RO84SBAwbg' scp -o StrictHostKeyChecking=no \
    "$APK_LOCAL" \
    root@66.163.113.147:/var/www/hushtv/hushtv-canada.apk

sshpass -p 'A_i_36RO84SBAwbg' scp -o StrictHostKeyChecking=no \
    /app/_buildenv/version-canada.json \
    root@66.163.113.147:/var/www/hushtv/version-canada.json

echo "▶ Tagging the commit so we can roll back to it later…"
/app/_buildenv/tag-release.sh canada || true

echo "✔ Canada channel updated:"
echo "  APK:      https://hushtv.xyz/hushtv-canada.apk"
echo "  Manifest: https://hushtv.xyz/version-canada.json"
