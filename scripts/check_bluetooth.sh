#!/bin/bash
# Reports the Android Bluetooth power profile and paired audio devices.
# The SM-J737T (Android 9) defaults to "Optimized" battery mode which
# suspends the BT stack when the screen is off, causing audio to drop.
# Ref: https://www.samsung.com/us/support/answer/ANS00080239/
set -u
ADB="${ADB:-adb}"
SERIAL="${1:-}"
if [ -z "$SERIAL" ]; then SERIAL=$($ADB devices | awk 'NR>1 && $2=="device"{print $1; exit}'); fi
if [ -z "$SERIAL" ]; then echo "[FATAL] no device"; exit 2; fi

echo "[BT] state:"
$ADB -s "$SERIAL" shell dumpsys bluetooth_manager | grep -E "State:|Name:|Battery" | head -10
echo ""
echo "[BT] paired audio devices:"
$ADB -s "$SERIAL" shell dumpsys bluetooth_manager | grep -E "A2DP|AVRCP|HEADSET" | head -5
echo ""
echo "[FIX] To stop audio dropouts:"
echo "  Settings -> Apps -> show system apps -> Bluetooth -> Battery -> Unrestricted"
echo "  Settings -> Device care -> Battery -> Power mode -> Optimized (not Medium)"
echo "  If it still drops: Settings -> General management -> Reset -> Reset network settings"
