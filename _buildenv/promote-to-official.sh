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
#      → root@66.163.113.147:/var/www/hushtv/HushTV-Official.apk
#   3. Copy /app/_buildenv/version-official.json
#      → root@66.163.113.147:/var/www/hushtv/version-official.json
#
# Run this ONLY when the user explicitly says "push to official".
# Routine dev pushes use ./gradlew assembleDevDebug + the existing
# scp commands.

set -euo pipefail

cd /app/androidtv

echo "▶ Building official-flavor APK…"
./gradlew assembleOfficialDebug

APK_LOCAL=app/build/outputs/apk/official/debug/app-official-debug.apk
echo "▶ Built $APK_LOCAL ($(du -h "$APK_LOCAL" | cut -f1))"

echo "▶ Uploading APK + manifest to OTA server…"
sshpass -p 'A_i_36RO84SBAwbg' scp -o StrictHostKeyChecking=no \
    "$APK_LOCAL" \
    root@66.163.113.147:/var/www/hushtv/HushTV-Official.apk

sshpass -p 'A_i_36RO84SBAwbg' scp -o StrictHostKeyChecking=no \
    /app/_buildenv/version-official.json \
    root@66.163.113.147:/var/www/hushtv/version-official.json

echo "✔ Official channel updated:"
echo "  APK:      https://hushtv.xyz/hushtv-official.apk"
echo "  Manifest: https://hushtv.xyz/version-official.json"
