#!/usr/bin/env bash
# bootstrap-build-env.sh — Recover Android build env after pod reschedule.
#
# Pod restarts wipe:
#   • /usr/lib/jvm (and our /app/_buildenv/jdk symlink target)
#   • qemu-user-static (needed because Android SDK build-tools ship x86_64
#     binaries but the pod is ARM64)
#   • Often: gradle cache + build dirs at /var/{gradle-home,androidtv-gradle,androidtv-build}
#
# This script is idempotent — running it on a healthy pod is a no-op.
# Re-run after every pod restart, then call disk-janitor.sh + your usual
# gradle command.
#
# Updated v1.44.90 (2026-02-19) after a fresh pod-reschedule recovery
# required adding qemu-user-binfmt + the /usr/x86_64-linux-gnu prefix.

set -euo pipefail

echo "▶ bootstrap-build-env: checking JDK…"
if ! command -v java >/dev/null 2>&1; then
    echo "  JDK missing → installing openjdk-17-jdk-headless + sshpass"
    apt-get update -qq
    apt-get -qq install -y openjdk-17-jdk-headless sshpass file
fi

JDK_DIR=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")
echo "  detected JDK at: $JDK_DIR"
ln -sfn "$JDK_DIR" /app/_buildenv/jdk

echo "▶ bootstrap-build-env: checking qemu-x86_64 (for aapt2)…"
if ! command -v qemu-x86_64 >/dev/null 2>&1; then
    apt-get -qq install -y qemu-user-binfmt
fi
if [ ! -e /usr/bin/qemu-x86_64-static ]; then
    ln -sfn /usr/bin/qemu-x86_64 /usr/bin/qemu-x86_64-static
fi

echo "▶ bootstrap-build-env: x86_64 sysroot for QEMU_LD_PREFIX…"
mkdir -p /usr/x86_64-linux-gnu
ln -sfn /usr/lib/x86_64-linux-gnu /usr/x86_64-linux-gnu/lib

# Install x86_64 glibc + libstdc++ for aapt2.
if [ ! -e /usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2 ]; then
    dpkg --add-architecture amd64
    apt-get update -qq
    apt-get -qq install -y libc6:amd64 libstdc++6:amd64
fi

echo "▶ bootstrap-build-env: gradle cache + build dirs…"
mkdir -p /var/gradle-home /var/androidtv-gradle /var/androidtv-build

echo "▶ bootstrap-build-env: smoke-test aapt2 under qemu…"
if /app/_buildenv/aapt2-wrapper/aapt2 version >/dev/null 2>&1; then
    echo "  ✓ aapt2 wrapper works"
else
    echo "  ✗ aapt2 wrapper FAILS — manually investigate"
    /app/_buildenv/aapt2-wrapper/aapt2 version || true
    exit 1
fi

echo "▶ bootstrap-build-env: done."
echo ""
echo "Next: run /app/_buildenv/disk-janitor.sh before any gradle build."
echo "Then: cd /app/androidtv && export JAVA_HOME=/app/_buildenv/jdk &&"
echo "      export ANDROID_HOME=/app/_buildenv/android-sdk &&"
echo "      export PATH=\$JAVA_HOME/bin:\$PATH &&"
echo "      ./gradlew assembleDevDebug -PdemoUploadToken=\$DEMO_TOKEN"
