#!/usr/bin/env bash
# build-and-deploy-dev.sh — Single-command Dev release flow.
#
# Usage:
#   /app/_buildenv/build-and-deploy-dev.sh
#
# Does, in order:
#   1. assembleDevDebug (./gradlew)
#   2. scp APK + version.json to root@66.163.113.147:/var/www/hushtv/
#   3. Tag HEAD as v{versionName}-dev (via tag-release.sh — idempotent)
#   4. Print the live manifest summary so you can verify the OTA
#      server is actually serving the new build.
#
# This replaces the manual sequence of:
#   ./gradlew assembleDevDebug
#   sshpass -p 'XXX' scp ...
#   curl -s https://hushtv.xyz/version.json
#
# After this script exits successfully, the user can sideload or OTA
# update on Fire Stick / Shield, AND the commit is permanently
# bookmarked for "go back to vN.N.N" requests.

set -euo pipefail

cd /app/androidtv

echo "▶ Building dev-flavor APK…"
export JAVA_HOME=/app/_buildenv/jdk
export ANDROID_HOME=/app/_buildenv/android-sdk
./gradlew assembleDevDebug

APK_LOCAL=app/build/outputs/apk/dev/debug/app-dev-debug.apk
APK_SIZE=$(du -h "$APK_LOCAL" | cut -f1)
echo "▶ Built $APK_LOCAL ($APK_SIZE)"

echo "▶ Uploading APK + manifest to OTA server…"
sshpass -p 'A_i_36RO84SBAwbg' scp -o StrictHostKeyChecking=no \
    "$APK_LOCAL" \
    root@66.163.113.147:/var/www/hushtv/HushTV.apk

sshpass -p 'A_i_36RO84SBAwbg' scp -o StrictHostKeyChecking=no \
    /app/_buildenv/version.json \
    root@66.163.113.147:/var/www/hushtv/version.json

echo "▶ Tagging the commit so we can roll back to it later…"
/app/_buildenv/tag-release.sh dev

echo ""
echo "✔ Dev channel updated:"
echo "  APK:      https://hushtv.xyz/HushTV.apk"
echo "  Manifest: https://hushtv.xyz/version.json"
echo ""
echo "▶ Live manifest from OTA server:"
curl -s https://hushtv.xyz/version.json | python3 -m json.tool 2>/dev/null \
    || curl -s https://hushtv.xyz/version.json
