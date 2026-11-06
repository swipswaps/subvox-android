#!/usr/bin/env sh
set -u
REPO_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_DIR" || exit 2
[ -f gradle/wrapper/gradle-wrapper.jar ] || { echo "[FATAL] wrapper jar missing"; exit 2; }
chmod +x ./gradlew
./gradlew --no-daemon assembleDebug || { echo "[FATAL] build failed"; exit 2; }
APK=app/build/outputs/apk/debug/app-debug.apk
[ -f "$APK" ] || { echo "[FATAL] APK not found"; exit 2; }
echo "[OK] built $APK"
command -v adb >/dev/null 2>&1 || { echo "[INFO] adb not on PATH"; exit 0; }
COUNT=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
[ "$COUNT" = "0" ] && { echo "[INFO] no device attached"; exit 0; }
adb install -r "$APK" || { echo "[WARN] adb install failed"; exit 2; }
echo "[OK] installed"
