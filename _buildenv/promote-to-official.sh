#!/usr/bin/env bash
# Promote the current dev build → the official channel.
#
# Usage:
#   /app/_buildenv/promote-to-official.sh
#
# What this does (in order):
#   1. Build the official-flavor APK from the current source tree.
#      Same code, same versionCode/versionName, just BuildConfig
#      points at https://hushtv.xyz/version-official.json
#      and https://hushtv.xyz/hushtv-official.apk
#   2. Copy app/build/outputs/apk/official/debug/app-official-debug.apk
#      → root@66.163.113.147:/var/www/hushtv/hushtv-official.apk
#      (lowercase to match the URL; we ALSO mirror to
#       HushTV-Official.apk so legacy bookmarks / docs keep working).
#   3. Copy /app/_buildenv/version-official.json
#      → root@66.163.113.147:/var/www/hushtv/version-official.json
#
# Run this ONLY when the user explicitly says "push to official".
# Routine dev pushes use ./gradlew assembleDevDebug + the existing
# scp commands.

set -euo pipefail

cd /app/androidtv

# Pre-build disk hygiene so a 9.8G /app pod never aborts the
# official build mid-compile.
/app/_buildenv/disk-janitor.sh

echo "▶ Building official-flavor APK…"
./gradlew assembleOfficialDebug

APK_LOCAL=app/build/outputs/apk/official/debug/app-official-debug.apk
echo "▶ Built $APK_LOCAL ($(du -h "$APK_LOCAL" | cut -f1))"

echo "▶ Uploading APK + manifest to OTA server…"
# Upload to the lowercase name the manifest references (and that
# nginx serves at /hushtv-official.apk). Without this, version-
# official.json points clients at a stale binary. Mirror to the
# legacy CamelCase name too so older docs / bookmarks still work.
sshpass -p 'A_i_36RO84SBAwbg' scp -o StrictHostKeyChecking=no \
    "$APK_LOCAL" \
    root@66.163.113.147:/var/www/hushtv/hushtv-official.apk

sshpass -p 'A_i_36RO84SBAwbg' ssh -o StrictHostKeyChecking=no \
    root@66.163.113.147 \
    "cp -f /var/www/hushtv/hushtv-official.apk /var/www/hushtv/HushTV-Official.apk"

sshpass -p 'A_i_36RO84SBAwbg' scp -o StrictHostKeyChecking=no \
    /app/_buildenv/version-official.json \
    root@66.163.113.147:/var/www/hushtv/version-official.json

echo "▶ Tagging the commit so we can roll back to it later…"
/app/_buildenv/tag-release.sh official

echo "✔ Official channel updated:"
echo "  APK:      https://hushtv.xyz/hushtv-official.apk"
echo "  Manifest: https://hushtv.xyz/version-official.json"
