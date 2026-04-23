#!/bin/bash
# Pod provisioning script — install everything needed to build HushTV APK
# on a fresh aarch64 Debian pod. Safe to run multiple times (idempotent).
set -e

echo "[1/5] Installing JDK + build deps…"
dpkg --add-architecture amd64 2>/dev/null || true
apt-get update -qq 2>/dev/null
apt-get install -y openjdk-17-jdk-headless qemu-user-static sshpass \
  libc6:amd64 libstdc++6:amd64 zlib1g:amd64 unzip wget 2>&1 | tail -1

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

if [ ! -x /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager ]; then
  echo "[2/5] Installing Android cmdline-tools…"
  cd /tmp
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline.zip
  mkdir -p /opt/android-sdk/cmdline-tools
  unzip -q -o cmdline.zip -d /opt/android-sdk/cmdline-tools
  mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest
fi

if [ ! -d /opt/android-sdk/build-tools/34.0.0 ]; then
  echo "[3/5] Installing platform-tools, platforms;android-34, build-tools;34.0.0…"
  yes | sdkmanager --licenses >/dev/null 2>&1 || true
  sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" 2>&1 | tail -1
fi

echo "[4/5] Creating aapt2 wrapper…"
mkdir -p /opt/aapt2-wrapper
cat > /opt/aapt2-wrapper/aapt2 <<'WRAP'
#!/bin/bash
exec qemu-x86_64-static /opt/android-sdk/build-tools/34.0.0/aapt2 "$@"
WRAP
chmod +x /opt/aapt2-wrapper/aapt2

echo "[5/5] Ready. Run:"
echo "  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64"
echo "  export ANDROID_HOME=/opt/android-sdk"
echo "  cd /app/androidtv && ./gradlew assembleDebug"
