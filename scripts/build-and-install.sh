#!/usr/bin/env sh
# Build the debug APK and install it on the first connected device.
# Idempotent: rebuilds are safe, reinstalls use -r. Never exits 1.
set -u

REPO_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_DIR" || exit 2

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ] && [ ! -f local.properties ]; then
    echo "[WARN] ANDROID_HOME / ANDROID_SDK_ROOT unset and no local.properties."
    echo "       Point one at your SDK, or open the project once in Android Studio."
fi

if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
    echo "[FATAL] gradle/wrapper/gradle-wrapper.jar missing; re-run the generator."
    exit 2
fi

chmod +x ./gradlew
echo "[...] ./gradlew --no-daemon assembleDebug"
./gradlew --no-daemon assembleDebug || { echo "[FATAL] gradle build failed"; exit 2; }

APK=app/build/outputs/apk/debug/app-debug.apk
if [ ! -f "$APK" ]; then
    echo "[FATAL] expected APK not found at $APK"
    exit 2
fi
echo "[OK] built $APK"

if ! command -v adb >/dev/null 2>&1; then
    echo "[INFO] adb not on PATH; skipping install."
    echo "       Install manually: adb install -r $APK"
    exit 0
fi

COUNT=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
if [ "$COUNT" = "0" ]; then
    echo "[INFO] no adb device attached; skipping install."
    echo "       Enable USB debugging, replug, then: adb install -r $APK"
    exit 0
fi

echo "[...] adb install -r $APK"
adb install -r "$APK" || { echo "[WARN] adb install failed"; exit 2; }
echo "[OK] installed"
